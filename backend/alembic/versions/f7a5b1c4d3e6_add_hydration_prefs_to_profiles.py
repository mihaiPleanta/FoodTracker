"""add hydration prefs to profiles

Revision ID: f7a5b1c4d3e6
Revises: e6f4a0b3c2d5
Create Date: 2026-10-25 12:00:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


revision: str = "f7a5b1c4d3e6"
down_revision: Union[str, None] = "e6f4a0b3c2d5"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column(
        "profiles",
        sa.Column(
            "takes_creatine",
            sa.Boolean(),
            server_default=sa.false(),
            nullable=False,
        ),
    )
    op.add_column(
        "profiles",
        sa.Column("hydration_goal_override_l", sa.Float(), nullable=True),
    )


def downgrade() -> None:
    op.drop_column("profiles", "hydration_goal_override_l")
    op.drop_column("profiles", "takes_creatine")
