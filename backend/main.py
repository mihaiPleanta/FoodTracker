from fastapi import FastAPI
from pydantic import BaseModel

# Crearea de server API
app = FastAPI()

# Model de date pentru aliment
class Food(BaseModel):
    name: str
    calories: int

# Lista pentru stocarea alimentelor
foods = []

# Endpoint GET pentru a obtine toate alimentele
@app.get("/foods")
def get_foods():
    return foods

# Endpoint POST pentru a adauga un aliment
@app.post("/foods")
def add_food(food: Food):
    foods.append(food.dict())
    return {"message": "Food added successfully!"}
