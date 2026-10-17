# Building Avatar 2.0

Use JDK 8 and Gradle 7.4. The release build reads proprietary client libraries
from the local, ignored `libs` directory:

```text
client-patched.jar
common.jar
javassist.jar
modlauncher.jar
junit-4.13.2.jar
hamcrest-core-1.3.jar
```

Obtain game libraries from your own compatible Wurm Unlimited installation.
They must never be committed or included in a public distribution. Native
runtime checks also need that client's JavaFX JAR and native-library directory;
the Windows defaults are declared in `build.gradle`.

The shared updater is built from the canonical checkout at
`C:/projects/updater`. Read its `docs/INTEGRATION.md`; another checkout can be
selected with `-PchamomiloUpdaterReference=<absolute-path>`. Its complete module
includes the pinned Chamomilo UI SDK. Do not add a second SDK copy.

With Gradle 7.4 on PATH and JAVA_HOME pointing to JDK 8:

```powershell
gradle --offline --no-daemon --console=plain clean build dist
```

The reference updater must have its own dependencies available offline. Pass
`-PwurmJavaFxJar=<absolute-path-to-jfxrt.jar>` when its native UI probe cannot
find JavaFX. Concurrent release builds can isolate canonical updater outputs
with `-PupdaterBuildDirectory=<absolute-path>` and
`-PupdaterDistributionDirectory=<absolute-path>`.

The installer is written to `build/distributions/avatar2-<version>.zip`.
Its file allowlist is checked by `dist`. The release gate also verifies complete
updater and SDK entry bytes, the mod descriptor, resource provenance, profiles,
native UI controls and the normal body/hair/camera runtime checks. Reports stay
under `build/reports`; they are not installed.

`verifyShieldWalkRuntime` is a separate, expensive diagnostic task and is not
part of normal builds or distribution packaging. The build never installs files
into the game.

Runtime bodies, skin textures, motions and baked morph banks are in
`assets/runtime`. Their provenance is retained in `assets/metadata`.
The Blender authoring scripts require separately downloaded upstream assets
and are not needed to compile the checked-in runtime resources.

Project source is available at the matching Git tag. `sourceDist` can produce
a separate source archive for development; it is not an installable mod.
