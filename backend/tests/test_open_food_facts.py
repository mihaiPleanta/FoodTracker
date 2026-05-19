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
