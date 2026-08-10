# UNI-KIT Mock UNI-HUB — WebSocket Spec (Phase 1)

## `/ws/telemetry` — server-to-client broadcast

Connect: `ws://<host>:8000/ws/telemetry` or
`ws://<host>:8000/ws/telemetry?client=GLASS`.

This is a **broadcast** channel: every connected client (Phone, Glass, the
developer monitor) receives the same messages. There is no per-client
filtering in Phase 1 — a future phase may add subscription filters (e.g.
"only vitals, skip raw_signal") if bandwidth becomes a concern on real Wi-Fi
links, but that does not exist yet.

Clients are not required to send anything after connecting. If a client
*does* send text, the server ignores its content but treats it as a
liveness signal.

### `?client=GLASS` query parameter

If present, the connection is tracked in the device registry: `GLASS`
flips to `CONNECTED` on connect (broadcast as a `device_status` message
immediately), a heartbeat is refreshed at least every ~2s while the socket
stays open, and `GLASS` flips back to `DISCONNECTED` on disconnect (also
broadcast). Omit this parameter for the developer monitor or any client
that shouldn't affect Glass's tracked connection state — the monitor JS
uses `?client=MONITOR`, which the server accepts but does not track (only
the literal value `GLASS` triggers device-registry heartbeating today).

### Message types you will receive

All are JSON objects with a top-level `"type"` field. Full shapes are in
`docs/data_contracts.md`.

| type | when | rate (mock mode) |
|---|---|---|
| `vitals` | always | ~1 Hz |
| `raw_signal` (`signal: PPG_IR`) | always | ~5 chunks/sec, 10 samples/chunk |
| `raw_signal` (`signal: PPG_RED`) | always | ~5 chunks/sec, 10 samples/chunk |
| `raw_signal` (`signal: ECG`) | always | ~5 chunks/sec, 20 samples/chunk |
| `device_status` | on heartbeat/state change | ~1 Hz per heartbeated device, plus immediately on any state flip |
| `glass_state` | on any `GlassState` mutation | event-driven (exam start/end, control commands) |
| `event` | a command was accepted on `/ws/control` | event-driven |

None of the rate numbers above are a real hardware spec — they fall out of
`SIMULATION_TELEMETRY_INTERVAL_SEC` / `SIMULATION_SIGNAL_CHUNK_SEC` /
`SIMULATION_PPG_SAMPLE_RATE_HZ` / `SIMULATION_ECG_SAMPLE_RATE_HZ` in
`app/config.py`.

## `/ws/control` — client-to-server commands

Connect: `ws://<host>:8000/ws/control`.

Send one JSON object per command:
```json
{ "command": "SET_OTOSCOPE_RIGHT" }
```

Valid `command` values:

| command | effect on GlassState |
|---|---|
| `CAPTURE` | no state field changes in Phase 1 (no media pipeline yet); logged as an event only |
| `START_RECORDING` | `recording = true` |
| `STOP_RECORDING` | `recording = false` |
| `FREEZE` | `frozen = true` |
| `UNFREEZE` | `frozen = false` |
| `SET_OTOSCOPE_LEFT` | `examMode = "OTOSCOPE_LEFT"`, `laterality = "LEFT"` |
| `SET_OTOSCOPE_RIGHT` | `examMode = "OTOSCOPE_RIGHT"`, `laterality = "RIGHT"` |
| `SET_COLPOSCOPE` | `examMode = "COLPOSCOPE"`, `laterality = "NONE"` |

Every accepted command:
1. Applies the state change above via `GlassStateManager.update()`, which
   broadcasts the updated `glass_state` on `/ws/telemetry`.
2. Is persisted as a row in the `events` SQLite table
   (`exam_id`, `patient_id`, `event_type`, `created_at`).
3. Is broadcast as an `event` message on `/ws/telemetry`.
4. Gets an ack sent back on the *same* `/ws/control` connection:
   ```json
   { "type": "command_ack", "command": "SET_OTOSCOPE_RIGHT", "ok": true }
   ```

Unknown commands or invalid JSON get an error reply instead of an ack:
```json
{ "type": "error", "message": "unknown command: FOO" }
```
The connection is **not** closed on an error — the client can keep sending.

### Why control and telemetry are separate sockets

Commands (`/ws/control`) and the resulting broadcast (`/ws/telemetry`) are
deliberately different connections. This mirrors the required flow —
"Phone command -> UNI-HUB -> GlassState updated -> Phone + Glass receive new
state" — literally: a client's own command doesn't reach it as a special
direct reply, it comes back the same way every other subscriber gets it, on
`/ws/telemetry`. This is what keeps the Phone Glass-control screen from
becoming disguised screen mirroring.

## `/ws/camera` — binary frame relay (added out of phase order)

Added ahead of the rest of Phase 3, to get a live phone camera feed onto
Glass. Query param `role` is required and must be `PHONE` or `GLASS`;
anything else closes the connection with code 1008.

- `?role=PHONE`: sends one **binary** WebSocket message per JPEG frame.
  Each frame relayed marks the `PHONE_CAMERA` device `CONNECTED` (same
  registry/heartbeat mechanism as `GLASS` on `/ws/telemetry`); on
  disconnect it flips back to `DISCONNECTED`, broadcast on
  `/ws/telemetry` as a normal `device_status` event.
- `?role=GLASS`: receives every frame the current `PHONE` sender relays,
  verbatim, as binary messages. Doesn't need to send anything back;
  text frames it does send are ignored (used only to detect disconnect).
- Deliberately a separate socket from `/ws/telemetry` — frame bytes never
  share a channel with JSON vitals, so the vitals stream's message
  parsing never has to skip binary frames.
- No buffering/backlog: a `GLASS` viewer that connects mid-stream just
  gets frames from that point on, same non-resumable-stream rule as
  `/ws/telemetry`.
- This is a plain per-frame JPEG relay, not WebRTC — simplest thing that
  gets a visible feed onto Glass today. See `docs/development_roadmap.md`
  "Camera relay (added out of phase order)" for why this jumped ahead of
  the rest of Phase 3.

## Not implemented in Phase 1

- No authentication/authorization on any socket (single-user dev LAN
  assumption for now).
- No reconnect/resume-from-sequence-number support — a reconnecting client
  gets the next broadcast, not a backlog. `GET /glass/state` (REST) is the
  way to fetch current state on (re)connect.
- No WebRTC endpoints for endoscope video / stethoscope audio yet.
  `/ws/camera` (above) covers the phone-camera-to-Glass case with a much
  simpler per-frame relay; a real endoscope/stethoscope pipeline may still
  want WebRTC later — see `docs/api_spec.md`'s "Not implemented" section.
