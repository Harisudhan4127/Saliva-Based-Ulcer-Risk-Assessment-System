import urllib.request
import json
import os
import sys
import subprocess

CURRENT_VERSION = "v1.0.0"
API_URL = "https://api.github.com/repos/Harisudhan4127/Saliva-Based-Ulcer-Risk-Assessment-System/releases/latest"

def check_update(progress_callback=None, status_callback=None):
    try:
        if status_callback:
            status_callback("Checking for updates...")

        with urllib.request.urlopen(API_URL) as res:
            data = json.loads(res.read().decode())

        latest = data.get("tag_name", "")

        if latest == CURRENT_VERSION:
            if status_callback:
                status_callback("App is up to date")
            return False

        for asset in data.get("assets", []):
            if asset["name"].endswith(".exe"):
                url = asset["browser_download_url"]
                file_path = "update.exe"

                download_with_progress(url, file_path, progress_callback)

                if status_callback:
                    status_callback("Installing update...")

                subprocess.Popen(file_path)
                sys.exit()

    except Exception as e:
        if status_callback:
            status_callback("Update failed")
        print("Update error:", e)

    return False


def download_with_progress(url, filename, progress_callback):
    def report(block_num, block_size, total_size):
        downloaded = block_num * block_size
        percent = int(downloaded * 100 / total_size)

        if progress_callback:
            progress_callback(min(percent, 100))

    urllib.request.urlretrieve(url, filename, reporthook=report)