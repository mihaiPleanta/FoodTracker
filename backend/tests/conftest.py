import os
import tempfile

# Must be set BEFORE importing main so the app picks up the test DB.
os.environ["TESTING"] = "1"
_TEST_DB_PATH = os.path.join(tempfile.gettempdir(), "foodtracker_test.db")
os.environ["DATABASE_URL"] = f"sqlite+aiosqlite:///{_TEST_DB_PATH}"

import pytest
from unittest.mock import patch
from fastapi.testclient import TestClient
from sqlalchemy import create_engine

from main import app
from database import Base
import models  # noqa: F401  # ensure models register with Base.metadata


# Sync engine used only to create/drop tables between tests.
# The app itself still uses the async engine via aiosqlite on the same file.
_sync_engine = create_engine(f"sqlite:///{_TEST_DB_PATH}")


@pytest.fixture(autouse=True)
def setup_db():
    Base.metadata.drop_all(_sync_engine)
    Base.metadata.create_all(_sync_engine)
    yield
    Base.metadata.drop_all(_sync_engine)


@pytest.fixture
def client():
    return TestClient(app)


@pytest.fixture(autouse=True)
def mock_firebase_token():
    """Mock Firebase token verification for all tests."""
    with patch("firebase_admin.auth.verify_id_token") as mock:
        mock.return_value = {"uid": "test-uid-123", "email": "test@test.com"}
        yield mock
