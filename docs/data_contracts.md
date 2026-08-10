# UNI-KIT Data Contracts

Source of truth for these shapes is the code — this document mirrors
`server/app/models/*.py` and `server/app/state/*.py`. If they drift, trust
the code and update this file.

Every message on `/ws/telemetry` shares a common envelope idea (per the
approved design): `patientId`, `examId`, `timestamp`, `sourceDevice`,
`quality` (where applicable), `simulated`. Below is the concrete shape per
message `type`.

## Patient (REST resource, `app/models/patient.py`)

```json
{
  "patientId": "P001",
  "displayName": "KJH",
  "age": 32,
  "sex": "F",
  "createdAt": "2026-08-10T09:45:55.718179"
}
```

`patientId` is caller-supplied (not server-generated) and must be unique;
`POST /patients` with a duplicate returns `409`.

## Examination (REST resource, `app/models/exam.py`)

```json
{
  "examId": "E09A7AC49",
  "patientId": "P001",
  "startedAt": "2026-08-10T09:45:55.747089",
  "endedAt": null,
  "status": "ACTIVE"
}
```

`examId` is server-generated (`E` + 8 hex chars) on `POST /exams`. `status`
is `"ACTIVE"` or `"ENDED"`. Ending an already-ended exam is idempotent
(returns 200 with the existing row, does not error).

## Vitals message (`app/models/vitals.py::VitalsMessage`)

Broadcast on `/ws/telemetry` roughly once per second by the mock telemetry
loop.

```json
{
  "type": "vitals",
  "patientId": "P001",
  "examId": "E09A7AC49",
  "timestamp": "2026-08-10T09:46:00.123456+00:00",
  "sourceDevice": "MOCK_UNI_HUB",
  "quality": "good",
  "simulated": true,
  "data": {
    "bpSys": 118,
    "bpDia": 76,
    "spo2": 98,
    "heartRate": 72,
    "temperature": 36.7
  }
}
```

`patientId` / `examId` are `null` when no exam is active — the hub still
broadcasts vitals in that case (useful for bench-testing the hub itself),
but clients should not attribute them to a patient.

`quality` is one of `"good" | "fair" | "poor" | "unknown"`. Phase 1's mock
provider always reports `"good"` — it does not attempt to simulate signal
degradation.

## Raw signal message (`app/models/vitals.py::RawSignalMessage`)

```json
{
  "type": "raw_signal",
  "patientId": "P001",
  "examId": "E09A7AC49",
  "signal": "PPG_IR",
  "sampleRate": 50,
  "unit": "ADC_COUNT",
  "channel": "default",
  "timestamp": "2026-08-10T09:46:00.323456+00:00",
  "sourceDevice": "MOCK_UNI_HUB",
  "quality": "good",
  "simulated": true,
  "samples": [50012.3, 50040.1, "... 10 values per chunk in mock mode"]
}
```

`signal` is one of `"PPG_RED" | "PPG_IR" | "ECG" | "BP_CUFF_PRESSURE" |
"STETHOSCOPE_AUDIO"`. Phase 1 only produces `PPG_RED`, `PPG_IR`, `ECG`;
`BP_CUFF_PRESSURE` and `STETHOSCOPE_AUDIO` are reserved for when real
hardware (or the existing Windows program) supplies them — **do not invent
data for these**.

`sampleRate` is nullable on the wire. In mock mode it is filled from
`app.config.SIMULATION_PPG_SAMPLE_RATE_HZ` (50 Hz) /
`SIMULATION_ECG_SAMPLE_RATE_HZ` (100 Hz) — **arbitrary UI-development
constants, not measured or datasheet hardware sampling rates**. When a real
provider is wired in, it must report its own actual sample rate or `null`
if unknown; it must never inherit the mock constant.

## Event message (`app/models/vitals.py::EventMessage`, broadcast form)

Broadcast on `/ws/telemetry` whenever `/ws/control` accepts a command, and
persisted to the `events` SQLite table (`app/models/event.py`).

```json
{
  "type": "event",
  "patientId": "P001",
  "examId": "E09A7AC49",
  "timestamp": "2026-08-10T09:46:05.000000+00:00",
  "sourceDevice": "MOCK_UNI_HUB",
  "eventType": "SET_OTOSCOPE_RIGHT",
  "detail": {}
}
```

## Device status message (`app/models/device.py::DeviceStatus`)

```json
{
  "type": "device_status",
  "device": "SPO2",
  "state": "CONNECTED",
  "lastSeen": "2026-08-10T09:46:00.507292+00:00",
  "quality": "good",
  "simulated": true
}
```

`device` is one of `UNI_HUB | GLASS | BLOOD_PRESSURE | SPO2 | TEMPERATURE |
ECG | STETHOSCOPE | ENDOSCOPE`. `state` is `CONNECTED | DISCONNECTED`.

## GlassState (`app/models/glass_state.py`)

The single shared state object. Returned by `GET /glass/state` and
broadcast (prefixed with `"type": "glass_state"`) on every mutation.

```json
{
  "connected": true,
  "patientId": "P001",
  "examId": "E09A7AC49",
  "patientDisplayName": "KJH",
  "age": 32,
  "sex": "F",
  "bpSys": 118,
  "bpDia": 76,
  "spo2": 98,
  "heartRate": 72,
  "temperature": 36.7,
  "examMode": "OTOSCOPE_RIGHT",
  "laterality": "RIGHT",
  "recording": false,
  "frozen": false,
  "endoscopeConnected": false,
  "lastSeen": "2026-08-10T09:46:05.000000+00:00",
  "simulated": true
}
```

`examMode` is one of `OTOSCOPE_LEFT | OTOSCOPE_RIGHT | COLPOSCOPE | NONE`.
`laterality` is `LEFT | RIGHT | NONE`. When no exam is active, all
patient/vitals fields are `null`, `connected` is `false`, and clients must
render a disconnected/unavailable state — never the last-known numbers.

## Sample payloads on disk

Copies of the messages above live under
`shared/data_contracts_examples/` for quick reference without booting the
server.
