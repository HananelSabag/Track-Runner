# Track-Runner · FAST

<img src="docs/brand/logo-wordmark.svg" alt="FAST" width="320">

A personal speed tracker for Wear OS, with a companion Android app. Two modes:

- **Run** — how fast you are moving right now, your top speed, and your speed minute by minute.
- **Football** — training read as work and rest, detected from movement with no pause button: working time, sprints with their peaks, speed zones, heart-rate load and recovery.

Built for the OnePlus Watch 2 (and newer Wear OS watches) and the OnePlus 13. Local-only: no backend, no account.

> **Status: first feature working.** The watch app has a **sprint test** — one sprint, a top speed, and a confidence score — running on the real watch's GPS. The run and football modes are designed and their analysis is tested in `core/tracking`, but they are not yet wired to sensors. See the [roadmap](docs/roadmap.md).

## Architecture & Modules

FAST separates platform-agnostic domain logic from Android UI/sensor implementations into pure Kotlin core modules:

| Module | Type | Description |
|---|---|---|
| `wear/` | Android App | Wear OS app (Compose for Wear OS, Material 3) |
| `mobile/` | Android App | Companion phone app (Jetpack Compose, Material 3) |
| `core/model/` | Pure Kotlin | Domain data models and entities |
| `core/tracking/` | Pure Kotlin | Sprint-test scoring, speed filtering, speed zones, work/rest detection, and session analysis |
| `core/diagnostics/` | Android Library | Hardware capabilities and sensor diagnostics |
| `tools/brand/` | Node.js script | Generates SVG logos and launcher icons from one script |

Both apps share the application ID `com.hananel.fast`, which the Wearable Data Layer requires for them to communicate.

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose, Wear OS Compose (Material 3), Navigation 3
- **Local Storage:** Room Database (local-only source of truth on each device)
- **Build System:** Gradle 9, Android Gradle Plugin (AGP) 9, JDK (JetBrains Runtime bundled with Android Studio)

## Documentation

All project documents live in [`docs/`](docs/README.md) (primarily in Hebrew). Recommended reading order:
1. [One-page Summary (`00-summary.md`)](docs/00-summary.md) — Current project status and overview.
2. [Architecture (`architecture.md`)](docs/architecture/architecture.md) — System modules, tracking engine, and data flow.
3. [Product Spec (`product-spec.md`)](docs/spec/product-spec.md) — Goals, scope, and requirements.
4. [UX Spec (`ux-spec.md`)](docs/spec/ux-spec.md) — Screens, flows, and UI mockups.
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
