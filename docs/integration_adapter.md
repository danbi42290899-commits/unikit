# Integration Adapter Plan

How the Mock UNI-HUB gets replaced by real data sources later, without
redesigning the Phone or Glass applications. This is a plan for future
phases (9 and 10 in `development_roadmap.md`) — nothing described as
"later" here exists yet.

## Why this works: the seam is already in the code

Phone and Glass (once built) will only ever talk to:
- the REST endpoints in `docs/api_spec.md`
- the two WebSocket channels in `docs/websocket_spec.md`
- the message shapes in `docs/data_contracts.md`

They never import a provider class or know whether a value came from
`MockSensorProvider`, a wrapped Windows program, or real Pi hardware. The
only place a concrete provider is named is one block in
`server/app/main.py`:

```python
vitals_provider = MockVitalsProvider()
ppg_provider = MockPPGProvider()
ecg_provider = MockECGProvider()
task = asyncio.create_task(telemetry_loop(vitals_provider, ppg_provider, ecg_provider))
```

Replacing a provider means changing what gets instantiated here (plus,
likely, adding a small config flag so mock vs. real can be chosen without
editing code — not yet built, trivial when needed). Routers, `app/ws/*`,
`app/state/*`, and every client are untouched.

## A) Wrapping the existing Windows signal program

Location on the user's machine (not accessible from this dev environment):
`C:\Users\danbi\Desktop\unikit\notebook.uni\inco_notebook`, launched via
`run_app.bat`. It is a Python project; beyond that, everything about its
internals — sensor set, serial/BLE/USB protocol, sampling rates, units,
filtering, patient/session model — is **UNKNOWN** until it is actually
inspected (see the still-outstanding PHASE 0 analysis task). Nothing below
assumes specific values from it.

Planned shape once it can be inspected: `ExistingProgramAdapter`, one
concrete class per interface it can actually satisfy
(`VitalsProvider`/`PPGProvider`/`ECGProvider`/etc., from
`app/providers/base.py`), living in a new
`server/app/providers/existing_program_adapter.py`.

Two integration strategies, to be chosen once the program's actual
interface is known:

1. **In-process import**, if `inco_notebook` exposes importable Python
   functions/classes that already parse raw sensor data into values (e.g.
   a function that returns current HR/SpO2, or a generator yielding PPG
   samples). The adapter calls those directly and repackages the result
   into `VitalsData` / `RawSignalMessage`. Fastest path, but couples the
   UNI-HUB process to that program's runtime dependencies.

2. **Out-of-process bridge**, if `inco_notebook` is really a standalone
   app (`run_app.bat` launching a GUI, writing to a file/pipe/local socket,
   etc.) that can't be cleanly imported. The adapter would instead read
   whatever the existing program already emits — a log file, a local TCP
   socket, a serial passthrough — and translate that into the same
   `VitalsData` / `RawSignalMessage` objects. Slower to build, but doesn't
   require modifying or forking the existing program at all (Windows
   engineering constraint: it must remain untouched and unmoved without
   explicit permission).

Either way, the adapter is responsible for:
- Setting `sourceDevice` to something identifying the real origin (e.g.
  `"WINDOWS_INCO_NOTEBOOK"`), not `"MOCK_UNI_HUB"`.
- Setting `simulated: false` (the mock-only guarantee in
  `mock_provider.py`'s docstring does not apply here).
- Reporting the **real** sample rate it actually reads from the program —
  never falling back to `SIMULATION_PPG_SAMPLE_RATE_HZ` /
  `SIMULATION_ECG_SAMPLE_RATE_HZ`. If the real rate is unknown, `sampleRate`
  must be `null`, per the existing nullable contract.
- Reporting real signal quality if the source provides any indicator, else
  `"unknown"` — never inventing a `"good"`.

## B) Raspberry Pi 5 sensor acquisition

Once the Pi 5 is available, `RaspberryPiSensorProvider` implementations
(again, one per interface actually backed by real hardware — BP monitor,
MAX30102, NTC 100K 3950, ECG front-end via ADS1256, stethoscope, IMU, etc.)
replace the mock providers the same way. The Pi is expected to run this
same `server/` FastAPI app directly (it's already Python + FastAPI, no
Windows dependency), reading real sensors over I2C/SPI/UART/BLE instead of
generating synthetic waveforms.

Concretely:
- MAX30102 → `RaspberryPiPPGProvider` (I2C). Actual sample rate comes from
  the MAX30102's configured ADC rate — must be read from register config
  or datasheet-verified, not assumed as 50 Hz (Phase 1's mock value).
- ADS1256 + ECG analog front-end → `RaspberryPiECGProvider` (SPI). Real
  sample rate from ADS1256's configured data rate. Per the safety rules,
  the ADS1256 alone is not treated as a complete, safe patient ECG
  interface — the analog front-end / patient isolation stays a separate
  hardware review, independent of this software adapter.
- NTC 100K 3950 → `RaspberryPiTemperatureProvider`, using the thermistor's
  Steinhart-Hart or beta-equation conversion (implementation deferred to
  Phase 10; do not invent calibration constants ahead of having the actual
  circuit).
- Arduino Nano 33 BLE Sense x2 / MPU6050 / I2S mic + MAX98357A / wireless
  stethoscope / otoscope-colposcope camera: each gets its own adapter
  behind the matching interface (`StethoscopeProvider`, `EndoscopeProvider`,
  or a new interface if none of the existing ones fit) when that phase is
  reached.

`sourceDevice` for these should read `"UNI_HUB"` (matching the "real
Raspberry Pi is the UNI-HUB" framing) or a more specific per-sensor string
if that proves more useful once Phone/Glass UI needs to disambiguate.

## What must NOT change when either adapter lands

- `app/models/*.py` wire-level shapes (`VitalsData`, `RawSignalMessage`,
  `GlassState`, `DeviceStatus`, `EventMessage`) — these are the contract
  Phone/Glass are built against.
- The REST/WebSocket route paths and their request/response shapes.
- The `GlassState` update flow (`Phone command -> UNI-HUB -> GlassState ->
  Phone + Glass`).
- The `DEVICE_STALE_TIMEOUT_SEC` disconnect behavior — a real sensor going
  quiet must still flip its `DeviceStatus` to `DISCONNECTED`, exactly like
  mock devices do today.

## Open item blocking part A

PHASE 0 (analyze the existing Windows program) has not been completed —
this Claude Code environment has no filesystem access to
`C:\Users\danbi\Desktop\unikit\notebook.uni\inco_notebook`. Before writing
`ExistingProgramAdapter`, that program needs to be inspected (file listing,
`run_app.bat` contents, key Python source files) via one of the hand-off
methods discussed earlier (paste contents, zip upload, run inspection
commands locally, or push to a repo this environment can clone).
