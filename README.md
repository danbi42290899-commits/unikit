# UNI-KIT — Mock UNI-HUB (Phase 1)

Research prototype. All vitals/signals produced by this server are
**simulated** — see `docs/architecture.md` for the safety rules this
codebase follows.

Full docs: `docs/architecture.md`, `docs/data_contracts.md`,
`docs/api_spec.md`, `docs/websocket_spec.md`, `docs/integration_adapter.md`,
`docs/development_roadmap.md`.

Everything below runs the server standalone against Mock providers, for
development off the real hardware. To run the server **on the Raspberry
Pi itself as the Wi-Fi access point** Phone and Glass join directly, see
`pi/README.md` instead.

## Setup

This machine's system Python (3.8) can't build a `venv` (`ensurepip`
missing, no sudo), so this project uses a conda environment instead.

```bash
source ~/miniconda3/etc/profile.d/conda.sh
conda create -n unikit python=3.11 -y      # one-time
conda activate unikit
conda install -y pip                        # one-time, this env ships without pip
python -m pip install -r server/requirements.txt
```

(If you're on a machine where `python3 -m venv` works normally, a
plain venv with Python 3.10+ works too — the code uses `X | None` type
hints, which need 3.10+.)

## Run the server

```bash
source ~/miniconda3/etc/profile.d/conda.sh && conda activate unikit
cd server
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

- API root: `http://localhost:8000/`
- OpenAPI docs: `http://localhost:8000/docs`
- Developer monitor: `http://localhost:8000/monitor/`

The dev SQLite DB is created at `server/unikit_dev.db` on first run
(gitignored-worthy; delete it to reset state).

## Open the developer monitor

With the server running, open `http://localhost:8000/monitor/` in a
browser. It's a plain HTML/JS page (not the Phone or Glass app) that:

- lets you create a patient and start/end an exam,
- shows live vitals, GlassState, and per-device connection status,
- draws the synthetic PPG/ECG waveforms,
- has buttons for every `/ws/control` command
  (`OTOSCOPE LEFT/RIGHT`, `COLPOSCOPE`, `CAPTURE`, `START/STOP REC`,
  `FREEZE`/`UNFREEZE`).

## Run tests

```bash
source ~/miniconda3/etc/profile.d/conda.sh && conda activate unikit
cd /path/to/unikit
python -m pytest tests/ -v
```

Tests use an isolated SQLite file (`tests/test_unikit.db`, created and
torn down automatically) via the `UNIKIT_DATABASE_URL` env var — they never
touch `server/unikit_dev.db`.

## Quick manual smoke test

```bash
curl -X POST localhost:8000/patients -H "Content-Type: application/json" \
  -d '{"patientId":"P001","displayName":"KJH","age":32,"sex":"F"}'

curl -X POST localhost:8000/exams -H "Content-Type: application/json" \
  -d '{"patientId":"P001"}'

curl localhost:8000/glass/state
curl localhost:8000/devices
```
