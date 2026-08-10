# UNI-KIT Mock UNI-HUB — REST API Spec (Phase 1)

Base URL during development: `http://<host>:8000` (see README for exact run
command / port). Interactive OpenAPI docs are auto-served at `/docs` by
FastAPI — treat this file as the narrative companion, not the source of
truth for exact schemas (that's `app/models/*.py`, mirrored in
`docs/data_contracts.md`).

All endpoints return JSON. All timestamps are ISO 8601.

## Patients

### `POST /patients`

Create a patient. `patientId` is caller-supplied.

Request body:
```json
{ "patientId": "P001", "displayName": "KJH", "age": 32, "sex": "F" }
```
`age` and `sex` are optional.

Responses:
- `201` — created, body is the `Patient` resource (`docs/data_contracts.md`).
- `409` — `patientId` already exists.
- `422` — validation error (missing `patientId`/`displayName`).

### `GET /patients/{patientId}`

- `200` — `Patient` resource.
- `404` — not found.

## Examinations

### `POST /exams`

Start an examination for an existing patient. Server generates `examId`.
As a side effect, this call also updates the shared `GlassState`:
`connected=true`, `patientId`/`examId`/`patientDisplayName`/`age`/`sex` are
populated from the patient record, `examMode="NONE"`, `recording=false`,
`frozen=false`.

Request body:
```json
{ "patientId": "P001" }
```

Responses:
- `201` — `Examination` resource, `status: "ACTIVE"`.
- `404` — `patientId` does not exist.

### `GET /exams/{examId}`

- `200` — `Examination` resource.
- `404` — not found.

### `POST /exams/{examId}/end`

Marks the exam `"ENDED"` and stamps `endedAt`. Idempotent — calling it again
on an already-ended exam returns `200` with the unchanged resource rather
than erroring.

If the ended exam is the currently active one in `GlassState`, this also
resets all patient/vitals fields to `null`, clears `examMode`/`laterality`/
`recording`/`frozen`, and sets `connected=false` — this is what makes Glass
show "DISCONNECTED" instead of frozen stale numbers once an exam ends.

- `200` — `Examination` resource, `status: "ENDED"`.
- `404` — not found.

## Status / devices / Glass

### `GET /status`

Coarse server health snapshot — not per-device detail (use `/devices` for
that).

```json
{
  "server": "MOCK_UNI_HUB",
  "mode": "SIMULATION",
  "telemetryClients": 2,
  "glassConnected": true,
  "activePatientId": "P001",
  "activeExamId": "E09A7AC49"
}
```

### `GET /devices`

Returns an array of `DeviceStatus` (see `data_contracts.md`), one entry per
known device name (`UNI_HUB`, `GLASS`, `BLOOD_PRESSURE`, `SPO2`,
`TEMPERATURE`, `ECG`, `STETHOSCOPE`, `ENDOSCOPE`).

### `GET /glass/state`

Returns the current `GlassState` snapshot. Equivalent to the payload you'd
get from the most recent `"type": "glass_state"` message on
`/ws/telemetry` — this endpoint exists so a client can fetch current state
on load without waiting for the next broadcast.

## WebSocket endpoints

See `docs/websocket_spec.md` for `/ws/telemetry` and `/ws/control`.

## Not implemented in Phase 1 (documented for planning only)

These were named in the original UNI-KIT concept doc as "potential future
media endpoints." None of them exist in this codebase yet — listed here so
Phase 2+ work has a place to land without re-deriving the shape from
scratch:

- `POST /exams/{examId}/media/endoscope-image` — still-frame capture upload.
- `POST /exams/{examId}/media/endoscope-video` — video segment upload, or a
  WebRTC signaling endpoint if streaming instead of upload-after-record.
- `GET /exams/{examId}/report` — assembles the "UNI-KIT Examination Report"
  (never "Diagnostic Report") from stored vitals/recordings/media.
- Stethoscope audio: likely WebRTC or a chunked upload similar to video;
  undecided until Phase 8 (Mock endoscope streaming) informs the pattern.

Do not build these until the corresponding roadmap phase is reached and
approved.
