from services.open_food_facts import OpenFoodFactsClient


def _complete_product(**overrides) -> dict:
    base = {
        "code": "5449000000996",
        "product_name": "Coca-Cola",
        "product_name_ro": "Coca-Cola",
        "brands": "Coca-Cola",
        "image_small_url": "https://images.openfoodfacts.org/foo.jpg",
        "categories_tags": ["en:beverages", "en:carbonated-drinks", "ro:racoritoare", "en:colas"],
        "nutriments": {
            "energy-kcal_100g": 42.0,
            "proteins_100g": 0.0,
            "carbohydrates_100g": 10.6,
            "fat_100g": 0.0,
        },
    }
    base.update(overrides)
    return base


def test_normalize_returns_dto_for_complete_product():
    client = OpenFoodFactsClient()
    dto = client._normalize(_complete_product())
    assert dto is not None
    assert dto.barcode == "5449000000996"
    assert dto.name == "Coca-Cola"
    assert dto.brand == "Coca-Cola"
    assert dto.image_url == "https://images.openfoodfacts.org/foo.jpg"
    assert dto.kcal_100g == 42.0
    assert dto.protein_100g == 0.0
    assert dto.carbs_100g == 10.6
    assert dto.fat_100g == 0.0
    assert dto.categories == ["beverages", "carbonated-drinks", "racoritoare"]


def test_normalize_prefers_romanian_name():
    product = _complete_product(product_name="Cola", product_name_ro="Cola RO")
    dto = OpenFoodFactsClient()._normalize(product)
    assert dto.name == "Cola RO"


def test_normalize_falls_back_to_english_name_when_ro_missing():
    product = _complete_product(product_name_ro=None)
    dto = OpenFoodFactsClient()._normalize(product)
    assert dto.name == "Coca-Cola"


def test_normalize_falls_back_to_barcode_when_no_name():
    product = _complete_product(product_name=None, product_name_ro=None)
    dto = OpenFoodFactsClient()._normalize(product)
    assert dto.name == "Product 5449000000996"


def test_normalize_returns_none_when_missing_kcal():
    product = _complete_product()
    del product["nutriments"]["energy-kcal_100g"]
    assert OpenFoodFactsClient()._normalize(product) is None


def test_normalize_returns_none_when_missing_protein():
    product = _complete_product()
    del product["nutriments"]["proteins_100g"]
    assert OpenFoodFactsClient()._normalize(product) is None


def test_normalize_returns_none_when_nutriments_empty():
    product = _complete_product(nutriments={})
    assert OpenFoodFactsClient()._normalize(product) is None


def test_normalize_returns_none_when_no_barcode():
    product = _complete_product(code=None)
    assert OpenFoodFactsClient()._normalize(product) is None


import httpx
import pytest

from services.open_food_facts import OpenFoodFactsClient, USER_AGENT


def _make_mock_http(handler) -> httpx.AsyncClient:
    transport = httpx.MockTransport(handler)
    return httpx.AsyncClient(transport=transport, headers={"User-Agent": USER_AGENT})


@pytest.mark.asyncio
async def test_search_filters_out_incomplete_products():
    def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path == "/cgi/search.pl"
        assert request.url.params["search_terms"] == "cola"
        assert request.url.params["lc"] == "ro"
        return httpx.Response(200, json={
            "products": [
                _complete_product(code="111", product_name="Complete"),
                _complete_product(code="222", nutriments={}),  # incomplete
            ],
            "count": 2,
        })

    client = OpenFoodFactsClient(http_client=_make_mock_http(handler))
    items, _ = await client.search("cola", page_size=20)
    assert len(items) == 1
    assert items[0].barcode == "111"


@pytest.mark.asyncio
async def test_search_sends_user_agent_header():
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["ua"] = request.headers.get("user-agent")
        return httpx.Response(200, json={"products": [], "count": 0})

    client = OpenFoodFactsClient(http_client=_make_mock_http(handler))
    await client.search("x", page_size=5)
    assert captured["ua"] == USER_AGENT


@pytest.mark.asyncio
async def test_search_forwards_page_and_returns_total():
    def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.params["page"] == "2"
        return httpx.Response(200, json={
            "products": [_complete_product(code="111", product_name="Complete")],
            "count": 87,
        })

    client = OpenFoodFactsClient(http_client=_make_mock_http(handler))
    items, total = await client.search("cola", page_size=20, page=2)
    assert items[0].barcode == "111"
    assert total == 87


@pytest.mark.asyncio
async def test_get_by_barcode_returns_dto():
    def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path == "/api/v2/product/5449000000996"
        return httpx.Response(200, json={
            "status": 1,
            "code": "5449000000996",
            "product": _complete_product(code="5449000000996"),
        })

    client = OpenFoodFactsClient(http_client=_make_mock_http(handler))
    dto = await client.get_by_barcode("5449000000996")
    assert dto is not None
    assert dto.barcode == "5449000000996"


@pytest.mark.asyncio
async def test_get_by_barcode_returns_none_when_not_found():
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json={"status": 0, "status_verbose": "product not found"})

    client = OpenFoodFactsClient(http_client=_make_mock_http(handler))
    dto = await client.get_by_barcode("000")
    assert dto is None


@pytest.mark.asyncio
async def test_get_by_barcode_returns_none_when_nutrition_incomplete():
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json={
            "status": 1,
            "code": "111",
            "product": _complete_product(code="111", nutriments={}),
        })

    client = OpenFoodFactsClient(http_client=_make_mock_http(handler))
    assert await client.get_by_barcode("111") is None
