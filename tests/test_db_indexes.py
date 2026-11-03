from __future__ import annotations

from sqlalchemy import create_engine, text

from app.db import _create_missing_indexes
from app.models import Base


def test_missing_index_is_restored_for_existing_table(tmp_path):
    engine = create_engine(f"sqlite:///{(tmp_path / 'test.db').as_posix()}")
    with engine.begin() as connection:
        Base.metadata.create_all(connection)
        # Имитируем базу, созданную до появления индекса.
        connection.execute(text("drop index ix_tracks_source_url"))

        _create_missing_indexes(connection)

        names = [row[1] for row in connection.execute(text("pragma index_list(tracks)"))]
        assert "ix_tracks_source_url" in names

        plan = " ".join(
            row[3]
            for row in connection.execute(
                text(
                    "explain query plan "
                    "select id from tracks where source_url = 'x' and status = 'done'"
                )
            )
        )
        assert "ix_tracks_source_url" in plan
    engine.dispose()
