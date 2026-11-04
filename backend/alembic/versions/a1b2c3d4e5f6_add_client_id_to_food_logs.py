"""add client_id to food_logs

Revision ID: a1b2c3d4e5f6
Revises: c1d2e3f4a5b6
Create Date: 2026-11-04 14:00:00.000000

"""
import uuid
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


revision: str = "a1b2c3d4e5f6"
down_revision: Union[str, None] = "c1d2e3f4a5b6"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column("food_logs", sa.Column("client_id", sa.String(36), nullable=True))
    bind = op.get_bind()
    ids = [r[0] for r in bind.execute(sa.text("SELECT id FROM food_logs")).fetchall()]
    for row_id in ids:
        bind.execute(
            sa.text("UPDATE food_logs SET client_id = :c WHERE id = :i"),
            {"c": str(uuid.uuid4()), "i": row_id},
        )
    with op.batch_alter_table("food_logs") as batch:
        batch.alter_column("client_id", existing_type=sa.String(36), nullable=False)
        batch.create_unique_constraint("uq_food_logs_uid_client_id", ["uid", "client_id"])


def downgrade() -> None:
    with op.batch_alter_table("food_logs") as batch:
        batch.drop_constraint("uq_food_logs_uid_client_id", type_="unique")
        batch.drop_column("client_id")
