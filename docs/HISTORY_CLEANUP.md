# Git history cleanup

The repository history contains files that should never have been committed.
They have been **untracked** and added to `.gitignore`, but they are still
reachable in history. Rewriting history is destructive and was **not** performed
(it is outside the authorized scope for this change set).

> Until the history is rewritten and any exposed credential is rotated, treat
> the data below as public.

## What is in history

Verified with
`git log --all --pretty=format: --name-only --diff-filter=A | sort -u`:

| Path | Why it matters |
|------|----------------|
| `User_data/patients.db` | A SQLite patient database. Personal/health data. |
| `src/data.pkl` | A pickled scikit-learn model. Pickle executes code on load. |
| `src/data.csv` | The 300-row training dataset. Provenance unknown. |
| `__pycache__/backend.cpython-312.pyc` | Compiled bytecode. |
| `__pycache__/splash.cpython-312.pyc` | Compiled bytecode. |
| `__pycache__/updater.cpython-312.pyc` | Compiled bytecode. |
| `secret_key.py` | See below. |

### `secret_key.py`

At `545f43f` the file read the key from the environment:

```python
key = os.getenv("SECRET_KEY")
if not key:
    raise ValueError("SECRET_KEY missing in .env")
cipher = Fernet(key.encode())
```

No literal key is present in the tracked source, which is good. **However**, a
Fernet key was clearly used locally with `src/data.enc`. If the same key was ever
committed, pasted into a chat, or shared, it must be considered compromised.

## Recommended cleanup (manual, destructive)

Only run this on a copy or after coordinating with all collaborators. It rewrites
every commit hash and requires a force-push, which this change set deliberately
did **not** do.

### Option A — `git filter-repo` (preferred)

```bash
pip install git-filter-repo
# Work on a fresh clone; filter-repo rewrites history in place.
git clone <repo> suras-clean && cd suras-clean

git filter-repo \
  --path User_data/patients.db \
  --path src/data.pkl \
  --path src/data.csv \
  --path secret_key.py \
  --path-glob '__pycache__/*' \
  --invert-paths

git log --all --pretty=format: --name-only --diff-filter=A | sort -u   # verify
```

### Option B — BFG Repo-Cleaner

```bash
java -jar bfg.jar --delete-files patients.db
java -jar bfg.jar --delete-files data.pkl
java -jar bfg.jar --delete-files secret_key.py
java -jar bfg.jar --delete-folders __pycache__
git reflog expire --expire=now --all && git gc --prune=now --aggressive
```

### Then

```bash
git push --force-with-lease origin main   # NOT done here; requires owner sign-off
```

## Mandatory follow-up: rotate anything exposed

1. **Rotate the Fernet / `SECRET_KEY`** used to encrypt `src/data.enc`. Generate
   a new key and re-encrypt or regenerate the artifact.
2. **Rotate any release signing keystore** if it was ever committed (it was not,
   as far as this audit can tell — `*.keystore` / `*.jks` never appear in
   history).
3. **Invalidate any tokens** that may have been in a committed `.env` (none was
   found, but confirm).
4. **Delete stale forks/clones** that still contain the old objects.

## Current status

- Files untracked and git-ignored: **done**.
- Local copies still on disk (untracked): `apps/desktop/User_data/patients.db`,
  `apps/desktop/src/data.pkl`, `apps/desktop/src/data.csv`. These are ignored and
  are not part of the new build.
- History rewrite: **not done** (out of scope).
- Credential rotation: **not done** (owner action).
