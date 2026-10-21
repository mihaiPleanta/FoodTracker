"""create favorite_foods

Revision ID: c4d2e8f1a9b3
Revises: 8f3bd6e52f69
Create Date: 2026-10-21 12:00:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


revision: str = "c4d2e8f1a9b3"
down_revision: Union[str, None] = "8f3bd6e52f69"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        "favorite_foods",
        sa.Column("id", sa.Integer(), autoincrement=True, nullable=False),
        sa.Column("uid", sa.String(), nullable=False),
        sa.Column("barcode", sa.String(), nullable=False),
        sa.Column("name", sa.String(), nullable=False),
        sa.Column("brand", sa.String(), nullable=True),
        sa.Column("image_url", sa.String(), nullable=True),
        sa.Column("categories", sa.JSON(), nullable=False),
        sa.Column("kcal_100g", sa.Float(), nullable=False),
        sa.Column("protein_100g", sa.Float(), nullable=False),
        sa.Column("carbs_100g", sa.Float(), nullable=False),
        sa.Column("fat_100g", sa.Float(), nullable=False),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
        sa.ForeignKeyConstraint(["uid"], ["profiles.uid"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("uid", "barcode", name="uq_favorite_foods_uid_barcode"),
        sa.CheckConstraint("kcal_100g >= 0", name="ck_favorite_foods_kcal_nonneg"),
        sa.CheckConstraint("protein_100g >= 0", name="ck_favorite_foods_protein_nonneg"),
        sa.CheckConstraint("carbs_100g >= 0", name="ck_favorite_foods_carbs_nonneg"),
        sa.CheckConstraint("fat_100g >= 0", name="ck_favorite_foods_fat_nonneg"),
    )


def downgrade() -> None:
    op.drop_table("favorite_foods")
