"""Avatar 2.0 loopback controller; Python 3 stdlib only. SPDX: LGPL-3.0-only."""
import argparse
import copy
import json
import math
from pathlib import Path
import re
import tempfile
import time
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


class Connector:
    def __init__(self, session=None):
        candidates = [Path(session)] if session else sorted(
            Path(tempfile.gettempdir()).glob("avatar2-lab-session-*.json"),
            key=lambda p: p.stat().st_mtime, reverse=True)[:10]
        for path in candidates:
            try:
                info = json.loads(path.read_text(encoding="utf-8"))
                if not re.fullmatch(r"http://127\.0\.0\.1:[0-9]{1,5}", info["url"]):
                    raise ValueError("Session endpoint must be IPv4 loopback")
                if info.get("protocol") != 1 or not re.fullmatch(r"[A-Za-z0-9_-]{43}", info["token"]):
                    raise ValueError("Invalid protocol/token")
                self.url, self.token = info["url"], info["token"]
                self.state()
                return
            except (OSError, KeyError, ValueError, URLError):
                if session:
                    raise
        raise RuntimeError("No live session. Install the ZIP manually, then run /avatar2 lab on in Wurm.")

    def request(self, path, data=None, raw=False):
        headers = {"Authorization": "Bearer " + self.token}
        if data is not None:
            headers["Content-Type"] = "application/json"
        body = None if data is None else json.dumps(data, allow_nan=False).encode("utf-8")
        request = Request(self.url + path, data=body, headers=headers)
        try:
            with urlopen(request, timeout=3) as response:
                payload = response.read()
                return (payload, {key.lower(): value for key, value in response.headers.items()}) if raw else json.loads(payload)
        except HTTPError as error:
            raise RuntimeError(f"HTTP {error.code}: {error.read().decode('utf-8')}") from None

    def state(self):
        return self.request("/state")

    def command(self, data, expected=None):
        value = copy.deepcopy(data)
        value["expectedRevision"] = self.state()["command"]["revision"] if expected is None else expected
        return self.request("/command", value)["revision"]

    def settled(self, revision, schema_id=None, timeout=8):
        deadline, first = time.monotonic() + timeout, None
        while time.monotonic() < deadline:
            state = self.state()
            if state["command"]["revision"] != revision:
                raise RuntimeError("Another controller changed the buffer; measurement aborted")
            t = state["telemetry"]
            if state.get("error"):
                raise RuntimeError(state["error"])
            if t.get("revision") == revision:
                if schema_id and t["schemaId"] != schema_id:
                    raise RuntimeError("Avatar model changed during measurement")
                if first is not None and t["sequence"] >= first + 2:
                    return state
                first = t["sequence"] if first is None else first
            time.sleep(.08)
        raise TimeoutError("No settled skinned pose. Keep Wurm rendering with the local body visible in third person.")

    def capture(self, output, revision=None):
        if revision is not None:
            self.settled(revision)
        before = self.state()["capture"].get("timeMillis", 0)
        self.request("/capture", {})
        deadline = time.monotonic() + 8
        while time.monotonic() < deadline:
            state = self.state()
            meta = state["capture"]
            if meta.get("status") == "failed":
                raise RuntimeError(meta["error"])
            if meta.get("timeMillis", 0) > before:
                if revision is not None and meta.get("revision") != revision:
                    raise RuntimeError("Capture revision mismatch")
                payload, headers = self.request("/frame.png", raw=True)
                if revision is not None and int(headers["x-avatar2-revision"]) != revision:
                    raise RuntimeError("Capture replaced by another controller")
                Path(output).write_bytes(payload)
                return meta
            time.sleep(.1)
        raise TimeoutError("Wurm did not render a framebuffer")


def position(matrix):
    return [matrix[3], matrix[7], matrix[11]]


def derivative(plus, minus, base, radians):
    """Model-space linear and angular response per input radian near baseline."""
    scale = 1 / (2 * radians)
    dp = [(a - b) * scale for a, b in zip(position(plus), position(minus))]
    # dR * R^T, then the skew-symmetric part (small-angle angular velocity).
    a = [[sum((plus[r*4+k] - minus[r*4+k]) * scale * base[c*4+k]
              for k in range(3)) for c in range(3)] for r in range(3)]
    omega = [(a[2][1]-a[1][2])/2, (a[0][2]-a[2][0])/2, (a[1][0]-a[0][1])/2]
    return {"positionPerRadian": dp, "rotationAxisPerRadian": omega}


def restorable(command):
    return {key: copy.deepcopy(command[key]) for key in ("preview", "corrections", "pose", "geometry", "heightScale", "ageSkin") if key in command}


def measure(connector, args):
    initial = connector.state()
    if not initial["schema"]:
        raise RuntimeError("Avatar body has not loaded")
    schema_id, revision = initial["schema"]["id"], initial["command"]["revision"]
    saved = restorable(initial["command"])
    records = []
    try:
        base_command = {"preview": {"clip": args.clip, "paused": True, "phase": args.phase, "speed": 1},
                        "corrections": {}, "pose": {}}
        revision = connector.command(base_command, revision)
        baseline = connector.settled(revision, schema_id)
        records.append(baseline)
        response = {}
        for axis, label in enumerate("XYZ"):
            samples = []
            for sign in (1, -1):
                rotation = [0, 0, 0]
                rotation[axis] = sign * args.angle
                revision = connector.command({"corrections": {args.bone: {
                    "space": args.space, "rotation": rotation, "translation": [0, 0, 0]}}}, revision)
                sample = connector.settled(revision, schema_id)
                records.append(sample)
                samples.append(sample["telemetry"]["bones"])
            response[label] = {
                name: derivative(samples[0][name]["engineBind"], samples[1][name]["engineBind"],
                                 base["engineBind"], math.radians(args.angle))
                for name, base in baseline["telemetry"]["bones"].items()}
        result = {"protocol": 1, "scope": "local linearisation of Wurm FK at fixed clip/phase, not a biomechanical model",
                  "bone": args.bone, "space": args.space, "angleDegrees": args.angle,
                  "clip": args.clip, "phase": args.phase, "schema": initial["schema"],
                  "jacobian": response, "samples": records}
        Path(args.output).write_text(json.dumps(result, indent=2, allow_nan=False), encoding="utf-8")
        print(f"Calibration written: {args.output}; X/Y/Z response measured through Wurm")
    finally:
        # Never overwrite a newer human/controller command in error recovery.
        if connector.state()["command"]["revision"] == revision:
            connector.command(saved, revision)
        else:
            print("Buffer changed externally; previous command was not restored")


def record(connector, args):
    initial = connector.state()
    revision = initial["command"]["revision"]
    schema_id = initial["schema"]["id"]
    saved = restorable(initial["command"])
    output = Path(args.output)
    output.mkdir(parents=True, exist_ok=True)
    samples = []
    try:
        for index in range(args.frames):
            phase = index / args.frames
            revision = connector.command({"preview": {"clip": args.clip, "paused": True,
                                                       "phase": phase, "speed": 1}}, revision)
            state = connector.settled(revision, schema_id)
            if args.images:
                state["captureResult"] = connector.capture(output / f"frame-{index:03}.png", revision)
            samples.append(state)
        (output / "poses.json").write_text(json.dumps(samples, indent=2, allow_nan=False), encoding="utf-8")
        print(f"Recorded {len(samples)} phases in {output}")
    finally:
        if connector.state()["command"]["revision"] == revision:
            connector.command(saved, revision)


def height_command(value, unit, schema):
    if not math.isfinite(value):
        raise ValueError("Height must be finite")
    if unit == "dirt":
        if not 13 <= value <= 20:
            raise ValueError("Height must be in 13..20 dirt")
        return {"heightDirts": value}
    if unit == "metres":
        if not 1.3 <= value <= 2:
            raise ValueError("Height must be in 1.3..2.0 metres")
        return {"heightMetres": value}
    lower, upper = schema["heightScaleRange"]
    scale = value / 100
    if not lower - 1e-7 <= scale <= upper + 1e-7:
        raise ValueError(f"This body allows {lower*100:.2f}..{upper*100:.2f} percent (13..20 dirt)")
    return {"heightScale": scale}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--session", help="Optional exact session JSON path (contains a secret; never publish it)")
    commands = parser.add_subparsers(dest="action", required=True)
    commands.add_parser("state")
    commands.add_parser("reset")
    skin = commands.add_parser("skin", help="Temporary adult diffuse skin; independent of body shape and saved profiles")
    skin.add_argument("preset", choices=["original", "young", "middle", "old"])
    height = commands.add_parser("height", help="Temporary proportional Lab height relative to the current approved body")
    height.add_argument("value", type=float, help="13..20 dirt or 1.3..2.0 metres; percent limits depend on the body")
    height.add_argument("--unit", choices=["percent", "metres", "dirt"], default="percent",
                        help="Default: percent; 100 restores the current body's original height")
    geometry = commands.add_parser("geometry", help="Temporary stage-0 diagnostic/authored geometry; does not save a body profile")
    geometry.add_argument("--width", type=float, default=0)
    geometry.add_argument("--depth", type=float, default=0)
    geometry.add_argument("--shape", choices=["none", "mpfb-athletic", "mpfb-heavy", "mpfb-older", "blend", "proportions"], default="none")
    geometry.add_argument("--mix", type=float, help="Authored endpoint blend 0..1; defaults to 1 with --shape, otherwise 0")
    geometry.add_argument("--athletic", type=float, default=0, help="Blend contribution 0..1")
    geometry.add_argument("--heavy", type=float, default=0, help="Blend contribution 0..1")
    geometry.add_argument("--older", type=float, default=0, help="Blend contribution 0..1")
    geometry.add_argument("--muscle", type=float, default=1, help="Build muscle 0..1; 1 is Original")
    geometry.add_argument("--fat", type=float, default=0, help="Build fullness 0..1; 0 is Original")
    geometry.add_argument("--head-size", type=float, default=.5, help="Head size 0..1; .5 original, 92..108% actual scale")
    geometry.add_argument("--age", choices=["young", "middle", "old"], default="young", help="Build adult body preset; skin remains a separate command")
    geometry.add_argument("--shoulders", type=float, default=.5, help="Build shoulder width 0..1; .5 original")
    geometry.add_argument("--breast", type=float, default=.5, help="Female Build breast volume 0..1; .5 retains the current build")
    command = commands.add_parser("command")
    command.add_argument("json_file")
    capture = commands.add_parser("capture")
    capture.add_argument("output")
    probe = commands.add_parser("probe")
    probe.add_argument("bone")
    probe.add_argument("--space", choices=["local", "parent", "model"], default="local")
    probe.add_argument("--angle", type=float, default=3)
    probe.add_argument("--clip", default="bind")
    probe.add_argument("--phase", type=float, default=.25)
    probe.add_argument("--output", default="axis-calibration.json")
    rec = commands.add_parser("record")
    rec.add_argument("--clip", default="walk")
    rec.add_argument("--frames", type=int, default=24)
    rec.add_argument("--images", action="store_true")
    rec.add_argument("--output", default="walk-observation")
    args = parser.parse_args()
    if args.action == "height" and not math.isfinite(args.value):
        parser.error("Height must be finite")
    if args.action == "geometry" and (not -1 <= args.width <= 1 or not -1 <= args.depth <= 1):
        parser.error("Geometry probe width/depth must be finite and in -1..1")
    if args.action == "geometry":
        args.mix = (0 if args.shape == "none" else 1) if args.mix is None else args.mix
        if not 0 <= args.mix <= 1 or args.shape == "none" and args.mix != 0:
            parser.error("Authored mix must be finite and in 0..1; none requires zero mix")
        if any(not 0 <= value <= 1 for value in (args.athletic, args.heavy, args.older)):
            parser.error("Blend contributions must be finite and in 0..1")
        if args.shape != "blend" and any((args.athletic, args.heavy, args.older)):
            parser.error("Contributions require --shape blend")
        if any(not 0 <= value <= 1 for value in (args.muscle, args.fat, args.breast, args.shoulders, args.head_size)):
            parser.error("Build controls must be finite and in 0..1")
        if args.shape != "proportions" and (args.muscle != 1 or args.fat != 0 or args.breast != .5 or args.shoulders != .5 or args.age != "young"):
            parser.error("Build controls require --shape proportions")
    if args.action == "probe" and (not .1 <= args.angle <= 10 or not 0 <= args.phase <= 1 or args.clip == "game"):
        parser.error("Probe needs a fixed clip/bind, phase 0..1 and angle 0.1..10 degrees")
    if args.action == "record" and (not 2 <= args.frames <= 240 or args.clip in ("game", "bind")):
        parser.error("Recording needs a concrete clip and 2..240 samples")
    connector = Connector(args.session)
    if args.action == "state":
        print(json.dumps(connector.state(), indent=2))
    elif args.action == "reset":
        print("Revision", connector.command({"reset": True}))
    elif args.action == "skin":
        print("Revision", connector.command({"ageSkin": args.preset}))
    elif args.action == "height":
        initial = connector.state()
        revision = connector.command(height_command(args.value, args.unit, initial["schema"]), initial["command"]["revision"])
        state = connector.settled(revision)
        print(json.dumps(state["telemetry"].get("height", {}), indent=2))
    elif args.action == "geometry":
        revision = connector.command({"geometry": {"torsoWidth": args.width, "torsoDepth": args.depth,
                                                   "shape": args.shape, "shapeMix": args.mix,
                                                   "blendAthletic": args.athletic, "blendHeavy": args.heavy,
                                                   "blendOlder": args.older,
                                                   "buildMuscle": args.muscle, "buildFat": args.fat,
                                                   "buildBreast": args.breast, "buildShoulders": args.shoulders, "headSize": args.head_size, "agePreset": args.age}})
        state = connector.settled(revision)
        print(json.dumps(state["telemetry"].get("geometry", []), indent=2))
    elif args.action == "command":
        print("Revision", connector.command(json.loads(Path(args.json_file).read_text(encoding="utf-8-sig"))))
    elif args.action == "capture":
        print(json.dumps(connector.capture(args.output, connector.state()["command"]["revision"])))
    elif args.action == "probe":
        measure(connector, args)
    elif args.action == "record":
        record(connector, args)


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, ValueError, OSError) as error:
        raise SystemExit(str(error)) from None
