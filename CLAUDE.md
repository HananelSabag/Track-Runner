# Track-Runner (brand: FAST)

Speed tracker for Wear OS (OnePlus Watch 2 and newer) with a companion Android phone app. Local-only: no backend, no accounts.

## Read first

- `docs/00-summary.md` — where the project stands.
- `docs/architecture/decisions.md` — what is decided, and what is still waiting on Hananel. Do not settle an open question (Q*) on your own; ask him.
- `docs/roadmap.md` — the current milestone.

## Build and test

`java` is not on PATH. Use Android Studio's JDK:

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :wear:assembleDebug :mobile:assembleDebug
./gradlew test
```

- There is no Wear OS emulator image installed. The watch app can only be compiled locally; running it needs the real watch over wireless adb, or the image installed.
- The phone AVD is `Pixel_9_Pro_Fold`. It has two displays, so screenshots need `adb exec-out screencap -d <displayId> -p` (list ids with `adb shell dumpsys SurfaceFlinger --display-id`). Installs can fail on low emulator storage; retry once.
- Library versions are capped by the installed toolchain (AGP 8.13 / compileSdk 36). Read `docs/architecture/tech-stack.md` before bumping anything — the newest AndroidX releases need AGP 9.1+ and compileSdk 37 and will fail the build.

## Rules that are easy to break

- **Same `applicationId` (`com.hananel.fast`) and signing key in `wear` and `mobile`.** The Data Layer stops connecting them otherwise.
- **`wear` and `mobile` never depend on each other.** Shared code goes in `core/`.
- **Keep `core` modules pure Kotlin wherever possible** so the speed filter and sync protocol run in plain JVM tests.
- **Store raw samples, derive everything else.** Stats carry an `algorithmVersion` and must be recomputable from raw data. Never write a statistic that cannot be regenerated.
- **Speed comes from Doppler (`Location.getSpeed()`), never from differencing positions.** Maximum speed comes from the filtered series, never from a raw sample.
- **A session is synced only after the phone acknowledges it is stored.** Never delete an unacknowledged session.
- **Numbers, units and charts stay LTR in the Hebrew UI.** English technical data (the device-check report) is laid out LTR too.
- Launcher icons and logo SVGs are generated: edit `tools/brand/generate.mjs` and run it, do not edit the outputs.

## Conventions

- Docs are in Hebrew with English technical terms. Code and code comments are in English. UI strings are bilingual (`values/` English, `values-iw/` Hebrew).
- Wear UI follows Google's `wear-compose-m3` guidance: one `AppScaffold`, a `ScreenScaffold` per screen, `TransformingLazyColumn` for lists, `MaterialTheme` tokens instead of hard-coded colors.
- Update the relevant doc in the same commit that changes what it describes.
- One commit per complete unit of work on `main`, with a body explaining why. Annotated tag per milestone.
