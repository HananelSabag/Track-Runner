# Track-Runner · FAST

<img src="docs/brand/logo-wordmark.svg" alt="FAST" width="320">

A personal speed tracker for Wear OS, with a companion Android app. It shows how fast you are moving right now, your top speed, and how your speed changed minute by minute — as accurately as the hardware allows, and without ever losing a recording.

Built for the OnePlus Watch 2 (and newer Wear OS watches) and the OnePlus 13.

> **Status: M0 — research, spec and project skeleton.** The apps currently contain one real screen, a device capability check. See the [roadmap](docs/roadmap.md).

## Documentation

All project documents are in Hebrew and live in [`docs/`](docs/README.md). Start with the [one-page summary](docs/00-summary.md).

## Layout

| Module | What it is |
|---|---|
| `wear/` | The watch app (Compose for Wear OS, Material 3) |
| `mobile/` | The phone app (Jetpack Compose, Material 3) |
| `core/model/` | Domain types. Pure Kotlin |
| `core/diagnostics/` | Reads what the device hardware can measure |
| `tools/brand/` | Generates the logo SVGs and launcher icons from one script |

Both apps share the application id `com.hananel.fast`, which the Wearable Data Layer requires for them to talk to each other.

## Build

`java` is not on the PATH on the development machine, so point Gradle at Android Studio's bundled JDK:

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :wear:assembleDebug :mobile:assembleDebug
./gradlew test
```

Regenerate the logo and launcher icons after editing `tools/brand/generate.mjs`:

```bash
node tools/brand/generate.mjs
```
