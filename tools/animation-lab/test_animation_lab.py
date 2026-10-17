"""Offline tests for the companion. SPDX: LGPL-3.0-only."""
import math
import unittest
from unittest.mock import patch
from contextlib import redirect_stdout
from io import StringIO
import animation_lab
from animation_lab import derivative, restorable, height_command


class MathTests(unittest.TestCase):
    def test_blend_cli_uses_the_validated_geometry_payload(self):
        sent = []
        class FakeConnector:
            def __init__(self, session):
                pass
            def command(self, payload):
                sent.append(payload)
                return 1
            def settled(self, revision):
                return {"telemetry": {"geometry": []}}
        argv = ["animation_lab.py", "geometry", "--shape", "blend", "--athletic", ".25",
                "--heavy", "1", "--older", ".5", "--mix", ".75"]
        with patch("sys.argv", argv), patch.object(animation_lab, "Connector", FakeConnector), redirect_stdout(StringIO()):
            animation_lab.main()
        self.assertEqual(sent, [{"geometry": {"torsoWidth": 0, "torsoDepth": 0, "shape": "blend",
                         "shapeMix": .75, "blendAthletic": .25, "blendHeavy": 1, "blendOlder": .5,
                         "buildMuscle": 1, "buildFat": 0, "buildBreast": .5, "buildShoulders": .5, "headSize": .5, "agePreset": "young"}}])
        sent.clear()
        argv = ["animation_lab.py", "geometry", "--shape", "proportions", "--muscle", ".25", "--fat", ".75", "--breast", ".75", "--shoulders", ".25", "--head-size", ".75", "--age", "old"]
        with patch("sys.argv", argv), patch.object(animation_lab, "Connector", FakeConnector), redirect_stdout(StringIO()):
            animation_lab.main()
        self.assertEqual(sent[0]["geometry"]["shape"], "proportions")
        self.assertEqual(sent[0]["geometry"]["buildMuscle"], .25)
        self.assertEqual(sent[0]["geometry"]["buildFat"], .75)
        self.assertEqual(sent[0]["geometry"]["buildBreast"], .75)
        self.assertEqual(sent[0]["geometry"]["buildShoulders"], .25)
        self.assertEqual(sent[0]["geometry"]["headSize"], .75)
        self.assertEqual(sent[0]["geometry"]["agePreset"], "old")
        sent.clear()
        with patch("sys.argv", ["animation_lab.py", "geometry", "--head-size", ".25"]), patch.object(animation_lab, "Connector", FakeConnector), redirect_stdout(StringIO()):
            animation_lab.main()
        self.assertEqual(sent[0]["geometry"]["shape"], "none")
        self.assertEqual(sent[0]["geometry"]["headSize"], .25)
    def test_axis_response_around_y(self):
        angle = math.radians(3)
        def rotation(theta):
            c, s = math.cos(theta), math.sin(theta)
            return [c,0,s,0, 0,1,0,0, -s,0,c,0, 0,0,0,1]
        result = derivative(rotation(angle), rotation(-angle), rotation(0), angle)
        self.assertEqual(result["positionPerRadian"], [0,0,0])
        self.assertAlmostEqual(result["rotationAxisPerRadian"][0], 0)
        self.assertAlmostEqual(result["rotationAxisPerRadian"][1], math.sin(angle)/angle)
        self.assertAlmostEqual(result["rotationAxisPerRadian"][2], 0)

    def test_restore_payload_is_copied_without_stale_revision(self):
        original = {"revision":5, "preview":{"clip":"walk"}, "corrections":{}, "pose":{},
                    "geometry":{"torsoWidth":1, "torsoDepth":-.5}, "heightScale":.85, "ageSkin":"old"}
        saved = restorable(original)
        saved["preview"]["clip"] = "run"
        self.assertEqual(original["preview"]["clip"], "walk")
        self.assertNotIn("revision", saved)
        saved["geometry"]["torsoWidth"] = 0
        self.assertEqual(original["geometry"]["torsoWidth"], 1)
        self.assertEqual(saved["heightScale"], .85)
        self.assertEqual(saved["ageSkin"], "old")

    def test_height_units_and_body_specific_percent_limits(self):
        for baseline in (1.729108, 1.591230):
            schema = {"heightScaleRange": [1.3/baseline, 2/baseline]}
            self.assertEqual(height_command(100, "percent", schema), {"heightScale": 1})
            for dirt in (13, 20):
                self.assertEqual(height_command(dirt, "dirt", schema), {"heightDirts": dirt})
                self.assertEqual(height_command(dirt/10, "metres", schema), {"heightMetres": dirt/10})
                self.assertAlmostEqual(height_command(dirt*10/baseline, "percent", schema)["heightScale"], dirt/10/baseline)
            for value, unit in ((50,"percent"),(150,"percent"),(12.999,"dirt"),(20.001,"dirt"),(1.299,"metres"),(2.001,"metres")):
                with self.assertRaises(ValueError):
                    height_command(value, unit, schema)
            for unit in ("percent", "metres", "dirt"):
                for value in (math.nan, math.inf, -math.inf):
                    with self.assertRaises(ValueError):
                        height_command(value, unit, schema)


if __name__ == "__main__":
    unittest.main()
