from schemas import RecipeDto


def test_recipe_json_schema_has_expected_fields():
    schema = RecipeDto.model_json_schema()
    props = schema["properties"]
    for field in (
        "title", "description", "ingredients", "steps",
        "servings", "kcal_per_serving", "protein_g", "carbs_g", "fat_g",
    ):
        assert field in props
