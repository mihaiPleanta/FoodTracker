from fastapi import APIRouter, Depends, HTTPException, Response, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from auth import verify_token
from database import get_db
from models import SavedRecipe
from schemas import (
    RecipeDto,
    RecipeGenerateRequest,
    SavedRecipeCreate,
    SavedRecipeDto,
)
from services.ollama_client import OllamaUnavailable, get_ollama_client
from services.recipes import (
    InsufficientData,
    RecipeParseError,
    generate_recipe,
)

router = APIRouter()


@router.post("/recipes/generate", response_model=RecipeDto)
async def generate(
    req: RecipeGenerateRequest,
    token: dict = Depends(verify_token),
    db: AsyncSession = Depends(get_db),
    ollama=Depends(get_ollama_client),
) -> RecipeDto:
    uid = token["uid"]
    try:
        return await generate_recipe(db, uid, req.meal_type, ollama, req.language)
    except InsufficientData:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="Loghează mai multe alimente ca să generăm rețete",
        )
    except OllamaUnavailable:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Serviciul AI nu e disponibil, încearcă din nou",
        )
    except RecipeParseError:
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail="Răspuns AI invalid, încearcă din nou",
        )


@router.post("/recipes", response_model=SavedRecipeDto, status_code=status.HTTP_201_CREATED)
async def save_recipe(
    req: SavedRecipeCreate,
    token: dict = Depends(verify_token),
    db: AsyncSession = Depends(get_db),
) -> SavedRecipe:
    uid = token["uid"]
    row = SavedRecipe(
        uid=uid,
        meal_type=req.meal_type,
        title=req.recipe.title,
        recipe=req.recipe.model_dump(),
    )
    db.add(row)
    await db.commit()
    await db.refresh(row)
    return row


@router.get("/recipes", response_model=list[SavedRecipeDto])
async def list_recipes(
    token: dict = Depends(verify_token),
    db: AsyncSession = Depends(get_db),
) -> list[SavedRecipe]:
    uid = token["uid"]
    result = await db.execute(
        select(SavedRecipe)
        .where(SavedRecipe.uid == uid)
        .order_by(SavedRecipe.created_at.desc(), SavedRecipe.id.desc())
    )
    return list(result.scalars().all())


@router.delete("/recipes/{recipe_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_recipe(
    recipe_id: int,
    token: dict = Depends(verify_token),
    db: AsyncSession = Depends(get_db),
) -> Response:
    uid = token["uid"]
    result = await db.execute(select(SavedRecipe).where(SavedRecipe.id == recipe_id))
    row = result.scalar_one_or_none()
    if row is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Recipe not found")
    if row.uid != uid:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Not your recipe")
    await db.delete(row)
    await db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)
