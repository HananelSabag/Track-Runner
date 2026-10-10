# Track-Runner · FAST

<img src="docs/brand/logo-wordmark.svg" alt="FAST" width="320">

A personal speed tracker for Wear OS, with a companion Android app. Two modes:

- **Run** — how fast you are moving right now, your top speed, and your speed minute by minute.
- **Football** — training read as work and rest, detected from movement with no pause button: working time, sprints with their peaks, speed zones, heart-rate load and recovery.

Built for the OnePlus Watch 2 (and newer Wear OS watches) and the OnePlus 13. Local-only: no backend, no account.

> **Status: first feature working.** The watch app has a **sprint test** — one sprint, a top speed, and a confidence score — running on the real watch's GPS. The run and football modes are designed and their analysis is tested in `core/tracking`, but they are not yet wired to sensors. See the [roadmap](docs/roadmap.md).

## Documentation

All project documents are in Hebrew and live in [`docs/`](docs/README.md). Start with the [one-page summary](docs/00-summary.md).

## Layout

| Module | What it is |
|---|---|
| `wear/` | The watch app (Compose for Wear OS, Material 3) |
| `mobile/` | The phone app (Jetpack Compose, Material 3) |
| `core/model/` | Domain types. Pure Kotlin |
| `core/tracking/` | Sprint-test scoring and session analysis: speed zones, work/rest detection, sprints, heart rate. Pure Kotlin |
| `core/diagnostics/` | Reads what the device hardware can measure |
| `tools/brand/` | Generates the logo SVGs and launcher icons from one script |

Both apps share the application id `com.hananel.fast`, which the Wearable Data Layer requires for them to talk to each other.

## Build

Needs Android Studio Quail 4 (2026.1.4) or newer to open in the IDE. From the command line, `java` is not on the PATH on the development machine, so point Gradle at Android Studio's bundled JDK. The updated Studio installed itself into `Android Studio1`; the old `Android Studio` folder is an empty leftover.

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio1/jbr"
./gradlew :wear:assembleDebug :mobile:assembleDebug
./gradlew test
```

Regenerate the logo and launcher icons after editing `tools/brand/generate.mjs`:

```bash
node tools/brand/generate.mjs
```
