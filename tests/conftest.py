import os
import shutil
import sys
from pathlib import Path

TESTS_DIR = Path(__file__).resolve().parent
SERVER_DIR = TESTS_DIR.parent / "server"
sys.path.insert(0, str(SERVER_DIR))

TEST_DB_PATH = TESTS_DIR / "test_unikit.db"
if TEST_DB_PATH.exists():
    TEST_DB_PATH.unlink()
os.environ["UNIKIT_DATABASE_URL"] = f"sqlite:///{TEST_DB_PATH}"

TEST_MEDIA_DIR = TESTS_DIR / "test_unikit_media"
shutil.rmtree(TEST_MEDIA_DIR, ignore_errors=True)
os.environ["UNIKIT_MEDIA_DIR"] = str(TEST_MEDIA_DIR)

import pytest  # noqa: E402
from fastapi.testclient import TestClient  # noqa: E402

from app.db import init_db  # noqa: E402
from app.main import app  # noqa: E402


@pytest.fixture(scope="session", autouse=True)
def _prepare_test_db():
    init_db()
    yield
    if TEST_DB_PATH.exists():
        TEST_DB_PATH.unlink()
    shutil.rmtree(TEST_MEDIA_DIR, ignore_errors=True)


@pytest.fixture()
def client():
    with TestClient(app) as c:
        yield c


@pytest.fixture(autouse=True)
def _reset_camera_relay_last_frame():
    # camera_relay is a process-global singleton; without this, a frame
    # sent by one test (e.g. test_camera_relay.py) would leak into an
    # unrelated test's "no frame yet" assertion (e.g. test_media.py).
    from app.ws.camera_relay import camera_relay

    camera_relay._last_frame = None
    yield
