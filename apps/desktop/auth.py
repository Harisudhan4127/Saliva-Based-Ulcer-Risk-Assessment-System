"""Local access password for the desktop app.

Earlier versions shipped a hard-coded ``admin / admin@123`` credential, which is
equivalent to having no protection at all. This module stores a salted,
PBKDF2-HMAC-SHA256 hash of a password chosen by the person installing the app.
The password never leaves the machine and is only a local access barrier; it is
not authentication for any server and is not a substitute for disk encryption.

Recovery: delete ``access.json`` from the user data directory. That resets the
local password but does not delete or decrypt patient records.
"""

from __future__ import annotations

import base64
import hashlib
import hmac
import json
import os
from pathlib import Path
from typing import Any

from paths import user_data_dir

AUTH_FILE_NAME = "access.json"
MIN_PASSWORD_LENGTH = 8
PBKDF2_ITERATIONS = 390_000
SALT_BYTES = 16
HASH_NAME = "sha256"
SCHEMA_VERSION = 1


class PasswordError(ValueError):
    """Raised when a password does not meet the local policy."""


def auth_file() -> Path:
    return user_data_dir() / AUTH_FILE_NAME


def is_configured() -> bool:
    try:
        data = _read()
    except (OSError, ValueError, KeyError, TypeError):
        return False
    return bool(data.get("hash") and data.get("salt"))


def set_password(password: str) -> None:
    validate_password(password)
    salt = os.urandom(SALT_BYTES)
    digest = _derive(password, salt, PBKDF2_ITERATIONS)
    payload = {
        "schemaVersion": SCHEMA_VERSION,
        "algorithm": f"pbkdf2-{HASH_NAME}",
        "iterations": PBKDF2_ITERATIONS,
        "salt": base64.b64encode(salt).decode("ascii"),
        "hash": base64.b64encode(digest).decode("ascii"),
    }
    path = auth_file()
    tmp = path.with_suffix(".tmp")
    # Restrict permissions where the platform supports it.
    tmp.write_text(json.dumps(payload, indent=2), encoding="utf-8")
    try:
        os.chmod(tmp, 0o600)
    except OSError:
        pass
    os.replace(tmp, path)


def verify_password(password: str) -> bool:
    if not isinstance(password, str) or not password:
        return False
    try:
        data = _read()
        salt = base64.b64decode(data["salt"])
        expected = base64.b64decode(data["hash"])
        iterations = int(data.get("iterations", PBKDF2_ITERATIONS))
    except (OSError, ValueError, KeyError, TypeError):
        return False
    if iterations <= 0 or not salt or not expected:
        return False
    actual = _derive(password, salt, iterations)
    return hmac.compare_digest(actual, expected)


def validate_password(password: str) -> None:
    if not isinstance(password, str) or len(password) < MIN_PASSWORD_LENGTH:
        raise PasswordError(
            f"Password must be at least {MIN_PASSWORD_LENGTH} characters."
        )
    if password.strip() != password:
        raise PasswordError("Password must not start or end with whitespace.")


def _derive(password: str, salt: bytes, iterations: int) -> bytes:
    return hashlib.pbkdf2_hmac(HASH_NAME, password.encode("utf-8"), salt, iterations)


def _read() -> dict[str, Any]:
    raw = auth_file().read_text(encoding="utf-8")
    data = json.loads(raw)
    if not isinstance(data, dict):
        raise ValueError("invalid access file")
    return data
