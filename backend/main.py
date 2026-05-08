import os
import firebase_admin
from firebase_admin import credentials
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

# Skip Firebase init when running tests (TESTING=1)
if os.getenv("TESTING") != "1":
    _cred_path = os.getenv("FIREBASE_SERVICE_ACCOUNT", "serviceAccountKey.json")
    firebase_admin.initialize_app(credentials.Certificate(_cred_path))

app = FastAPI()

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# ── Existing food endpoints (keep) ────────────────────────────────────────────
foods = [
    {"name": "Chicken breast", "calories": 165},
    {"name": "Rice", "calories": 130},
]

@app.get("/foods")
def get_foods():
    return foods

@app.post("/foods")
def add_food(food: dict):
    foods.append(food)
    return food
