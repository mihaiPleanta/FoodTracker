from __future__ import annotations

from schemas import FoodItemDto

USER_AGENT = "FoodTracker-Licenta/0.1 (mihaipleanta@gmail.com)"


class OpenFoodFactsClient:
    """Client async pentru Open Food Facts. HTTP-ul se adaugă în Task 3."""

    def _normalize(self, product: dict) -> FoodItemDto | None:
        barcode = product.get("code")
        if not barcode:
            return None

        nutriments = product.get("nutriments") or {}
        required = ("energy-kcal_100g", "proteins_100g", "carbohydrates_100g", "fat_100g")
        if any(nutriments.get(k) is None for k in required):
            return None

        name = (
            product.get("product_name_ro")
            or product.get("product_name")
            or f"Product {barcode}"
        )

        raw_tags = product.get("categories_tags") or []
        categories: list[str] = []
        for tag in raw_tags:
            if not isinstance(tag, str):
                continue
            stripped = tag.split(":", 1)[1] if ":" in tag else tag
            if stripped not in categories:
                categories.append(stripped)
            if len(categories) == 3:
                break

        brands = product.get("brands")
        brand = brands.split(",")[0].strip() if brands else None

        return FoodItemDto(
            barcode=str(barcode),
            name=name,
            brand=brand or None,
            image_url=product.get("image_small_url") or None,
            kcal_100g=float(nutriments["energy-kcal_100g"]),
            protein_100g=float(nutriments["proteins_100g"]),
            carbs_100g=float(nutriments["carbohydrates_100g"]),
            fat_100g=float(nutriments["fat_100g"]),
            categories=categories,
        )
