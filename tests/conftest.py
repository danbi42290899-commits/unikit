import os
import sys
from pathlib import Path

TESTS_DIR = Path(__file__).resolve().parent
SERVER_DIR = TESTS_DIR.parent / "server"
sys.path.insert(0, str(SERVER_DIR))

TEST_DB_PATH = TESTS_DIR / "test_unikit.db"
if TEST_DB_PATH.exists():
    TEST_DB_PATH.unlink()
os.environ["UNIKIT_DATABASE_URL"] = f"sqlite:///{TEST_DB_PATH}"

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


@pytest.fixture()
def client():
    with TestClient(app) as c:
        yield c
