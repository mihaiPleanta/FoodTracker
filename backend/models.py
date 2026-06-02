from datetime import date, datetime
from typing import Optional

from sqlalchemy import (
    JSON,
    CheckConstraint,
    DateTime,
    Date,
    Float,
    ForeignKey,
    Index,
    Integer,
    String,
    func,
)
from sqlalchemy.orm import Mapped, mapped_column

from database import Base


class Profile(Base):
    __tablename__ = "profiles"

    uid: Mapped[str] = mapped_column(String, primary_key=True)
    name: Mapped[str] = mapped_column(String, nullable=False)
    age: Mapped[int] = mapped_column(Integer, nullable=False)
    gender: Mapped[str] = mapped_column(String, nullable=False)
    height_cm: Mapped[int] = mapped_column(Integer, nullable=False)
    current_weight_kg: Mapped[float] = mapped_column(Float, nullable=False)
    target_weight_kg: Mapped[float] = mapped_column(Float, nullable=False)
    activity_level: Mapped[str] = mapped_column(String, nullable=False)


class FoodLog(Base):
    __tablename__ = "food_logs"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    uid: Mapped[str] = mapped_column(
        String, ForeignKey("profiles.uid", ondelete="CASCADE"), nullable=False
    )
    log_date: Mapped[date] = mapped_column(Date, nullable=False)
    meal: Mapped[str] = mapped_column(String, nullable=False)
    grams: Mapped[int] = mapped_column(Integer, nullable=False)

    barcode: Mapped[str] = mapped_column(String, nullable=False)
    name: Mapped[str] = mapped_column(String, nullable=False)
    brand: Mapped[Optional[str]] = mapped_column(String, nullable=True)
    image_url: Mapped[Optional[str]] = mapped_column(String, nullable=True)
    categories: Mapped[list] = mapped_column(JSON, nullable=False, default=list)
    kcal_100g: Mapped[float] = mapped_column(Float, nullable=False)
    protein_100g: Mapped[float] = mapped_column(Float, nullable=False)
    carbs_100g: Mapped[float] = mapped_column(Float, nullable=False)
    fat_100g: Mapped[float] = mapped_column(Float, nullable=False)

    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )

    __table_args__ = (
        CheckConstraint("grams > 0", name="ck_food_logs_grams_positive"),
        CheckConstraint("kcal_100g >= 0", name="ck_food_logs_kcal_nonneg"),
        CheckConstraint("protein_100g >= 0", name="ck_food_logs_protein_nonneg"),
        CheckConstraint("carbs_100g >= 0", name="ck_food_logs_carbs_nonneg"),
        CheckConstraint("fat_100g >= 0", name="ck_food_logs_fat_nonneg"),
        Index("idx_food_logs_uid_date", "uid", "log_date"),
    )


class HydrationLog(Base):
    __tablename__ = "hydration_logs"

    uid: Mapped[str] = mapped_column(
        String, ForeignKey("profiles.uid", ondelete="CASCADE"), primary_key=True
    )
    log_date: Mapped[date] = mapped_column(Date, primary_key=True)
    liters: Mapped[float] = mapped_column(Float, nullable=False)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )

    __table_args__ = (
        CheckConstraint("liters >= 0 AND liters <= 10", name="ck_hydration_liters_range"),
    )


class WeightCheckIn(Base):
    __tablename__ = "weight_check_ins"

    uid: Mapped[str] = mapped_column(
        String, ForeignKey("profiles.uid", ondelete="CASCADE"), primary_key=True
    )
    log_date: Mapped[date] = mapped_column(Date, primary_key=True)
    weight_kg: Mapped[float] = mapped_column(Float, nullable=False)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )

    __table_args__ = (
        CheckConstraint("weight_kg > 0 AND weight_kg < 500", name="ck_weight_range"),
        Index("idx_weight_check_ins_uid_date", "uid", "log_date"),
    )


class SavedRecipe(Base):
    __tablename__ = "saved_recipes"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    uid: Mapped[str] = mapped_column(
        String, ForeignKey("profiles.uid", ondelete="CASCADE"), nullable=False
    )
    meal_type: Mapped[str] = mapped_column(String, nullable=False)
    title: Mapped[str] = mapped_column(String, nullable=False)
    recipe: Mapped[dict] = mapped_column(JSON, nullable=False)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )

    __table_args__ = (
        Index("idx_saved_recipes_uid", "uid"),
    )
