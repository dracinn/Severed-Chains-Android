# Severed Chains — Multi-Platform Port

Agent and contributor guide for this repository. Read this before touching
any build file or platform directory.

## What this repo is

An in-tree multi-platform port of
[Severed Chains](https://github.com/Legend-of-Dragoon-Modding/Severed-Chains),
the fan-made Java rebuild of The Legend of Dragoon. Android is the reference
port; macOS follows the same architecture; iOS, Windows and Linux use the
same pattern when their subprojects land.

## The one rule that matters

**`src/main/java` (and `src/main/resources`) stays byte-for-byte upstream.**
Platform code never lives in the shared tree. Every platform gets its own
Gradle subproject that stages, transforms, compiles and packages the shared
sources. This keeps `git subtree pull` / upstream merges trivial and lets
upstream bugfixes and features flow through untouched.

## Repository layout

```
src/main/java            upstream engine — PRISTINE, never edited here
src/main/resources       upstream resources — PRISTINE
third_party/*/src        vendored upstream mods (git subtree)
own_mods/*/src           local mods, same treatment
gfx/, patches/, lang/    upstream runtime assets staged into builds
isos/                    local dev discs ONLY (.gitignore '*')

android/                 Android subproject (reference implementation)
macos/                   macOS subproject (.app + ANGLE-over-Metal)
ios/                     future iOS subproject
windows/                 future Windows subproject
linux/                   future Linux subproject
```

## The platform subproject pattern

Every platform module implements the same pipeline. Names shown are the
Android ones; macOS uses the same names.

1. **`stageSharedSources` (Sync)** — copy the shared source roots
   (`src/main/java`, vendored mod roots) into
   `<platform>/build/shared-sources-unpatched`.
2. **`patchSharedSources` (Sync)** — copy staged sources into
   `shared-sources` and apply `sharedLiteralTransforms`.
3. **Transform contract** — `sharedLiteralTransforms` maps
   path-relative-to-staged-root → `{literal: replacement}` pairs:
   - A literal that no longer matches **fails the build loudly**
     ("upstream changed; update sharedLiteralTransforms") — transforms are
     never silently dropped.
   - Transforms are **idempotent** — re-running a patch on already-patched
     text is a no-op.
   - **Never duplicate a file key** — later entries overwrite earlier ones
     (Android build.gradle guards this explicitly).
   - Prefer the smallest literal that uniquely anchors the change.
4. **Compile** — staged shared sources + platform glue under
   `<platform>/src/main/java` compile together into the platform jar /
   classes. Platform glue lives in `legend.game.<platform>.*` (e.g.
   `legend.game.macos.BundleMain`) or shadows upstream classes in the same
   package when upstream expects them (e.g. Android's LWJGL/OpenAL shims).
5. **Package** — each platform produces its native artifact (APK, .app,
   .ipa, .exe/.msi, AppImage/deb) from `build/dist` (or the platform's
   standard output dir).

## Platform: android/ (reference)

`android/build.gradle` + `android/src/main/java` (68 glue classes,
`legend.game.android.*` + shaded `org.lwjgl`/`org.lwjgl.openal` shims for
the missing desktop APIs).

- **Toolchain**: AGP app module. `minSdk 26`, `compileSdk/targetSdk 37`,
  `arm64-v8a` + `x86_64`. App runs on Android's Java 17 runtime; engine
  sources stay at upstream's Java 25 level via desugaring-compatible
  compilation of the staged tree.
- **Exclusions**: upstream desktop-only files are dropped at staging
  (`SdlWindow`, `SdlInput`, updater/debugger classes, etc.) and replaced by
  `AndroidPlatformManager`/`AndroidWindow`/`AndroidInput` glue.
- **Rendering**: OpenGL ES via the device driver; `GlesCompat` +
  `patchGfxShaders`/`transpileShaders` adapt upstream shaders;
  `ShaderBinaryCache` + `ShaderWarmup` cut first-run hitching.
- **Input**: `TouchControlsView` — movable/resizable glass-and-gold
  controls (floating stick, d-pad, face cluster, shoulders); PlayStation/
  Xbox/Switch prompt sets; physical gamepads pass through upstream's path.
- **Tooling/inspection**: `GameInspector` exposes a loopback live-text
  screen feed + accessibility nodes so automated testing works without
  screenshots; `CrashReporter` relays crash logs; `SetupView` runs the
  first-boot disc setup (SAF picker + internal `isos/` fallback).
- **Storage**: `Documents/Severed Chains/` holds isos/saves/mods/files/
  config — survives uninstall. `AndroidEnv` handles legacy-dir migration.
- **Signing**: release/debug share the keystore from
  `releaseKeystorePath`/env vars (`SIGNING_*`); `perf` build type exists
  for profiling.
- **CI**: `.github/workflows/android.yml` builds/stamps releases.
- **Build**: `./gradlew :android:assembleRelease` (or `assembleDebug`).

## Platform: macos/

`macos/build.gradle` + `macos/src/main/java/legend/game/macos/`
(`BundleMain`, `AngleContext`, `RendererConfigEntry`). Java subproject
reusing the shared staging pipeline — no AGP.

- **Toolchain**: Java 25 (matches upstream). Classpath reuses
  `rootProject build/libs/libs/*.jar` (the same dependency set as the
  upstream jar — `compileOnly` against `:copyLibs`).
- **Entry point**: `legend.game.macos.BundleMain` — creates
  `~/Documents/Severed Chains/` and symlinks `isos`, `saves`, `mods`,
  `files`, `config.conf`, `config.dcnf`, `debug.log`, `unpacker.log` out of
  `Contents/app` before `legend.game.Main` runs. Writable state lives
  outside the sealed bundle and survives app replacement; read-only
  `gfx/`/`patches/`/`lang/` stay inside.
- **Rendering**: `AngleContext` creates an EGL context via JDK FFM on an
  `SDL_Metal_CreateView`/`CAMetalLayer` surface using the bundled
  `Contents/app/angle/libEGL.dylib` + `libGLESv2.dylib`, then validates
  the context reports ES 3.2+. Below 3.2 (or missing libs) it returns
  null and `SdlWindow`'s upstream GL 3.3 → native GLES 3.2 fallback
  chain runs unchanged. All hooks are staged transforms on `SdlWindow`
  — the upstream file is untouched.
- **Renderer selection**: `RendererConfigEntry` registers a
  `renderer_backend` config (AUTO / SYSTEM_GL / ANGLE_VULKAN) under
  Graphics via a staged `CoreMod` transform, with a help tooltip noting
  Vulkan is experimental and restart-required. `AngleContext` reads
  `config.conf` directly at window creation (before registries exist);
  precedence is `-Dlod.angle.backend` → persisted setting → `auto`
  (`-Dlod.angle.disable` also forces GL). AUTO tries ANGLE Metal then
  falls back to system GL; SYSTEM_GL skips ANGLE; ANGLE_VULKAN requests
  ANGLE's Vulkan backend, which runs over bundled `libMoltenVK.dylib`
  (real ES 3.2 on Apple Silicon). The titlebar reports the actual
  backend `[OpenGL|Vulkan|Metal]` — set only after ES-version
  validation so a failed ANGLE attempt correctly shows `[OpenGL]`.
- **GS-free TMD path**: neither ANGLE backend provides geometry shaders
  on Metal, so when `GL_MAX_GEOMETRY_OUTPUT_VERTICES == 0` the staged
  shaders swap `tmd.vsh`/`battle_tmd.vsh` for generated `_es.vsh`
  variants and `TmdObjLoader` carries sibling positions in vertex
  attribs — the vertex shader then does the GS's face-level near-plane
  cull and face-averaged `usePs1Depth` exactly (unlike Android's
  approximating NaN-cull `-310` path).
- **LWJGL steering**: `-Dorg.lwjgl.opengles.libname=$APPDIR/angle/
  libGLESv2.dylib` must be a JVM property (jpackage cfg), not runtime
  config — anything can bind the system libGL before class-init otherwise.
- **Packaging**: `:macos:packageMacApp` → jpackage `app-image` →
  `macos/build/dist/Severed Chains.app` — embedded JDK 25 runtime,
  `lod-game-snapshot.jar`, `libs/`, game data, generated `.icns`,
  `-XstartOnFirstThread` + upstream joml/add-opens flags, `-Duser.dir=
  $APPDIR`. Version via `-PmacAppVersion`.
- **Signing**: `:macos:signMacApp` codesigns `--deep --options runtime`
  with `-PmacSignIdentity` (default: Developer ID cert when present in
  keychain; skipped otherwise). Hardened-runtime entitlements: JIT,
  unsigned-executable-memory (LWJGL/ANGLE natives),
  disable-library-validation (Google-signed ANGLE). `:macos:verifyMacApp`
  runs `codesign --verify --deep --strict`.
- **ANGLE source build**: `/Volumes/Storage/angle-build` (depot_tools
  checkout; ~25G). Point the bundler at any `libEGL`/`libGLESv2` pair with
  `-PangleLibsDir=<dir>` — default is the Android emulator's gles_angle,
  which only advertises ES 3.0 and is rejected at runtime. The Vulkan
  backend additionally bundles `libMoltenVK.dylib` from Homebrew
  (`brew install molten-vk`, picked up from `/opt/homebrew/lib` or
  `/usr/local/lib`) plus a generated `MoltenVK_icd.json` manifest.

## Platform: ios/ (planned)

Mirrors `macos/` more than `android/` (same Metal goal) with Android's
input layer.

- **Structure**: `ios/build.gradle` + `ios/src/main/java/legend/game/ios/`
  — `BundleMain` (sandbox container wiring) + `AngleContext` (iOS ANGLE
  build, same EGL-on-CAMetalLayer path — iOS has **no** system GL, ANGLE is
  mandatory, not optional).
- **Rendering**: GLES-over-Metal only; same staged `SdlWindow` transforms,
  minus the GL fallback (there is none on iOS). The macOS GS-free TMD
  path carries over unchanged — iOS Metal has no geometry shaders either.
- **Input**: port `TouchControlsView` + `AndroidInput` logic into
  `IosInput`/`IosWindow` — the layout/prompt-set feature set carries over.
- **Storage**: app-container `Documents/` (already the sandbox's
  user-visible dir) — same symlink indirection, minus a home dir.
- **Packaging**: `.ipa` via Xcode project wrapping the jpackage runtime
  (or GraalVM/AOT if the runtime-size story requires it); Xcode handles
  signing/provisioning rather than Gradle.

## Platform: windows/ (planned)

- **Structure**: `windows/build.gradle` +
  `windows/src/main/java/legend/game/windows/` — `BundleMain` (writes
  `Documents/Severed Chains` or `%APPDATA%` indirection) + `AngleContext`
  variant using `libEGL.dll`/`libGLESv2.dll` (ANGLE D3D11 backend — the
  most-compatible path on Windows) or upstream GL where present.
- **Packaging**: jpackage `--type msi`/`exe` (or portable zip), same
  staging/`user.dir` trick, same JVM flags.
- **Signing**: optional signtool task mirroring `signMacApp`.

## Platform: linux/ (planned)

Lightest port — upstream already targets desktop Linux GL 3.3.

- **Structure**: `linux/build.gradle` + minimal glue — likely no rendering
  transform at all; the interesting transforms are storage redirection
  (`~/.local/share/Severed Chains` per XDG) and packaging.
- **Packaging**: AppImage or tarball via jpackage `app-image`; no signing.

## Cross-cutting rules

- **Never ship ISOs.** `isos/` is `.gitignore`d (`*`), excluded from every
  `assets.srcDirs`, jpackage input and CI artifact. Discs reach devices/
  bundles only via `adb push` or manual copy into the platform's
  `Documents/Severed Chains` dir.
- **Never edit `src/main/java` for platform behaviour.** If a platform
  needs an upstream hook, add a staged transform + glue class — the way
  `AngleContext` hooks `SdlWindow` on macOS.
- **Transforms fail loudly.** If `patchSharedSources` throws after an
  upstream pull, update `sharedLiteralTransforms` to match the new
  upstream text — don't weaken the check.
- **Match upstream feature-for-feature.** The shared jar still builds via
  `./gradlew jar` and `./gradlew run` — platform modules must not break it.
- **Java versions**: engine = Java 25 (upstream). Android runtime = 17
  (platform constraint). Platform glue follows its own toolchain.
- **External layout**: ANGLE lives at `/Volumes/Storage/angle-build`;
  Android SDK/AVDs/Gradle home are symlinked into `/Volumes/Storage/
  dev-env/` — internal-disk builds of anything big are wrong.

## Quick reference

```
./gradlew :android:assembleRelease      # APK
./gradlew :macos:packageMacApp          # unsigned .app
./gradlew :macos:signMacApp verifyMacApp
./gradlew jar && ./gradlew run          # vanilla upstream desktop build

# ANGLE (external checkout, depot_tools on PATH):
cd /Volumes/Storage/angle-build
autoninja -C out/Release libEGL libGLESv2   # or bare 'autoninja -C out/Release'
```
