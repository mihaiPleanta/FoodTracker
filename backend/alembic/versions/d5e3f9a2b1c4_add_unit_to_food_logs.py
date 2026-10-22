"""add unit and unit_qty to food_logs

Revision ID: d5e3f9a2b1c4
Revises: c4d2e8f1a9b3
Create Date: 2026-10-22 12:00:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


revision: str = "d5e3f9a2b1c4"
down_revision: Union[str, None] = "c4d2e8f1a9b3"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column("food_logs", sa.Column("unit", sa.String(), nullable=True))
    op.add_column("food_logs", sa.Column("unit_qty", sa.Float(), nullable=True))


def downgrade() -> None:
    op.drop_column("food_logs", "unit_qty")
    op.drop_column("food_logs", "unit")
