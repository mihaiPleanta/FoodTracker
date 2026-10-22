"""create user_portion_units and user_gram_presets

Revision ID: e6f4a0b3c2d5
Revises: d5e3f9a2b1c4
Create Date: 2026-10-22 12:00:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


revision: str = "e6f4a0b3c2d5"
down_revision: Union[str, None] = "d5e3f9a2b1c4"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        "user_portion_units",
        sa.Column("id", sa.Integer(), autoincrement=True, nullable=False),
        sa.Column("uid", sa.String(), nullable=False),
        sa.Column("barcode", sa.String(), nullable=False),
        sa.Column("unit", sa.String(), nullable=False),
        sa.Column("unit_key", sa.String(), nullable=False),
        sa.Column("grams", sa.Float(), nullable=False),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
        sa.ForeignKeyConstraint(["uid"], ["profiles.uid"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint(
            "uid", "barcode", "unit_key", name="uq_user_portion_units_uid_barcode_key"
        ),
        sa.CheckConstraint("grams > 0", name="ck_user_portion_units_grams_pos"),
    )
    op.create_table(
        "user_gram_presets",
        sa.Column("uid", sa.String(), nullable=False),
        sa.Column("barcode", sa.String(), nullable=False),
        sa.Column("presets", sa.JSON(), nullable=False),
        sa.Column(
            "updated_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
        sa.ForeignKeyConstraint(["uid"], ["profiles.uid"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("uid", "barcode"),
    )


def downgrade() -> None:
    op.drop_table("user_gram_presets")
    op.drop_table("user_portion_units")
