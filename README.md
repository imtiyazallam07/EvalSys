# Computer Based Test (CBT) System

A robust, portable Computer Based Assessment application featuring dynamic test content loading, response storage, automatic evaluation, and cloud synchronisation (Dropbox integration).

---

## System Requirements

| Requirement | Details |
|---|---|
| **OS** | Windows 10 or later (64-bit) |
| **Java 21+** | Must be installed and available on the system `PATH`. Download from [Oracle JDK 21 for Windows](https://www.oracle.com/in/java/technologies/downloads/#jdk21-windows) |
| **Python 3.x** | Either a portable interpreter (recommended) or a global installation. |

### Python Options

#### Option A – Portable Python (Recommended)
Download a portable Python package and extract it into a folder named **`Python`** in the project root so that `Python\python.exe` exists.

🔗 [Download Portable Python from SourceForge](https://sourceforge.net/projects/portable-python/)

#### Option B – System Python
Install Python 3.x globally from [python.org](https://www.python.org/downloads/). `start_test.bat` and `run_evaluator.bat` will detect it automatically if no portable interpreter is found.

---

## Project Structure

```
Computer Based Test/
├── start_test.bat          ← Entry point – run this to start
├── run_evaluator.bat       ← Called automatically after the test
├── load_data.py            ← Downloads assets and launches the Java GUI
├── evaluator.py            ← Evaluates responses and uploads reports
├── .env                    ← Environment configuration (not committed to Git)
├── dist/
│   └── Computer_Based_Test.jar
├── lib/                    ← Java dependency JARs
├── src/
│   ├── Details.java
│   └── Main.java           ← Java source
└── Python/                 ← Portable Python interpreter (not committed to Git)
```

---

## Setup

### 1. Configure `.env`

Create a file named `.env` in the project root. This file is **never committed to Git**.

```env
# ── Hosted file URLs ──────────────────────────────────────────
CONFIG_LINK=https://raw.githubusercontent.com/imtiyaz-allam/questions/refs/heads/main/config.json
QUESTION_LINK=https://raw.githubusercontent.com/imtiyaz-allam/questions/refs/heads/main/question.json
SOLUTION_LINK=https://raw.githubusercontent.com/imtiyaz-allam/questions/refs/heads/main/solution.json

# ── Dropbox API credentials ───────────────────────────────────
DROPBOX_APP_KEY=your_app_key_here
DROPBOX_APP_SECRET=your_app_secret_here
DROPBOX_REFRESH_TOKEN=your_refresh_token_here

# ── Local pre-exist flag ──────────────────────────────────────
PRE_EXIST=false
```

#### `.env` Key Reference

| Key | Type | Description |
|---|---|---|
| `CONFIG_LINK` | URL | Public URL of `config.json`. See [config.json format](#configjson-format) below. |
| `QUESTION_LINK` | URL | Public URL of `question.json`, which maps subject names to CSV file URLs. |
| `SOLUTION_LINK` | URL | Public URL of the master answer-key `solution.json` used by the evaluator. |
| `DROPBOX_APP_KEY` | String | Dropbox OAuth2 App Key for automatic report uploads. |
| `DROPBOX_APP_SECRET` | String | Dropbox OAuth2 App Secret. |
| `DROPBOX_REFRESH_TOKEN` | String | Long-lived Dropbox refresh token. |
| `PRE_EXIST` | `true` / `false` | `true` → skip all downloads and run with existing local files. `false` (default) → download fresh copies of config, questions, and solution on every run. |

> [!NOTE]
> Set `PRE_EXIST=true` when question and config files are already placed locally (e.g. running in an offline lab). Set to `false` for a standard cloud-hosted deployment.

---

## `config.json` Format

`config.json` is a small JSON file hosted at the URL specified by `CONFIG_LINK` in `.env` (or hardcoded in `Main.java` as `CONFIG_URL`). It is downloaded automatically at startup and saved locally as `C:\CBT\config.json`.

### Minimum Required Format

```json
{
  "dur": 180,
  "solution": "https://example.com/path/to/solution.json"
}
```

### Field Reference

| Field | Type | Required | Description |
|---|---|---|---|
| `dur` | Integer | **Yes** | Exam duration in **minutes**. Converted to seconds internally. Defaults to `180` if missing. |
| `solution` | String (URL) | No | Public URL to download `solution.json` after the test ends. Can be overridden by `SOLUTION_LINK` in `.env`. |

### Full Example

```json
{
  "dur": 120,
  "solution": "https://raw.githubusercontent.com/your-org/your-repo/main/solution.json"
}
```

> [!IMPORTANT]
> The `solution` URL in `config.json` acts as a **fallback**. If `SOLUTION_LINK` is set in `.env`, it takes priority over the value in `config.json`.

> [!NOTE]
> **System Requirement: Windows.** The application is designed to run on Windows. `start_test.bat` and `run_evaluator.bat` are Windows batch scripts. Java and Python must be accessible on the system `PATH` or via the portable interpreter placed in the `Python\` folder.

---

## Running

### Standard Test Mode

Double-click `start_test.bat` or run it from a terminal:

```cmd
start_test.bat
```

**What happens:**
1. Java installation is verified; an error with the download link is shown if missing.
2. Python interpreter is located (portable first, then system).
3. `load_data.py` runs:
   - If `PRE_EXIST=true`: downloads `config.json`, question CSVs, and `solution.json` into the correct directories, then launches the Java GUI.
   - If `PRE_EXIST=false`: skips downloads and launches the Java GUI directly.
4. Student fills in their details, takes the test, and submits.
5. Java saves responses and automatically calls `run_evaluator.bat`.
6. `evaluator.py` scores the responses, generates a CSV report, and uploads it to Dropbox.

### Admin Mode (Answer-Key Creation)

```cmd
start_test.bat --admin-mode
```

**What happens:**
1. Same Python and Java checks as above.
2. Downloads `config.json` and question CSVs **only** (solution download is skipped — the admin creates it).
3. Launches the Java GUI **without** the student details dialog.
4. Admin navigates through all questions selecting the correct answers.
5. On test end, the selected answers are saved as `solutions/solution.json` and `solution.json`.
6. `evaluator.py` is **not** launched.

---

## Installing Python Packages

Both `load_data.py` and `evaluator.py` perform a startup dependency check and print a single install command if any package is missing.

`evaluator.py` requires the **Dropbox SDK**:

```cmd
python -m pip install dropbox
```

Or, using the portable interpreter:

```cmd
Python\python.exe -m pip install dropbox
```

If you extend `load_data.py` with additional libraries (e.g. `boto3`, `requests`), add them to the `_REQUIRED_PACKAGES` list at the top of the file — users will automatically receive the correct install command.

---

## Customising Downloads for Secure Cloud Storage

If your files are hosted on a private/authenticated storage service (AWS S3, Azure Blob, Google Cloud Storage, or any API-key-protected server), modify the download helpers in `load_data.py` to add the required headers.

### Example — Adding Authentication Headers

Replace the plain `urllib.request.urlopen(url)` pattern in any download function with:

```python
def download_secure_file(url, target_path, headers=None):
    """Generic authenticated downloader. Pass a dict of HTTP headers."""
    import urllib.request
    print(f"Downloading {target_path} from {url} ...")
    try:
        req = urllib.request.Request(url)

        # ── Add your auth headers here ──────────────────────────
        if headers:
            for key, value in headers.items():
                req.add_header(key, value)
        # ────────────────────────────────────────────────────────

        with urllib.request.urlopen(req, timeout=15) as resp:
            data = resp.read()

        os.makedirs(os.path.dirname(target_path) or ".", exist_ok=True)
        with open(target_path, "wb") as f_out:
            f_out.write(data)
        print(f"Saved to {target_path}")
        return True
    except Exception as e:
        print(f"Download failed: {e}")
        return False
```

#### AWS S3 / API token example

```python
download_secure_file(
    url="https://your-bucket.s3.amazonaws.com/config.json",
    target_path="config.json",
    headers={
        "Authorization": "Bearer YOUR_TOKEN",
        "x-api-key": "YOUR_API_KEY",
    }
)
```

#### Google Cloud Storage (signed URL — no headers needed)
```python
download_secure_file(
    url="https://storage.googleapis.com/your-bucket/config.json?X-Goog-Signature=...",
    target_path="config.json",
)
```

Store any secrets used here in `.env` (add new keys as needed) and read them via `env.get("YOUR_KEY")` — **never hard-code credentials in source files**.

---

## `.gitignore` Summary

The following are excluded from version control:

| Excluded | Reason |
|---|---|
| `.env` | Contains secrets / personal credentials |
| `Python/` | Large binary; user installs their own |
| `__pycache__/` | Python bytecode cache |
| `config.json`, `qstns/`, `solutions/`, `response/` | Downloaded or generated at runtime |
| `examinee_details.json` | Personal test data |
| `build/`, `nbproject/private/` | IDE / build artefacts |
