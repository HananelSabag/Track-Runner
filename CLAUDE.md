# Track-Runner (brand: FAST)

Speed tracker for Wear OS (OnePlus Watch 2 and newer) with a companion Android phone app. Two modes, run and football. Local-only: no backend, no accounts.

## Read first

- `docs/00-summary.md` — where the project stands.
- `docs/architecture/decisions.md` — what is decided, and what is still waiting on Hananel. Do not settle an open question (Q*) on your own; ask him.
- `docs/roadmap.md` — the current milestone.
- `docs/research/06-football-tracking.md` — the definitions behind the football mode.
- `docs/spec/sprint-test.md` — the sprint test and how its confidence score is built.

## Build, test, run

`java` is not on PATH. Use Android Studio's JDK (JDK 25). Note the folder name: the 2026.2.1 update installed into `Android Studio1`, and the old `Android Studio` folder holds a broken leftover JDK that must not be used:

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio1/jbr"
./gradlew :wear:assembleDebug :mobile:assembleDebug
./gradlew test
```

- Toolchain: Gradle 9.8.1, AGP 9.4.1 with built-in Kotlin (there is no `kotlin-android` plugin — do not add it back), compileSdk 37, targetSdk 36.
- Emulators: `Wear_OS_5_Large_Round` (API 34) for the watch, `Pixel_9_Pro_Fold` (API 36) for the phone. Start headless with `emulator -avd <name> -no-window -no-audio`. Run one at a time, or pass `-s <serial>` to adb.
- The phone AVD has two displays, so screenshots need `adb exec-out screencap -d <displayId> -p` (list ids with `adb shell dumpsys SurfaceFlinger --display-id`). Installs can fail on low emulator storage; retry once.
- The real watch (OnePlus Watch 2, `OPWWE231`, Wear OS 5 / API 34) is paired with this PC for wireless debugging. Find its current port with `adb mdns services` (the `_adb-tls-connect` line; the port changes whenever wireless debugging restarts), then `adb connect <ip:port>`. The IP that mDNS advertises can be wrong: on 2026-10-10 it advertised 192.168.1.205 while the watch answered on 192.168.1.211, so if the connection is refused try the advertised port on the last address that worked. Wireless debugging switches itself off when the watch leaves Wi-Fi; Hananel has to turn it back on in Developer options (no re-pairing). While it sits on the charger its charging screen covers every app, so screenshots show only that and taps cannot reach the app. `adb shell dumpsys location` on the watch is the best evidence of what the GPS actually did: the `gps provider +registration/-registration` lines show how long each search really lasted, and the `GNSS_KPI` block shows fixes delivered and signal strength.
- Sprint tests are logged on the watch in the app's private storage. Pull them from a debug build with `adb -s <watch> exec-out run-as com.hananel.fast cat files/sprints/results.csv` (one row per test), `.../files/sprints/sprint-<utc>.csv` (raw fixes) and `.../files/sprints/gnss-log.txt` (how each GPS search went: satellites heard, time to first fix, whether fixes carried speed — no positions, safe to quote). The raw files contain positions: analyse them locally, never paste coordinates into chat, docs or commits.
- The home screen shows the installed version at the bottom. Bump `versionName` in `wear/build.gradle.kts` whenever a build goes to the real watch, so "which build is on my wrist?" has an answer.
- Simulating a sprint on the Wear emulator: `adb -s emulator-5554 emu geo fix <lon> <lat> <alt> <sats> <knots>` once a second; `pm grant com.hananel.fast android.permission.ACCESS_FINE_LOCATION` skips the dialog.
- SDK packages: use the Android CLI, `"$LOCALAPPDATA/Android/Sdk/cmdline-tools/latest/bin/android.exe" sdk install <package>`, with `/`-separated package names (`platforms/android-37.0`). `sdkmanager.bat` mangles `;` when called from Git Bash.

## Rules that are easy to break

- **Same `applicationId` (`com.hananel.fast`) and signing key in `wear` and `mobile`.** The Data Layer stops connecting them otherwise.
- **`wear` and `mobile` never depend on each other.** Shared code goes in `core/`.
- **Keep `core` modules pure Kotlin wherever possible** so analysis and the sync protocol run in plain JVM tests.
- **Store raw samples, derive everything else.** Stats carry an `algorithmVersion` and must be recomputable from raw data. Never write a statistic that cannot be regenerated.
- **Speed comes from Doppler (`Location.getSpeed()`), never from differencing positions.** Maximum speed comes from the filtered series, never from a raw sample.
- **Rest is decided from movement, never from heart rate.** Heart rate stays high for minutes after the player stops.
- **A run and a football session differ only by their `ActivityProfile`.** Add a mode by adding a profile, not a code path.
- **Do not report a metric the hardware cannot measure.** Acceleration counts from 1 Hz GPS are deliberately absent.
- **A confidence score must be hard to earn.** A check that cannot be made caps the score; it never counts as passed. Any change to the sprint scoring needs a test for the bad case it guards against.
- **Nothing that measures may live in a screen.** The real watch turns its display off after 5 seconds and whenever the wrist drops, and the activity is stopped with it. Anything that must keep running — a GPS search, a sprint, a recording — belongs in a foreground service (`SprintService` is the pattern), with the screen only observing its state.
- **Read the watch's own GNSS by name (`GPS_PROVIDER`).** The fused provider on a watch can silently return the phone's location.
- **A session is synced only after the phone acknowledges it is stored.** Never delete an unacknowledged session.
- **Numbers, units and charts stay LTR in the Hebrew UI.** English technical data (the device-check report) is laid out LTR too.
- Launcher icons and logo SVGs are generated: edit `tools/brand/generate.mjs` and run it, do not edit the outputs.

## Conventions

- Docs are in Hebrew with English technical terms. Code and code comments are in English. UI strings are bilingual (`values/` English, `values-iw/` Hebrew).
- Colours come from `theme/FastTheme.kt` in each app and match `docs/brand/brand.md`. No yellow or orange: Hananel rejected the first palette for them.
- Wear UI follows Google's `wear-compose-m3` guidance: one `AppScaffold`, a `ScreenScaffold` per screen, `TransformingLazyColumn` for lists with 5.2% side margins, `MaterialTheme` tokens instead of hard-coded colors.
- Update the relevant doc in the same commit that changes what it describes.
- One commit per complete unit of work on `main`, with a body explaining why. Annotated tag per milestone.
- When a shell heredoc contains Hebrew text, write the script to a file first: an unpaired `'` (the Hebrew geresh) breaks the Bash tool's parsing.
