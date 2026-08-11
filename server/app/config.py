"""
Central configuration for the Mock UNI-HUB.

Everything under SIMULATION_* is a development-only convenience value
used to drive the mock signal generator and the developer monitor UI.
None of these values come from real hardware measurement or datasheets.
They MUST NOT be treated as the actual sampling rate / accuracy of any
real UNI-KIT sensor once real hardware is integrated (see
docs/integration_adapter.md).
"""
import os
from pathlib import Path

SERVER_NAME = "MOCK_UNI_HUB"

BASE_DIR = Path(__file__).resolve().parent.parent
DB_PATH = BASE_DIR / "unikit_dev.db"

# UNIKIT_DATABASE_URL lets tests point at an isolated/in-memory database
# without touching the real dev DB file.
DATABASE_URL = os.environ.get("UNIKIT_DATABASE_URL", f"sqlite:///{DB_PATH}")

STATIC_MONITOR_DIR = BASE_DIR / "app" / "static" / "monitor"

# Where CAPTURE-persisted otoscope/colposcope still images are written.
# UNIKIT_MEDIA_DIR lets tests point at an isolated directory, same pattern
# as UNIKIT_DATABASE_URL.
MEDIA_STORAGE_DIR = Path(os.environ.get("UNIKIT_MEDIA_DIR", str(BASE_DIR / "media")))

# --- Mock vitals baseline (explicitly simulated, never real) ---
MOCK_VITALS = {
    "bpSys": 118,
    "bpDia": 76,
    "spo2": 98,
    "heartRate": 72,
    "temperature": 36.7,
}

# --- Simulation-only signal generation settings ---
# These sample rates are arbitrary choices made purely so the developer
# monitor / future phone graphs have something smooth to render during
# UI development. They are NOT the real MAX30102 / ECG front-end sampling
# rate. Real values must come from the existing Windows program or from
# datasheet + measured hardware behavior (see docs/integration_adapter.md).
SIMULATION_PPG_SAMPLE_RATE_HZ = 50
SIMULATION_ECG_SAMPLE_RATE_HZ = 100
SIMULATION_TELEMETRY_INTERVAL_SEC = 1.0
SIMULATION_SIGNAL_CHUNK_SEC = 0.2

# --- Device heartbeat / disconnect timeout ---
DEVICE_STALE_TIMEOUT_SEC = 5.0
