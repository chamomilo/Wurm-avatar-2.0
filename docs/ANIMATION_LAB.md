# Animation Lab

The Lab previews appearance and animation on the local avatar. It is optional
and does not change server-side movement. Open the **Lab** tab in Avatar Studio,
enable **Lab on**, and choose a motion or appearance control. **Open Lab** opens
the browser dashboard on the local machine.

The native window, browser dashboard and Python companion submit the same
validated commands. Tests remain separate from the saved Body profile.
**Reset test**, **Lab off**, or an expired temporary command restores the
applied appearance. **Save body** explicitly keeps the selected appearance;
temporary animation and pose commands are not written to the profile.

## Console commands

Enter these in Wurm's F1 console:

```text
/avatar2 lab on
/avatar2 lab open
/avatar2 lab status
/avatar2 lab off
```

## Optional Python companion

The companion is source tooling and is not required to install or use the mod.
It is available in `tools/animation-lab/animation_lab.py` in the repository.
Run it with Python 3 after enabling the Lab in game:

```powershell
python tools/animation-lab/animation_lab.py --help
python tools/animation-lab/animation_lab.py height 15 --unit dirt
python tools/animation-lab/animation_lab.py geometry --shape mpfb-athletic --mix 0.5
python tools/animation-lab/animation_lab.py skin middle
python tools/animation-lab/animation_lab.py reset
```

Height is bounded to 13–20 dirt, or 1.3–2.0 metres. The browser publishes the
current schema and supported body bindings. Invalid or incompatible commands
are rejected before changing the active command. The local server starts only
when the Lab is enabled; keep its dashboard address private.
