# FoodTracker

A full-stack Android app for food and nutrition tracking: search a database of ~4.6M
products, log meals, track hydration and weight, get a personalized calorie goal, and
generate AI recipes from your own eating habits — all running against a self-hosted API.

Built with Kotlin + Jetpack Compose on the client and FastAPI + PostgreSQL on the server,
with Firebase authentication and a locally-hosted LLM for recipe generation.

## Demo

A screen recording of the app in action is in
[`demo-aplicatie/demo-aplicatie-comprimat.mp4`](demo-aplicatie/demo-aplicatie-comprimat.mp4).

## Features

- **Food search & barcode scan** — live search over [Open Food Facts](https://world.openfoodfacts.org)
  (~4.6M products) proxied through the backend, plus a curated set of generic foods with
  Romanian names and USDA macros. Barcode scanning via ML Kit (no camera permission needed).
- **Meal logging** — log foods per meal (breakfast / lunch / dinner / snacks) per day, with
  automatic calorie & macro totals.
- **Personalized calorie goal** — TDEE computed from the Mifflin–St Jeor formula based on the
  user's profile (age, sex, height, weight, activity level, goal).
- **Hydration & weight tracking** — daily water intake and a weight-trend history.
- **AI recipe generator** — picks a "star ingredient" from your most-logged foods and asks a
  locally-hosted LLM (Ollama, `gemma3:4b`) to invent a concrete recipe (title, ingredients,
  steps, macros) that fits your remaining budget for the chosen meal. Runs fully offline —
  no API keys, no user data leaving the machine.
- **Auth** — Firebase email/password (with email verification + password reset) and Google
  Sign-In. Every backend request is authenticated with a Firebase ID token.
- **Offline-aware caching** — a Room cache sits in front of the food API (cache-first, 24h TTL,
  stale fallback when offline).
- **Bilingual UI (RO/EN)** and responsive layout (phone + tablet).

## Tech stack

**Android** — Kotlin 2.0.21 · Jetpack Compose (Material 3) · Navigation Compose · Retrofit +
OkHttp · Coroutines/Flow · Room · DataStore · Coil · ML Kit barcode scanner · Firebase Auth ·
minSdk 24 / targetSdk 36.

**Backend** — Python · FastAPI · SQLAlchemy 2.0 (async) + Alembic · PostgreSQL 16 · httpx
(async Open Food Facts client) · Firebase Admin (token verification) · Ollama (local LLM) ·
pytest.

## Architecture

```
Android app  ──Firebase ID token──▶  FastAPI backend  ──▶  PostgreSQL   (user profiles & logs)
   │                                       │
   │                                       ├──▶  Open Food Facts API   (live product data)
   │                                       └──▶  Ollama (local LLM)     (recipe generation)
   └── Firebase Auth (email/password + Google Sign-In)
```

- The backend **never** stores a food catalog — PostgreSQL holds only user data. Food data is
  live from Open Food Facts plus an in-memory set of hand-written generic foods.
- Auth is enforced on every endpoint: the client attaches the Firebase ID token via an OkHttp
  interceptor, and the backend verifies it with the Firebase Admin SDK.

## Repository layout

```
frontend/   Android app (Kotlin + Jetpack Compose).
backend/    FastAPI server (routers, services, Alembic migrations, pytest suite).
docs/       Feature write-ups.
```

## Build & run

**Backend** (from `backend/`):

```bash
# Requires PostgreSQL 16 (database `foodtracker`) and, for AI recipes, Ollama with gemma3:4b.
python3 -m uvicorn main:app --reload --port 8000     # add --host 0.0.0.0 for a physical device
TESTING=1 python3 -m pytest tests/ -v                # tests: SQLite in-memory, mocked Firebase & OFF
alembic upgrade head
```

**Frontend** (from `frontend/`):

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The emulator reaches the backend at `http://10.0.2.2:8000/`. For a physical device, set
`BASE_URL` in `api/RetrofitIstance.kt` to the host machine's LAN IP and run the backend with
`--host 0.0.0.0`.

### Required secrets (not committed)

Both are obtained from the Firebase Console and are gitignored:

- `frontend/app/google-services.json` — Firebase Android config.
- `backend/serviceAccountKey.json` — Firebase Admin service account.

## Data attribution

Product data is provided by **[Open Food Facts](https://world.openfoodfacts.org)** and is made
available under the **[Open Database License (ODbL)](https://opendatacommons.org/licenses/odbl/1-0/)**.

## License

Released under the [MIT License](LICENSE).
