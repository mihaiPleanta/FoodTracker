import os
os.environ["TESTING"] = "1"   # must be set BEFORE importing main

import pytest
from unittest.mock import patch
from fastapi.testclient import TestClient
from main import app


@pytest.fixture
def client():
    return TestClient(app)


@pytest.fixture(autouse=True)
def mock_firebase_token():
    """Mock Firebase token verification for all tests."""
    with patch("firebase_admin.auth.verify_id_token") as mock:
        mock.return_value = {"uid": "test-uid-123", "email": "test@test.com"}
        yield mock
