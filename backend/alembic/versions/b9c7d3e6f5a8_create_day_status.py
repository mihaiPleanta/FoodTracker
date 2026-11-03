"""create day_status

Revision ID: b9c7d3e6f5a8
Revises: a8b6c2d5e4f7
Create Date: 2026-11-03 12:00:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


revision: str = "b9c7d3e6f5a8"
down_revision: Union[str, None] = "a8b6c2d5e4f7"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        "day_status",
        sa.Column("uid", sa.String(), nullable=False),
        sa.Column("log_date", sa.Date(), nullable=False),
        sa.Column("complete", sa.Boolean(), nullable=False),
        sa.Column("calorie_goal", sa.Integer(), nullable=True),
        sa.Column("protein_goal_g", sa.Integer(), nullable=True),
        sa.Column("carbs_goal_g", sa.Integer(), nullable=True),
        sa.Column("fat_goal_g", sa.Integer(), nullable=True),
        sa.Column("tdee", sa.Integer(), nullable=True),
        sa.Column(
            "updated_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
        sa.ForeignKeyConstraint(["uid"], ["profiles.uid"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("uid", "log_date"),
    )


def downgrade() -> None:
    op.drop_table("day_status")
