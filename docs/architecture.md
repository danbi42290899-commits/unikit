# UNI-KIT Architecture (Phase 1 snapshot)

## Status

This document describes what is **implemented today** (Mock UNI-HUB, Phase 1)
and what is **planned** (Glass app, Phone app, real hardware). Planned items
are explicitly marked "NOT YET IMPLEMENTED" — nothing in this file should be
read as a claim about code that exists.

Note on provenance: an earlier planning pass for this project assumed access
to an existing Windows signal-acquisition program at
`C:\Users\danbi\Desktop\unikit\notebook.uni\inco_notebook`. That program runs
on the user's local Windows PC; this Claude Code session runs on a separate
remote Linux server with no filesystem access to it. Phase 1 was therefore
built **independently**, using only mock/simulated data, with an explicit
adapter seam (see `integration_adapter.md`) for wiring the real program in
later without redesigning Phone/Glass.

## Target system

```
Sensors (BP cuff, MAX30102, NTC 100K, ECG front-end, stethoscope,
otoscope/colposcope, Arduino Nano 33 BLE Sense x2, ADS1256, I2S mic, IMU)
        |
        v
Raspberry Pi 5 "UNI-HUB"  <-- currently unavailable; see below
        |
        | local Wi-Fi (REST + WebSocket, later WebRTC for video/audio)
        |
   +----+----+
   |         |
   v         v
Android Phone   Google Glass EE2
   App             App
```

Phone and Glass never talk to each other directly. UNI-HUB (real or mock)
is the single source of truth for patient/exam data, vitals, raw signals,
device connection state, and `GlassState`. Both clients subscribe to the
same server state.

## Current state (Phase 1: Mock UNI-HUB only)

```
                 Mock UNI-HUB (this repo, server/)
                 Python + FastAPI, runs on any dev machine
                 (temporary stand-in for the Raspberry Pi 5)
                        |
        REST (/patients, /exams, /status, /devices, /glass/state)
        WebSocket (/ws/telemetry broadcast, /ws/control commands)
                        |
                 Developer browser monitor  <-- only client that exists today
                 (server/app/static/monitor/, served at /monitor/)
```

`glass-app/` (Phase 2) now exists — see `docs/glass_app.md` for its own
architecture writeup. `phone-app/` is **NOT YET IMPLEMENTED** — Phase 3 per
the approved roadmap (`development_roadmap.md`). The developer monitor
still exists to exercise this server independent of either real client;
it is not a substitute for either app.

## Provider abstraction (the swap seam)

```
SensorProvider (ABC)                     server/app/providers/base.py
  +-- VitalsProvider
  +-- PPGProvider
  +-- ECGProvider
  +-- BPProvider
  +-- TemperatureProvider
  +-- StethoscopeProvider   (interface only, no implementation yet)
  +-- EndoscopeProvider     (interface only, no implementation yet)

Implemented today:
  MockVitalsProvider, MockPPGProvider, MockECGProvider
  server/app/providers/mock_provider.py

Planned later (NOT YET IMPLEMENTED):
  ExistingProgramAdapter    -- wraps the Windows inco_notebook program
  RaspberryPiSensorProvider -- talks to real sensors on the Pi 5
```

Routers (`app/routers/*.py`) and the WebSocket layer (`app/ws/*.py`) only
import from `app.providers.base` and `app.models` — never a concrete
provider class by name outside of the single wiring point in
`app/main.py` (`lifespan()`, where `MockVitalsProvider()` /
`MockPPGProvider()` / `MockECGProvider()` are instantiated). Swapping to a
real provider later means changing that one instantiation, not the routers,
not the WebSocket handlers, and not any client.

## GlassState: the shared source of truth

`app/state/glass_state_manager.py` holds one in-memory `GlassState` object
per running server process (see `docs/data_contracts.md` for its fields).
Every mutation goes through `GlassStateManager.update()`, which:

1. Merges the changed fields into the current state.
2. Stamps `lastSeen`.
3. Broadcasts `{"type": "glass_state", ...}` to every `/ws/telemetry`
   subscriber.

This is what makes "Phone selects RIGHT EAR -> UNI-HUB updates state ->
Phone + Glass both re-render" possible: no client ever tells another client
anything directly. Commands arrive on `/ws/control`
(`app/ws/control.py`), mutate `GlassState`, and the resulting broadcast is
what every subscriber (Phone, Glass, or today, the developer monitor) reacts
to.

## Device connection tracking

`app/state/device_registry.py` tracks one `DeviceStatus` per device name
(`UNI_HUB`, `GLASS`, `BLOOD_PRESSURE`, `SPO2`, `TEMPERATURE`, `ECG`,
`STETHOSCOPE`, `ENDOSCOPE`). A device flips back to `DISCONNECTED` if no
heartbeat is seen within `DEVICE_STALE_TIMEOUT_SEC` (5s, `app/config.py`).
This is the mechanism behind the safety rule "disconnected sensors must not
keep showing stale values as current" — clients should treat
`DeviceStatus.state == "DISCONNECTED"` as "hide/gray out this value," not
just cosmetic.

In Phase 1, `UNI_HUB`, `BLOOD_PRESSURE`, `SPO2`, `TEMPERATURE`, `ECG` are
heartbeated automatically by the mock telemetry loop (they're all fed by the
same mock providers). `GLASS` is only marked connected while a client opens
`/ws/telemetry?client=GLASS`. `STETHOSCOPE` and `ENDOSCOPE` have no producer
yet and stay `DISCONNECTED` — this is intentionally honest, not a bug.

## Repository layout

```
unikit/
  docs/                     architecture.md, data_contracts.md,
                             api_spec.md, websocket_spec.md,
                             integration_adapter.md, development_roadmap.md
  server/
    requirements.txt
    app/
      main.py                FastAPI app + lifespan wiring (the swap point)
      config.py               SIMULATION_* constants, DB path, timeouts
      logging_config.py
      db.py                   SQLAlchemy engine/session, init_db()
      models/                 Patient, Exam, Event (SQLAlchemy) +
                               Vitals/RawSignal/Event (Pydantic wire types),
                               DeviceStatus, GlassState
      providers/               SensorProvider family + MockSensorProvider
      state/                   GlassStateManager, DeviceRegistry
      routers/                 patients, exams, status, devices, glass
      ws/                      connection_manager, telemetry, control
      static/monitor/          developer browser monitor (HTML/CSS/JS)
  shared/
    data_contracts_examples/   sample JSON payloads referenced by docs
  tests/                       pytest suite (REST + WebSocket)

  glass-app/                  Phase 2 — Google Glass HUD client, see docs/glass_app.md

  NOT YET CREATED (future phases, see development_roadmap.md):
  legacy/       -- reserved for the Windows inco_notebook program if/when
                   the user chooses to bring a copy into this repo. Nothing
                   is moved there automatically or without permission.
  phone-app/    -- Phase 3
```

## Safety / research-prototype constraints honored in this codebase

- Every vitals/raw-signal/event message sets `"simulated": true"` end to end
  (see `app/providers/mock_provider.py` docstring and `app/config.py`).
- `sampleRate` in raw signal messages is populated from documented
  `SIMULATION_*` constants only in mock mode, and the code comments say
  explicitly these are not real hardware specs.
- No blood-pressure-from-PPG inference, no automatic diagnosis, no
  healthy/unhealthy classification exists anywhere in this codebase.
- `docs/api_spec.md` / report generation language (future) must keep using
  "UNI-KIT Examination Report," never "Diagnostic Report."
