from fastapi import APIRouter, Depends, HTTPException, status
from auth import verify_token
from models import ProfileRequest, ProfileResponse

router = APIRouter()

# In-memory store: uid → profile dict
# TODO: Replace with database when DB is configured
profiles: dict[str, dict] = {}


@router.get("/profile", response_model=ProfileResponse)
async def get_profile(token: dict = Depends(verify_token)) -> ProfileResponse:
    uid = token["uid"]
    if uid not in profiles:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Profile not found")
    return ProfileResponse(**profiles[uid])


@router.post("/profile", response_model=ProfileResponse)
async def save_profile(
    req: ProfileRequest,
    token: dict = Depends(verify_token),
) -> ProfileResponse:
    uid = token["uid"]
    profiles[uid] = {**req.model_dump(), "uid": uid}
    return ProfileResponse(**profiles[uid])
