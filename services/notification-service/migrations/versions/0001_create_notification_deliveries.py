"""Create notification delivery log."""

from alembic import op
import sqlalchemy as sa

revision = "0001_notification_deliveries"
down_revision = None
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "notification_deliveries",
        sa.Column("event_id", sa.String(length=255), primary_key=True),
        sa.Column("order_id", sa.String(length=36), nullable=False),
        sa.Column("event_type", sa.String(length=64), nullable=False),
        sa.Column("recipient_email", sa.String(length=254), nullable=True),
        sa.Column("subject", sa.String(length=255), nullable=False),
        sa.Column("status", sa.String(length=32), nullable=False),
        sa.Column("attempts", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("last_error", sa.String(length=1000), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("sent_at", sa.DateTime(timezone=True), nullable=True),
    )
    op.create_index("notification_order_created_idx", "notification_deliveries", ["order_id", "created_at"])


def downgrade() -> None:
    op.drop_index("notification_order_created_idx", table_name="notification_deliveries")
    op.drop_table("notification_deliveries")
