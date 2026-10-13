# Track-Runner (brand: FAST)

Speed tracker for Wear OS (OnePlus Watch 2 and newer) with a companion Android phone app. Two modes, run and football. Local-only: no backend, no accounts.

## Read first

- `docs/00-summary.md` — where the project stands.
- `docs/architecture/decisions.md` — what is decided, and what is still waiting on Hananel. Do not settle an open question (Q*) on your own; ask him.
- `docs/roadmap.md` — the current milestone.
- `docs/research/06-football-tracking.md` — the definitions behind the football mode.
- `docs/spec/sprint-test.md` — the sprint test and how its confidence score is built.
- `docs/spec/training-recording.md` and `docs/spec/phone-app.md` — what is actually built on each device, with screenshots, and what has and has not been verified.

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
- Real recordings pulled from the devices are backed up in `field-data/<date>/` (git-ignored; they hold real positions). Back up before anything else whenever new data is on the watch: Hananel asked for exactly that. `tools/analysis/*.py` read those files and print numbers only, never a position — `session_report.py`, `peak_compare.py`, `burst_report.py`, `burst_trace.py`, and `compare_devices.py <watch.fast> <phone.fast>` for one run recorded on both devices (the phone's half comes from Settings → "מדידה מהטלפון" and is stored in `files/reference/`, outside the history and never synced). Findings: `docs/research/field-tests/2026-10-13-first-real-sessions.md`.
- What the real watch's GPS does is written up in `docs/research/field-tests/2026-10-11-first-real-gps.md`: one fix a second, a speed on every fix in 0.1-knot steps, accuracy fields that are constants, and a hint that the reported speed lags by a second or two.
- Sprint tests are logged on the watch in the app's private storage. Pull them from a debug build with `adb -s <watch> exec-out run-as com.hananel.fast cat files/sprints/results.csv` (one row per test), `.../files/sprints/sprint-<utc>.csv` (raw fixes) and `.../files/sprints/gnss-log.txt` (how each GPS search went: satellites heard, time to first fix, whether fixes carried speed — no positions, safe to quote). The raw files contain positions: analyse them locally, never paste coordinates into chat, docs or commits.
- The home screen shows the installed version at the bottom. Bump `versionName` in `wear/build.gradle.kts` whenever a build goes to the real watch, so "which build is on my wrist?" has an answer.
- Simulating a training on the Wear emulator: `python tools/sim/simulate.py football|run|sprint|stand [serial]` feeds one GPS fix a second plus heart rate, following a script (it wraps `adb emu geo fix <lon> <lat> <alt> <sats> <knots>` and `adb emu sensor set heart-rate <bpm>`). Start it before tapping Start; a rest only shows after a full minute of standing. `pm grant com.hananel.fast android.permission.ACCESS_FINE_LOCATION` (and `BODY_SENSORS`, `POST_NOTIFICATIONS`) skips the dialogs. The emulator drops some fixes, so peaks vary by a km/h between runs.
- `pm clear` also resets the app's language. Put it back with `adb shell cmd locale set-app-locales com.hananel.fast --user 0 --locales he-IL`; Hebrew is the language to check screens in.
- Recorded sessions live in the app's private storage as `files/sessions/<id>.fast` (plain text). Pull one with `adb exec-out run-as com.hananel.fast cat files/sessions/<id>.fast`. To get a watch recording into the phone emulator without a paired pair: `adb push` it to `/data/local/tmp`, then `adb shell run-as com.hananel.fast cp /data/local/tmp/<id>.fast files/sessions/`, and restart the app.
- **The two emulators cannot be paired** (it needs a Google sign-in on the phone image), so the Wearable API is absent on the phone emulator (`ApiException 17`, logged under `FastSync` and harmless). The watch half of the sync can be checked there — `adb shell dumpsys activity service WearableService | grep /fast/` lists the offered sessions. The whole path can only be tested on the real devices, where it worked on 2026-10-11: put a session file with `demo=1` in its header into the watch's `files/sessions/`, restart the watch app, and within seconds the phone holds the file and the watch has a `<id>.synced` marker. Remove the probe from both afterwards. Once real sessions exist there is a check that adds nothing to the history: remove one `<id>.synced` marker on the watch (the empty marker only, by its exact name) and start the watch app; the watch offers that session again and the marker returns when the phone confirms (3 seconds on 2026-10-13, phone locked with the app closed). Put a `timeout` on every adb call to the phone: its wireless debugging drops when it dozes, and a call caught by that hangs. Untested still: a session recorded out of range, and a long one.
- The real phone (OnePlus 13, `CPH2653`, Android 16) connects over wireless debugging as `adb-<serial>._adb-tls-connect._tcp`. It has more than one user, so pass `--user 0` to `pm` commands. Do not screenshot it unless FAST is the resumed activity and the screen is on: its lock screen is not ours to capture.
- The phone app's debug build can generate demo sessions, written through the real store and analysis, so they exercise the whole phone side. There is deliberately no button for it (Hananel asked for the demo data to go, 2026-10-14): `adb -s <emulator> shell am start -n com.hananel.fast/.mobile.MainActivity --es demo add` loads them, `--es demo remove` takes them away. Emulators only; never on his phone.
- SDK packages: use the Android CLI, `"$LOCALAPPDATA/Android/Sdk/cmdline-tools/latest/bin/android.exe" sdk install <package>`, with `/`-separated package names (`platforms/android-37.0`). `sdkmanager.bat` mangles `;` when called from Git Bash.

## Rules that are easy to break

- **Same `applicationId` (`com.hananel.fast`) and signing key in `wear` and `mobile`.** The Data Layer stops connecting them otherwise.
- **`wear` and `mobile` never depend on each other.** Shared code goes in `core/`. That includes reading the GPS (`core/gnss`): the phone's reference measurement is only comparable with the watch because both go through the same code.
- **Keep `core` modules pure Kotlin wherever possible** so analysis and the sync protocol run in plain JVM tests.
- **Store raw samples, derive everything else.** Stats carry an `algorithmVersion` and must be recomputable from raw data. Never write a statistic that cannot be regenerated.
- **Speed comes from Doppler (`Location.getSpeed()`), never from differencing positions.** Maximum speed comes from the filtered series, never from a raw sample. **This rule is under review (Q17):** the real watch's reported speed turned out to be smoothed by the receiver and read 17–46% low in four of five short efforts on 2026-10-13, while its positions agreed with the ground covered. Do not change the source on your own — it is Hananel's decision, pending a sprint over a known distance — but do not present today's top speeds as accurate either.
- **Rest is decided from movement, never from heart rate.** Heart rate stays high for minutes after the player stops.
- **A run and a football session differ only by their `ActivityProfile`.** Add a mode by adding a profile, not a code path.
- **Do not report a metric the hardware cannot measure.** Acceleration counts from 1 Hz GPS are deliberately absent.
- **A confidence score must be hard to earn.** A check that cannot be made caps the score; it never counts as passed. Any change to the sprint scoring needs a test for the bad case it guards against.
- **Nothing that measures may live in a screen.** The real watch turns its display off after 5 seconds and whenever the wrist drops, and the activity is stopped with it. Anything that must keep running — a GPS search, a sprint, a recording — belongs in a foreground service (`SprintService` is the pattern), with the screen only observing its state.
- **Read the watch's own GNSS by name (`GPS_PROVIDER`).** The fused provider on a watch can silently return the phone's location.
- **Do not believe the receiver's accuracy fields.** The real watch reports 25 m/s and 125 m on every fix, whatever the sky. Go through `credibleSpeedAccuracy()` / `crediblePositionAccuracy()` (`core/tracking/.../AccuracyClaims.kt`), which treat such figures as not reported, and judge the signal by satellites (`SatelliteView.fixQuality()`). The raw values are still stored as delivered.
- **A session is synced only after the phone acknowledges it is stored.** Never delete an unacknowledged session.
- **A raw session file is a fact; never rewrite one.** Everything shown is derived on reading it (`SessionInsights`), and cached summaries are disposable. Bump `ALGORITHM_VERSION` in `core/data` whenever a session would be read differently, so stale summaries are rebuilt.
- **A speed counts only once a second reading confirms it** (`TrackAssembler`). Do not add a path that takes a top speed, or starts a sprint, from one raw fix.
- **In the UI it is a "speed test" (בדיקת מהירות), not a sprint test.** Hananel renamed it on 2026-10-14: it gives a top speed whether or not the run was a full sprint. Code, file names and `ActivityKind.SPRINT_TEST` keep the old word.
- **The live screen and the saved session run the same analysis** (`LiveAnalyzer` calls `SessionAnalyzer` on what exists so far). Do not track running totals on the side; they would drift from the history.
- **The heart-rate sensor reports on change**, not on a clock: a steady heart sends nothing. Hold the last value and sample it yourself (`RecordingSession.recordHeartRate`).
- **Demo sessions never mix into real numbers**: they are flagged in the file header and ignored for the personal top speed as soon as one real session exists.
- **Everything Hananel starts is saved and listed**, whether or not it produced a result: a sprint test that was really a walk, a training in which he barely moved. A version that discarded or hid such "empty" entries was built on 2026-10-13 and he rejected it outright ("if I started, it should save"). Do not reintroduce it.
- **No manual pause.** Hananel forgets to pause and forgets to resume; stops are detected from movement in both modes, and a forgotten session ends itself. Do not add a pause button without asking him (Q15).
- **Numbers, units and charts stay LTR in the Hebrew UI.** English technical data (the device-check report) is laid out LTR too. Three traps met so far: a number range or a signed number inside a Hebrew string flips (`80–90%` renders as `90%–80`, `−36` as `36−`) unless wrapped in `\u2066…\u2069`; text with no letters such as `×30` takes the layout direction, so force it LTR; and a number with its unit must be one `Text` (or one string resource), not concatenated into a longer sentence.
- Launcher icons and logo SVGs are generated: edit `tools/brand/generate.mjs` and run it, do not edit the outputs.

## Conventions

- Docs are in Hebrew with English technical terms. Code and code comments are in English. UI strings are bilingual (`values/` English, `values-iw/` Hebrew).
- Colours come from `theme/FastTheme.kt` in each app and match `docs/brand/brand.md`. Each activity has one colour and one pictogram on both devices — speed test cyan with a bolt, football lilac with a ball, run sky with a runner (`ui/Activities.kt` and `ui/Icons.kt` on the watch, `ui/Format.kt` and `ui/Icons.kt` on the phone; the two `Icons.kt` are kept identical by hand). Rose stays for sprints and for a doubtful result. No yellow or orange: Hananel rejected the first palette for them.
- Wear UI follows Google's `wear-compose-m3` guidance: one `AppScaffold`, a `ScreenScaffold` per screen, `TransformingLazyColumn` for lists with 5.2% side margins, `MaterialTheme` tokens instead of hard-coded colors.
- Update the relevant doc in the same commit that changes what it describes.
- One commit per complete unit of work on `main`, with a body explaining why. Annotated tag per milestone.
- When a shell heredoc contains Hebrew text, write the script to a file first: an unpaired `'` (the Hebrew geresh, or an English apostrophe) breaks the Bash tool's parsing. The same tool also collapses `\\` to `\` inside a heredoc, so a script that must write a literal backslash escape (`\u2066`) has to come from a file too.
