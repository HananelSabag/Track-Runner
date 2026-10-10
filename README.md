# Track-Runner · FAST

<img src="docs/brand/logo-wordmark.svg" alt="FAST" width="320">

A personal speed tracker for Wear OS, with a companion Android app. Two modes:

- **Run** — how fast you are moving right now, your top speed, and your speed minute by minute.
- **Football** — training read as work and rest, detected from movement with no pause button: working time, sprints with their peaks, speed zones, heart-rate load and recovery.

Built for the OnePlus Watch 2 (and newer Wear OS watches) and the OnePlus 13. Local-only: no backend, no account.

> **Status: both apps built, verified on emulators only.** The watch records runs and football sessions (work, rest and sprints detected from movement) and has a sprint test with a confidence score. The phone shows the history, per-session analysis, and a replay of the track. Nothing beyond the sprint test has run on the real watch yet, and watch-to-phone sync has not run end to end. See the [roadmap](docs/roadmap.md).

## Architecture & Modules

FAST separates platform-agnostic domain logic from Android UI/sensor implementations into pure Kotlin core modules:

| Module | Type | Description |
|---|---|---|
| `wear/` | Android App | Wear OS app (Compose for Wear OS, Material 3) |
| `mobile/` | Android App | Companion phone app (Jetpack Compose, Material 3) |
| `core/model/` | Pure Kotlin | Domain data models and entities |
| `core/tracking/` | Pure Kotlin | Sprint-test scoring, speed zones, work/rest detection, session analysis, and the live reading of a session in progress |
| `core/data/` | Pure Kotlin | The session file format, the file store, turning raw fixes into a track, summaries, and the sync protocol's names |
| `core/diagnostics/` | Android Library | Hardware capabilities and sensor diagnostics |
| `tools/brand/` | Node.js script | Generates SVG logos and launcher icons from one script |
| `tools/sim/` | Python script | Feeds the Wear emulator a scripted training: GPS fixes and heart rate |

Both apps share the application ID `com.hananel.fast`, which the Wearable Data Layer requires for them to communicate.

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose, Wear OS Compose (Material 3); charts and the track replay are drawn on a Canvas
- **Local Storage:** one plain-text file per session, appended as it is recorded (no database)
- **Sync:** Wearable Data Layer, with an acknowledgement from the phone before a session counts as synced
- **Build System:** Gradle 9, Android Gradle Plugin (AGP) 9, JDK (JetBrains Runtime bundled with Android Studio)

## Documentation

All project documents live in [`docs/`](docs/README.md) (primarily in Hebrew). Recommended reading order:
1. [One-page Summary (`00-summary.md`)](docs/00-summary.md) — Current project status and overview.
2. [Architecture (`architecture.md`)](docs/architecture/architecture.md) — System modules, tracking engine, and data flow.
3. [Training recording (`training-recording.md`)](docs/spec/training-recording.md) and [Phone app (`phone-app.md`)](docs/spec/phone-app.md) — What is built, with screenshots.
4. [Product Spec (`product-spec.md`)](docs/spec/product-spec.md) — Goals, scope, and requirements.
5. [Roadmap (`roadmap.md`)](docs/roadmap.md) — Development phases (M0–M6).

## Build & Run

Needs Android Studio Quail 4 (2026.1.4) or newer. From the command line, `java` may not be on the PATH, so point Gradle at Android Studio's bundled JDK:

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio1/jbr"
./gradlew :wear:assembleDebug :mobile:assembleDebug
./gradlew test
```

Regenerate the logo and launcher icons after editing `tools/brand/generate.mjs`:

```bash
node tools/brand/generate.mjs
```
