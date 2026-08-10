# UNI-KIT Glass App (Phase 2)

Status: **source complete, offline Gradle build verified (`assembleDebug`
succeeds, produces a real APK), full WebSocket protocol verified against
the live Mock UNI-HUB. Not yet run on an emulator or physical Glass EE2**
-- this dev server has no Android emulator and no physical Glass attached.
See "What still needs your Windows PC / physical Glass" at the bottom.

## 1. Project file tree

```
glass-app/
  settings.gradle
  build.gradle                  root buildscript (AGP 8.13.0, Kotlin 1.9.24)
  gradle.properties
  gradlew, gradlew.bat
  gradle/wrapper/                Gradle 8.14.3
  local.properties                sdk.dir -- machine-specific, see below
  app/
    build.gradle                 minSdk 26, targetSdk 27, compileSdk 34
    proguard-rules.pro
    src/main/
      AndroidManifest.xml
      java/com/unikit/glass/
        network/
          UniHubConfig.kt              centralized host/port config
          ReconnectingWebSocketClient.kt  shared backoff/reconnect base class
          TelemetryClient.kt            /ws/telemetry subscriber
          ControlClient.kt              /ws/control command sender
        model/
          GlassStateMessage.kt         1:1 mirror of server GlassState
          TelemetryMessage.kt          sealed type + JSON parser
          JsonExt.kt                   null-safe org.json helpers
        state/
          UniHubConnectionState.kt     CONNECTING/CONNECTED/DISCONNECTED/RECONNECTING
        repository/
          UniHubRepository.kt          combines both clients, staleness watchdog
        input/
          GlassGestureListener.kt      TAP -> capture; other gestures stubbed
        ui/
          GlassHudActivity.kt          the one Activity; renders HUD, owns lifecycle
      res/
        layout/activity_glass_hud.xml  the HUD layout (see section 3)
        values/{colors,strings,styles,themes}.xml
        drawable/, mipmap-anydpi-v26/  launcher icon
```

## 2. Architecture

```
Mock UNI-HUB (server/, already running)
        |
        | ws://<host>:<port>/ws/telemetry?client=GLASS   (broadcast, read-only)
        | ws://<host>:<port>/ws/control                  (commands + acks)
        |
GlassHudActivity
        |
        +-- UniHubRepository            <- single source the UI reads from
                |
                +-- TelemetryClient      (extends ReconnectingWebSocketClient)
                +-- ControlClient        (extends ReconnectingWebSocketClient)
```

Glass is a pure client of UNI-HUB, per your instruction. It:
- never talks to a future Phone app directly (there's no code path that
  could -- the only network peer configured anywhere is UNI-HUB),
- never creates patients or exams (that stays the Phone's job later; in
  Phase 2 testing, a patient/exam is created via the developer monitor or
  `curl`, exactly as documented in `docs/api_spec.md`),
- renders **only** from `GlassState` fields it receives verbatim
  (`model/GlassStateMessage.kt` is a field-for-field copy of the server's
  `GlassState` -- see `docs/data_contracts.md#glassstate`). There is no
  second, competing state shape.

**Why two separate WebSocket clients instead of one:** this mirrors the
server's own separation (`docs/websocket_spec.md`) -- `/ws/telemetry` is
broadcast-only and `/ws/control` is where a sent command's ack actually
comes back. `ReconnectingWebSocketClient` holds the shared backoff/retry
logic so neither client duplicates it.

**Why the repository renders vitals from `glass_state` and not from raw
`vitals` messages:** the project's instructions say to use the existing
server-side GlassState as *the* shared state and not invent a competing
one. `GlassState` already mirrors current vitals once an exam is active,
so that's what the HUD binds to. Glass still fully subscribes to
`/ws/telemetry` (checklist item 3) -- every message of every type on that
socket, `vitals` and `raw_signal` included, feeds `lastTelemetryReceivedAt`
for the connection-liveness watchdog, they're just not separately
deep-parsed for display since `glass_state` already carries the numbers.

## 3. HUD layout

`res/layout/activity_glass_hud.xml`, a `ConstraintLayout` with four
corner blocks and an empty center:

```
BP 118/76                  KJH / F32
SpO2 98%                   ● CONNECTED
HR 72                      SIM
TEMP 36.7 C

          ENDOSCOPE VIEW      <- FrameLayout, empty, reserved for a future
                                  video Surface. Its constraints pin it
                                  between the vitals block and the bottom
                                  row, so adding a video view here later
                                  does not move anything else.

OTOSCOPE-L                 ● REC 01:24
```

A dismissable/persistent banner (`textBanner`) overlays the center on:
- `DISCONNECTED` -- shown continuously while the link is down, blocks
  nothing else from rendering (vitals are already blanked underneath).
- `RECONNECTED` -- shown for 1.5s after recovering from a DISCONNECTED or
  RECONNECTING state, then auto-hides.
- `CAPTURE SAVED` / `CAPTURE FAILED` / `NOT CONNECTED` -- shown for 1s
  after a TAP.

## 4. Build instructions

This dev server has the Android SDK (platforms 34/36, build-tools 34/35),
JDK 17, and Gradle 8.14.3 (cached) -- but no emulator and no physical
Glass. The build below is verified to succeed here.

```bash
cd /home/dbkim/unikit/glass-app
export JAVA_HOME=/home/dbkim/jdk-17.0.13+11   # AGP 8.13 requires JDK 17
export PATH=$JAVA_HOME/bin:$PATH

./gradlew --offline assembleDebug
```

`--offline` is what this environment needs (no live internet access here;
every dependency was already cached from building `reguard-app` earlier).
**On your Windows PC, drop `--offline`** -- Gradle will fetch anything not
already cached normally:

```bat
gradlew.bat assembleDebug
```

Confirmed output on this server:
```
BUILD SUCCESSFUL in 1s
37 actionable tasks: 5 executed, 32 up-to-date
```
APK produced at `app/build/outputs/apk/debug/app-debug.apk` (~4.2 MB).

`local.properties` (`sdk.dir=/home/dbkim/android-sdk`) is this machine's
path -- Android Studio on your Windows PC will regenerate it to point at
your own SDK automatically on first open; you don't need to edit it by
hand unless you're driving Gradle from the command line there too.

## 5. Installing on Google Glass Enterprise Edition 2

Glass EE2 doesn't have the Play Store enabled by default for sideloaded
apps -- install via ADB over USB (or ADB over Wi-Fi if you've enabled it
in Glass's developer settings):

1. Enable Developer Options + USB debugging on the Glass unit (Settings ->
   About -> tap Build Number 7 times, then Settings -> Developer Options ->
   USB debugging).
2. Connect Glass to your Windows PC via USB-C.
3. Confirm ADB sees it (see section 6).
4. Install: `adb install -r app-debug.apk`
5. Launch from the Glass app launcher, or directly via ADB (see section 6).

## 6. ADB installation / launch commands

```bash
adb devices                       # confirm Glass shows up, "device" not "unauthorized"
adb install -r app-debug.apk      # -r = reinstall over an existing copy

# launch directly
adb shell am start -n com.unikit.glass/.ui.GlassHudActivity

# launch with a one-off UNI-HUB address override (no rebuild needed)
adb shell am start -n com.unikit.glass/.ui.GlassHudActivity \
    --es uni_hub_host 192.168.0.42 --ei uni_hub_port 8000

# uninstall
adb uninstall com.unikit.glass
```

## 7. Configuring the UNI-HUB server address

Single source of truth: `app/build.gradle`'s `defaultConfig` block --

```groovy
buildConfigField "String", "UNI_HUB_HOST", "\"192.168.0.10\""
buildConfigField "int", "UNI_HUB_PORT", "8000"
```

Change these two values to your Mock UNI-HUB machine's LAN IP and port,
rebuild. `network/UniHubConfig.kt` is the only place in the app that reads
them; every URL (`telemetryUrl`, `controlUrl`, `baseHttpUrl`) is derived
from `UniHubConfig.host`/`port`, nothing else hardcodes an address.

For same-session testing without a rebuild, pass Intent extras at launch
(see section 6) -- `GlassHudActivity` applies `uni_hub_host` /
`uni_hub_port` overrides before starting the repository. This does not
persist across app restarts; that's intentional for Phase 2 (a real
settings UI, possibly Phone-assisted per the project brief, is future
work, not yet built).

**Both your Windows PC (running Mock UNI-HUB) and the Glass unit must be
on the same Wi-Fi network/subnet**, and the Mock UNI-HUB's `uvicorn` must
bind to `0.0.0.0` (not `127.0.0.1`) for Glass to reach it -- see
`unikit/README.md`'s run command; use
`uvicorn app.main:app --host 0.0.0.0 --port 8000` for this test.

## 8. Debug instructions

```bash
adb logcat | grep -E "GlassHudActivity|TelemetryClient|ControlClient|GlassGestureListener|ReconnectingWebSocketClient|TelemetryParser"
```

What you should see in a healthy run:
```
I/GlassHudActivity: UNI-KIT Glass HUD started; telemetry=ws://... control=ws://...
I/TelemetryClient: connecting to ws://...
I/TelemetryClient: connected
I/ControlClient: connecting to ws://...
I/ControlClient: connected
D/TelemetryClient: glass_state received (examId=...)
I/GlassGestureListener: tap -> CAPTURE
I/ControlClient: CAPTURE sent
I/ControlClient: CAPTURE acknowledged (ok=true)
```

On Wi-Fi loss, expect (no crash, no restart needed):
```
W/ReconnectingWebSocketClient: connection failure: ...
I/ReconnectingWebSocketClient: reconnecting in 1000ms (attempt 1)
I/ReconnectingWebSocketClient: reconnecting in 2000ms (attempt 2)
...
I/TelemetryClient: connected
```

Raw PPG/ECG/vitals/device_status frames are intentionally **not** logged
per-message (they arrive several times a second) -- only `glass_state`
changes and control traffic are logged, per your logging requirements.

## 9. Known limitations (Phase 2, by design)

- **Not tested on an emulator or physical Glass EE2.** This environment
  has neither. `./gradlew assembleDebug` succeeding proves the code
  compiles and type-checks correctly and produces a valid, correctly
  targeted (`minSdk=26`, `targetSdk=27`) APK; it does not prove on-device
  rendering or touchpad gesture recognition. The full WebSocket
  request/response pipeline (telemetry subscribe, glass_state updates,
  control commands, CAPTURE ack, device disconnect detection) *was*
  verified against the live Mock UNI-HUB using a script that speaks the
  exact same protocol the app's Kotlin code does -- see the verification
  checklist below for what that covers and doesn't.
- **Recording duration is client-side, not server-authoritative.**
  `GlassState.recording` is a boolean with no start timestamp, so
  `formatRecordingLabel()` starts its own clock the first time it observes
  `recording == true`. If Glass reconnects mid-recording after being
  disconnected, the displayed duration resets rather than reflecting the
  server's actual elapsed time. Fixing this properly means adding a
  `recordingStartedAt` field to server `GlassState` -- an API contract
  change, which you said not to make unless absolutely necessary, so it's
  flagged here instead.
- **No persisted UNI-HUB address setting.** Intent-extra override
  (section 7) is session-only. A real settings screen (or Phone-assisted
  provisioning, per the project brief) is future work.
- **No automated tests.** Phase 2's pure-logic bits (`examModeLabel`,
  backoff timing, etc.) live as private members of an `Activity` and
  helper classes that touch Android framework types (`View`, `Handler`),
  so exercising them needs either an emulator/device or a Robolectric
  setup -- neither is available in this environment. The protocol-level
  Python simulation (see checklist) is the substitute verification for
  this delivery.
- **Only TAP is wired up**, exactly as scoped. Double tap, long press, and
  swipe are implemented as real `GestureDetector` callbacks (so the
  gesture *pipeline* is real, not stubbed at the detection level) but each
  currently only logs and calls a no-op lambda.
- **`app:1.6.4` coroutines and `androidx.lifecycle` runtime-ktx were
  deliberately avoided** (the latter isn't cached in this offline
  environment) in favor of a plain `CoroutineScope` owned and cancelled
  manually by the Activity. Functionally equivalent for a single-Activity
  app; if the project grows more screens later, moving to
  `lifecycleScope`/`repeatOnLifecycle` (both trivial swaps) would be the
  natural next step, but requires network access to fetch that artifact
  once, wherever the build actually runs.

## 10. Phase 2 verification checklist

| # | Requirement | Verified how | Result |
|---|---|---|---|
| 1 | Glass app starts | `./gradlew assembleDebug` succeeds; produces a valid, correctly-targeted APK | **PASS (build-level)** -- not launched on-device |
| 2 | Connects to Mock UNI-HUB over Wi-Fi | Protocol-identical Python client connected to `/ws/telemetry?client=GLASS` and `/ws/control` against the live server | **PASS (protocol-level)** |
| 3 | Subscribes to WebSocket telemetry | Same as above -- received `vitals`, `glass_state`, `device_status` frames | **PASS (protocol-level)** |
| 4 | Subscribes to shared GlassState | Received and parsed real `glass_state` broadcasts | **PASS (protocol-level)** |
| 5 | Patient info appears | `glass_state.patientDisplayName/age/sex` observed = `"Glass Test" / M29` for the test patient | **PASS (protocol-level)** |
| 6 | BP appears | `glass_state.bpSys/bpDia` observed (e.g. 118/74) | **PASS (protocol-level)** |
| 7 | SpO2 appears | `glass_state.spo2` observed (e.g. 97%) | **PASS (protocol-level)** |
| 8 | Heart rate appears | `glass_state.heartRate` observed (e.g. 74) | **PASS (protocol-level)** |
| 9 | Temperature appears | `glass_state.temperature` observed (e.g. 36.6 C) | **PASS (protocol-level)** |
| 10 | Exam mode appears | `SET_OTOSCOPE_LEFT`/`_RIGHT`/`SET_COLPOSCOPE` all triggered server-side; resulting `examMode` correctly mapped by the app's exact label function to `OTOSCOPE-L` / `OTOSCOPE-R` / `COLPOSCOPE` | **PASS (protocol-level)** |
| 11 | Connection state appears | `connectionState` StateFlow design verified by code review; server-side GLASS device flip to CONNECTED/DISCONNECTED confirmed live | **PASS (protocol-level for the server half); on-device rendering not observed** |
| 12 | Recording state appears | `formatRecordingLabel()` logic reviewed against `GlassState.recording`/`frozen` fields | **Implemented, not exercised live this round** (Phase 1's own verification already exercised `START_RECORDING`/`STOP_RECORDING` server-side) |
| 13 | TAP -> CAPTURE | Sent `{"command":"CAPTURE"}` on `/ws/control` (same call `GlassGestureListener`'s `onTap` triggers), received `command_ack` `ok:true`, confirmed a `CAPTURE` event persisted in SQLite tagged with the active `examId` | **PASS (protocol-level)** |
| 14 | Shows DISCONNECTED on lost connection | Closed the telemetry socket; server immediately flipped the `GLASS` device entry to `DISCONNECTED` (confirmed via `GET /devices`) -- this is exactly the signal the app's `ReconnectingWebSocketClient.onFailure/onClosed` callbacks react to | **PASS (server-side trigger confirmed); on-device blanking not observed** |

**Bottom line:** every requirement that can be verified without a running
Android runtime has been -- the WebSocket protocol, message parsing logic,
label mapping, and command pipeline all check out against the real Mock
UNI-HUB, and the project compiles into a correctly-configured APK. What's
*not* verified is anything about actual on-screen rendering or physical
touchpad gesture recognition, because this environment has no emulator and
no Glass hardware.

## What still needs your Windows PC / physical Glass

- Actually installing `app-debug.apk` (section 6) and confirming the HUD
  renders as designed (section 3) on the real 640x360-ish Glass prism
  display -- text sizing/placement was chosen by eye reading the mockup,
  not validated on the actual optics.
- Confirming touchpad TAP is recognized as `onSingleTapConfirmed` (not
  swallowed as the first half of a double-tap, or misfiring against
  Glass's own system gestures) on real Glass EE2 hardware.
- Confirming the `DISCONNECTED`/`RECONNECTED` banner and vitals blanking
  actually happen visibly within ~5s of pulling Wi-Fi, per the staleness
  watchdog design.
- Running the Mock UNI-HUB with `--host 0.0.0.0` on your Windows PC and
  confirming Glass can reach it over your actual local Wi-Fi (this dev
  server and your Glass unit are not on the same network).
