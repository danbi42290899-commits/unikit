# UNI-KIT Development Roadmap

Referenced by `architecture.md` and `integration_adapter.md`. Order is
fixed by the approved development strategy; each phase requires explicit
approval before starting (per project rules — do not jump ahead).

| Phase | Scope | Status |
|---|---|---|
| 1 | Mock UNI-HUB (FastAPI server, models, REST + WebSocket API, SQLite, developer browser monitor, tests, docs) | **Done** |
| 2 | Google Glass HUD client | **Done — source complete, offline build verified, WebSocket protocol verified live; on-device/emulator testing still pending (no Android runtime in this environment), see `docs/glass_app.md`** |
| 3 | Android Phone basic app | **Started — camera capture + upload only** (see below); no patient/exam UI yet |
| 4 | Phone ↔ UNI-HUB ↔ Glass synchronization (end-to-end with real clients) | Not started |
| 5 | Raw signal graph components (Phone-side PPG/ECG rendering) | Not started |
| 6 | Patient/examination storage hardening (beyond Phase 1's dev SQLite) | Not started |
| 7 | Examination report ("UNI-KIT Examination Report", not diagnostic) | Not started |
| 8 | Mock endoscope streaming | Not started |
| 9 | Existing Windows signal-program adapter (`ExistingProgramAdapter`) | Blocked — needs PHASE 0 analysis of `inco_notebook`, see `integration_adapter.md` |
| 10 | Raspberry Pi 5 integration (`RaspberryPiSensorProvider`) | Blocked — hardware currently unavailable |

## Phase 1 deliverables (this delivery)

- `server/app/models/` — Patient, Examination, Vitals, DeviceStatus,
  GlassState, Event.
- REST: `POST/GET /patients`, `POST/GET /exams`, `POST /exams/{id}/end`,
  `GET /status`, `GET /devices`, `GET /glass/state`.
- WebSocket: `/ws/telemetry` (broadcast), `/ws/control` (commands:
  `CAPTURE`, `START_RECORDING`, `STOP_RECORDING`, `FREEZE`, `UNFREEZE`,
  `SET_OTOSCOPE_LEFT`, `SET_OTOSCOPE_RIGHT`, `SET_COLPOSCOPE`).
- Synthetic PPG/ECG generation, clearly marked `simulated: true`.
- SQLite dev database, structured logging, device heartbeat/staleness
  tracking.
- Developer browser monitor at `/monitor/` (not a Phone/Glass substitute).
- `pytest` suite covering REST + WebSocket behavior (11 tests, all passing
  as of this delivery).
- This `docs/` set.

## Phase 2 deliverables (this delivery)

- `glass-app/` -- Kotlin/Android project, `minSdk 26` / `targetSdk 27`
  (Glass EE2's actual Android 8.1 / API 27), no Google Play Services
  dependency, OkHttp-based WebSocket clients.
- `network/` -- `UniHubConfig`, `ReconnectingWebSocketClient` (bounded
  exponential backoff), `TelemetryClient`, `ControlClient`.
- `model/` -- `GlassStateMessage` (1:1 mirror of server `GlassState`),
  telemetry message parsing.
- `repository/UniHubRepository` -- combines both clients, tracks
  `lastTelemetryReceivedAt`, exposes connection state + capture results.
- `ui/GlassHudActivity` + `res/layout/activity_glass_hud.xml` -- the HUD
  per the approved mockup (BP/SpO2/HR/Temp top-left, patient+connection
  top-right, exam mode bottom-left, recording bottom-right, empty center
  reserved for future endoscope video).
- `input/GlassGestureListener` -- TAP wired to CAPTURE; double
  tap/long-press/swipe implemented as real gesture callbacks but stubbed
  to no-ops, ready for Freeze/Record/navigation later.
- `docs/glass_app.md` -- full build/install/debug instructions and Phase 2
  verification checklist.

Full verification detail, including what still requires the user's
Windows PC / physical Glass EE2, is in `docs/glass_app.md`.

## Camera relay (added out of phase order)

Driven by an explicit ask to get the phone's camera showing on Glass
immediately, ahead of the rest of Phase 3:

- `server/app/ws/camera.py` + `camera_relay.py` -- `/ws/camera` binary
  WebSocket relay. `?role=PHONE` uploads one JPEG per binary frame;
  `?role=GLASS` receives every frame relayed verbatim. Separate from
  `/ws/telemetry` so frame bytes never share a channel with JSON vitals.
  New `PHONE_CAMERA` device in the registry, tracked the same way as
  `GLASS`.
- `glass-app` -- `CameraClient` (binary-frame counterpart to
  `TelemetryClient`), decodes off the main thread, renders into the
  `imageCamera` view that now fills the same center area
  `docs/glass_app.md` originally reserved for a future endoscope feed.
- `phone-app/` -- new, minimal: CameraX capture + `CameraUploadClient`
  (same bounded-backoff reconnect pattern as glass-app). No patient/exam
  UI, no `/ws/control` or `/ws/telemetry` client yet -- just enough to get
  a live feed onto Glass. The rest of Phase 3 (patient/exam UI, control
  commands from the phone) is still not started.

## Explicitly not started

Patient/exam UI, `/ws/control` command sending, and `/ws/telemetry`
consumption on the phone side. Do not build these until Phase 3 is
explicitly approved as a whole.
