import pytest

from main import app
from schemas import FoodItemDto
from services.open_food_facts import get_off_client

AUTH_HEADER = {"Authorization": "Bearer fake-test-token"}


def _dto(**overrides) -> FoodItemDto:
    base = dict(
        barcode="111",
        name="Lapte",
        brand="Lidl",
        image_url="https://images.openfoodfacts.org/foo.jpg",
        kcal_100g=42.0,
        protein_100g=3.0,
        carbs_100g=4.7,
        fat_100g=1.5,
        categories=["dairies", "milks"],
    )
    base.update(overrides)
    return FoodItemDto(**base)


class _FakeClient:
    def __init__(self):
        self.search_calls: list[tuple[str, int]] = []
        self.barcode_calls: list[str] = []
        self.search_result: list[FoodItemDto] = []
        self.barcode_result: FoodItemDto | None = None

    async def search(self, query: str, page_size: int):
        self.search_calls.append((query, page_size))
        return self.search_result

    async def get_by_barcode(self, barcode: str):
        self.barcode_calls.append(barcode)
        return self.barcode_result


@pytest.fixture
def fake_off():
    fake = _FakeClient()
    app.dependency_overrides[get_off_client] = lambda: fake
    yield fake
    app.dependency_overrides.pop(get_off_client, None)


def test_search_returns_filtered_results(client, fake_off):
    fake_off.search_result = [_dto(barcode="111", name="Lapte")]
    response = client.get("/foods/search", params={"q": "lapte"}, headers=AUTH_HEADER)
    assert response.status_code == 200
    body = response.json()
    assert body["count"] == 1
    assert body["items"][0]["name"] == "Lapte"
    assert fake_off.search_calls == [("lapte", 20)]


def test_search_rejects_short_query(client, fake_off):
    response = client.get("/foods/search", params={"q": "a"}, headers=AUTH_HEADER)
    assert response.status_code == 422


def test_search_rejects_long_query(client, fake_off):
    response = client.get("/foods/search", params={"q": "x" * 51}, headers=AUTH_HEADER)
    assert response.status_code == 422


def test_search_rejects_oversized_page_size(client, fake_off):
    response = client.get("/foods/search", params={"q": "lapte", "page_size": 999}, headers=AUTH_HEADER)
    assert response.status_code == 422


def test_barcode_lookup_success(client, fake_off):
    fake_off.barcode_result = _dto(barcode="5449000000996", name="Coca-Cola")
    response = client.get("/foods/barcode/5449000000996", headers=AUTH_HEADER)
    assert response.status_code == 200
    assert response.json()["name"] == "Coca-Cola"
    assert fake_off.barcode_calls == ["5449000000996"]


def test_barcode_lookup_not_found(client, fake_off):
    fake_off.barcode_result = None
    response = client.get("/foods/barcode/0000000000000", headers=AUTH_HEADER)
    assert response.status_code == 404
    assert "not found" in response.json()["detail"].lower()


def test_barcode_lookup_validates_format(client, fake_off):
    response = client.get("/foods/barcode/abc", headers=AUTH_HEADER)
    assert response.status_code == 422
    response = client.get("/foods/barcode/1234567", headers=AUTH_HEADER)  # too short
    assert response.status_code == 422


def test_search_requires_auth(client, fake_off):
    response = client.get("/foods/search", params={"q": "lapte"})
    assert response.status_code == 422  # missing Authorization header


def test_barcode_requires_auth(client, fake_off):
    response = client.get("/foods/barcode/5449000000996")
    assert response.status_code == 422
