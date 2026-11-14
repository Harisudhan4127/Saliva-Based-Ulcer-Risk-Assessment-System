"""Tests for the desktop data locations and the legacy database migration."""

from __future__ import annotations

import sqlite3

import paths


def test_user_data_dir_honours_override(tmp_path, monkeypatch):
    monkeypatch.setenv("SURAS_DATA_DIR", str(tmp_path / "appdata"))
    resolved = paths.user_data_dir()
    assert resolved == (tmp_path / "appdata").resolve()
    assert resolved.is_dir()


def test_database_path_is_writable_and_outside_extraction_dir(tmp_path, monkeypatch):
    monkeypatch.setenv("SURAS_DATA_DIR", str(tmp_path / "appdata"))
    database = paths.database_path()
    assert database.parent.is_dir()
    connection = sqlite3.connect(database)
    connection.execute("CREATE TABLE t (id INTEGER)")
    connection.close()
    assert database.is_file()


def test_legacy_database_is_copied_once(tmp_path, monkeypatch):
    legacy_dir = tmp_path / "legacy"
    legacy_dir.mkdir()
    legacy_db = legacy_dir / paths.DB_FILE_NAME
    connection = sqlite3.connect(legacy_db)
    connection.execute("CREATE TABLE patients (id INTEGER PRIMARY KEY)")
    connection.execute("INSERT INTO patients (id) VALUES (7)")
    connection.commit()
    connection.close()

    monkeypatch.setenv("SURAS_DATA_DIR", str(tmp_path / "appdata"))
    monkeypatch.setattr(paths, "legacy_data_dirs", lambda: [legacy_dir])

    destination = paths.database_path()
    assert destination.is_file()
    assert destination != legacy_db
    assert legacy_db.is_file(), "migration must copy, not move, the legacy database"

    # A second call must not overwrite the migrated copy.
    with sqlite3.connect(destination) as connection:
        connection.execute("INSERT INTO patients (id) VALUES (8)")
    paths.database_path()
    with sqlite3.connect(destination) as connection:
        rows = connection.execute("SELECT id FROM patients ORDER BY id").fetchall()
    assert rows == [(7,), (8,)]


def test_existing_database_is_not_replaced(tmp_path, monkeypatch):
    legacy_dir = tmp_path / "legacy"
    legacy_dir.mkdir()
    sqlite3.connect(legacy_dir / paths.DB_FILE_NAME).close()

    monkeypatch.setenv("SURAS_DATA_DIR", str(tmp_path / "appdata"))
    monkeypatch.setattr(paths, "legacy_data_dirs", lambda: [legacy_dir])
    first = paths.database_path()
    first.write_bytes(b"current database")
    assert paths.database_path().read_bytes() == b"current database"


def test_shared_dir_points_at_repository_specs():
    shared = paths.shared_dir()
    assert (shared / "version.json").is_file()
    assert (shared / "model" / "model.onnx").is_file()
