"""Local access password tests (no Qt required)."""

from __future__ import annotations

import json

import pytest

import auth


@pytest.fixture(autouse=True)
def isolated_data_dir(tmp_path, monkeypatch):
    monkeypatch.setenv("SURAS_DATA_DIR", str(tmp_path))
    yield tmp_path


def test_no_default_password_is_configured():
    assert not auth.is_configured()
    assert not auth.verify_password("")
    assert not auth.verify_password("admin@123")


def test_set_and_verify_password():
    auth.set_password("correct horse battery staple")
    assert auth.is_configured()
    assert auth.verify_password("correct horse battery staple")
    assert not auth.verify_password("wrong password")
    assert not auth.verify_password("")


def test_password_is_never_stored_in_clear_text(isolated_data_dir):
    auth.set_password("a-very-secret-passphrase")
    raw = (isolated_data_dir / auth.AUTH_FILE_NAME).read_text(encoding="utf-8")
    assert "a-very-secret-passphrase" not in raw
    payload = json.loads(raw)
    assert payload["algorithm"] == "pbkdf2-sha256"
    assert payload["iterations"] >= 100_000
    assert len(payload["salt"]) > 0


@pytest.mark.parametrize("password", ["", "short", "1234567"])
def test_password_policy_rejects_short_values(password):
    with pytest.raises(auth.PasswordError):
        auth.set_password(password)


def test_password_policy_rejects_surrounding_whitespace():
    with pytest.raises(auth.PasswordError):
        auth.set_password(" valid-but-padded ")


def test_salt_is_random_per_install(isolated_data_dir):
    auth.set_password("same-password-twice")
    first = json.loads((isolated_data_dir / auth.AUTH_FILE_NAME).read_text())["salt"]
    auth.set_password("same-password-twice")
    second = json.loads((isolated_data_dir / auth.AUTH_FILE_NAME).read_text())["salt"]
    assert first != second


def test_corrupt_auth_file_fails_closed(isolated_data_dir):
    (isolated_data_dir / auth.AUTH_FILE_NAME).write_text("not json", encoding="utf-8")
    assert not auth.is_configured()
    assert not auth.verify_password("anything")
