import pytest

from main import app
from schemas import FoodItemDto
from services.generic_foods import get_generic_client
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
        self.search_calls: list[tuple[str, int, int]] = []
        self.barcode_calls: list[str] = []
        self.search_result: list[FoodItemDto] = []
        self.search_total: int | None = None
        self.barcode_result: FoodItemDto | None = None

    async def search(self, query: str, page_size: int, page: int = 1):
        self.search_calls.append((query, page_size, page))
        total = self.search_total if self.search_total is not None else len(self.search_result)
        return self.search_result, total

    async def get_by_barcode(self, barcode: str):
        self.barcode_calls.append(barcode)
        return self.barcode_result


class _FakeGenericClient:
    def __init__(self):
        self.search_calls: list[tuple[str, int]] = []
        self.search_result: list[FoodItemDto] = []

    def search(self, query: str, limit: int):
        self.search_calls.append((query, limit))
        return self.search_result


@pytest.fixture
def fake_generic():
    fake = _FakeGenericClient()
    app.dependency_overrides[get_generic_client] = lambda: fake
    yield fake
    app.dependency_overrides.pop(get_generic_client, None)


@pytest.fixture
def fake_off(fake_generic):  # depends on fake_generic so generics default to empty
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
    assert fake_off.search_calls == [("lapte", 20, 1)]


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


def test_search_translates_off_rate_limit_to_503(client, fake_off):
    import httpx

    async def raise_429(*args, **kwargs):
        request = httpx.Request("GET", "https://world.openfoodfacts.org/")
        response = httpx.Response(429, request=request)
        raise httpx.HTTPStatusError("rate limited", request=request, response=response)

    fake_off.search = raise_429
    response = client.get("/foods/search", params={"q": "lapte"}, headers=AUTH_HEADER)
    assert response.status_code == 503
    assert "busy" in response.json()["detail"].lower()


def test_barcode_translates_off_rate_limit_to_503(client, fake_off):
    import httpx

    async def raise_429(*args, **kwargs):
        request = httpx.Request("GET", "https://world.openfoodfacts.org/")
        response = httpx.Response(429, request=request)
        raise httpx.HTTPStatusError("rate limited", request=request, response=response)

    fake_off.get_by_barcode = raise_429
    response = client.get("/foods/barcode/5449000000996", headers=AUTH_HEADER)
    assert response.status_code == 503


def test_generics_rank_before_off(client, fake_off, fake_generic):
    fake_generic.search_result = [_dto(barcode="usda-chicken-breast-raw", name="Piept de pui crud", brand=None, image_url=None)]
    fake_off.search_result = [_dto(barcode="222", name="Piept de pui afumat")]
    response = client.get("/foods/search", params={"q": "piept de pui"}, headers=AUTH_HEADER)
    assert response.status_code == 200
    body = response.json()
    assert body["count"] == 2
    assert body["items"][0]["name"] == "Piept de pui crud"
    assert body["items"][1]["name"] == "Piept de pui afumat"
    assert fake_generic.search_calls == [("piept de pui", 20)]


def test_off_failure_returns_generics_only(client, fake_off, fake_generic):
    import httpx

    fake_generic.search_result = [_dto(barcode="usda-chicken-breast-raw", name="Piept de pui crud")]

    async def raise_503(*args, **kwargs):
        request = httpx.Request("GET", "https://world.openfoodfacts.org/")
        response = httpx.Response(503, request=request)
        raise httpx.HTTPStatusError("down", request=request, response=response)

    fake_off.search = raise_503
    response = client.get("/foods/search", params={"q": "piept de pui"}, headers=AUTH_HEADER)
    assert response.status_code == 200
    body = response.json()
    assert body["count"] == 1
    assert body["items"][0]["name"] == "Piept de pui crud"


def test_off_failure_without_generics_still_503(client, fake_off, fake_generic):
    import httpx

    fake_generic.search_result = []

    async def raise_503(*args, **kwargs):
        request = httpx.Request("GET", "https://world.openfoodfacts.org/")
        response = httpx.Response(503, request=request)
        raise httpx.HTTPStatusError("down", request=request, response=response)

    fake_off.search = raise_503
    response = client.get("/foods/search", params={"q": "xyzzy"}, headers=AUTH_HEADER)
    assert response.status_code == 503


def test_off_network_error_without_generics_still_503(client, fake_off, fake_generic):
    import httpx

    fake_generic.search_result = []

    async def raise_network(*args, **kwargs):
        raise httpx.ConnectError("no route")

    fake_off.search = raise_network
    response = client.get("/foods/search", params={"q": "xyzzy"}, headers=AUTH_HEADER)
    assert response.status_code == 503


def test_off_duplicate_of_generic_is_removed(client, fake_off, fake_generic):
    fake_generic.search_result = [_dto(barcode="usda-milk-1-5", name="Lapte")]
    fake_off.search_result = [_dto(barcode="111", name="Lapte"), _dto(barcode="222", name="Iaurt")]
    response = client.get("/foods/search", params={"q": "lapte"}, headers=AUTH_HEADER)
    body = response.json()
    assert [it["name"] for it in body["items"]] == ["Lapte", "Iaurt"]
    assert body["count"] == 2


def test_off_network_error_with_generics_returns_generics_only(client, fake_off, fake_generic):
    import httpx

    fake_generic.search_result = [_dto(barcode="usda-chicken-breast-raw", name="Piept de pui crud")]

    async def raise_network(*args, **kwargs):
        raise httpx.ConnectError("no route")

    fake_off.search = raise_network
    response = client.get("/foods/search", params={"q": "piept de pui"}, headers=AUTH_HEADER)
    assert response.status_code == 200
    body = response.json()
    assert body["count"] == 1
    assert body["items"][0]["name"] == "Piept de pui crud"


def test_page_one_returns_generics_plus_full_off_page(client, fake_off, fake_generic):
    fake_generic.search_result = [
        _dto(barcode="usda-a", name="Gen A"),
        _dto(barcode="usda-b", name="Gen B"),
    ]
    fake_off.search_result = [_dto(barcode=str(i), name=f"OFF {i}") for i in range(20)]
    response = client.get("/foods/search", params={"q": "xx", "page_size": 20}, headers=AUTH_HEADER)
    body = response.json()
    # Fără trim: 2 generice + 20 OFF = 22, genericele rămân primele.
    assert body["count"] == 22
    assert body["items"][0]["name"] == "Gen A"
    assert body["items"][1]["name"] == "Gen B"


def test_page_two_returns_off_only(client, fake_off, fake_generic):
    fake_generic.search_result = [_dto(barcode="usda-x", name="Gen X")]
    fake_off.search_result = [_dto(barcode="222", name="OFF 222")]
    response = client.get("/foods/search", params={"q": "xx", "page": 2}, headers=AUTH_HEADER)
    body = response.json()
    # Pe pagina 2 genericele NU se includ; page e forwardat la OFF.
    assert [it["name"] for it in body["items"]] == ["OFF 222"]
    assert fake_off.search_calls == [("xx", 20, 2)]


def test_page_two_dedups_off_name_matching_generic(client, fake_off, fake_generic):
    fake_generic.search_result = [_dto(barcode="usda-milk", name="Lapte")]
    fake_off.search_result = [_dto(barcode="111", name="Lapte"), _dto(barcode="222", name="Iaurt")]
    response = client.get("/foods/search", params={"q": "lapte", "page": 2}, headers=AUTH_HEADER)
    body = response.json()
    # Genericul nu apare pe pagina 2, dar OFF "Lapte" e tot eliminat ca duplicat al unui nume generic.
    assert [it["name"] for it in body["items"]] == ["Iaurt"]


def test_has_more_true_when_off_total_exceeds_page(client, fake_off, fake_generic):
    fake_off.search_result = [_dto(barcode=str(i), name=f"OFF {i}") for i in range(20)]
    fake_off.search_total = 100
    response = client.get("/foods/search", params={"q": "xx", "page_size": 20}, headers=AUTH_HEADER)
    assert response.json()["has_more"] is True


def test_has_more_false_on_last_page(client, fake_off, fake_generic):
    fake_off.search_result = [_dto(barcode="1", name="OFF 1")]
    fake_off.search_total = 1
    response = client.get("/foods/search", params={"q": "xx"}, headers=AUTH_HEADER)
    assert response.json()["has_more"] is False
