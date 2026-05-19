import os
import firebase_admin
from firebase_admin import credentials
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from routers.tdee import router as tdee_router
from routers.profile import router as profile_router
from routers.foods import router as foods_router

if os.getenv("TESTING") != "1":
    _cred_path = os.getenv("FIREBASE_SERVICE_ACCOUNT", "serviceAccountKey.json")
    firebase_admin.initialize_app(credentials.Certificate(_cred_path))

app = FastAPI()
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])
app.include_router(tdee_router)
app.include_router(profile_router)
app.include_router(foods_router)
