"""create saved_recipes

Revision ID: b3c1f2a45d67
Revises: 7ad6c5014c61
Create Date: 2026-06-02 12:00:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


revision: str = "b3c1f2a45d67"
down_revision: Union[str, None] = "7ad6c5014c61"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        "saved_recipes",
        sa.Column("id", sa.Integer(), autoincrement=True, nullable=False),
        sa.Column("uid", sa.String(), nullable=False),
        sa.Column("meal_type", sa.String(), nullable=False),
        sa.Column("title", sa.String(), nullable=False),
        sa.Column("recipe", sa.JSON(), nullable=False),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
        sa.ForeignKeyConstraint(["uid"], ["profiles.uid"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index("idx_saved_recipes_uid", "saved_recipes", ["uid"])


def downgrade() -> None:
    op.drop_index("idx_saved_recipes_uid", table_name="saved_recipes")
    op.drop_table("saved_recipes")
