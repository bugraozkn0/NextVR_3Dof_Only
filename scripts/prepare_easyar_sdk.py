"""Extract ONLY the pinned Android Community runtime from the user's official 4.11.0 archive.
No downloads, installs or license copying. Usage: python scripts/prepare_easyar_sdk.py SDK.7z
Requires an existing 7-Zip executable; --sevenzip can give its path.
"""
import argparse
import hashlib
from pathlib import Path
import shutil
import subprocess

FILES = {
    "EasyAR.jar": "0abfeab996be69afb47a4ea3c4bc3ccf21e8eb1193bfc1fe002b7e0d499486ef",
    "arm64-v8a/libEasyAR.so": "ed2849fed5c5d7e45fb31d0f0b92dadb8c0d1fbe014d5238901b292559c4725e",
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    parser.add_argument("--sevenzip", default=shutil.which("7z") or "C:/Program Files/7-Zip/7z.exe")
    args = parser.parse_args()
    archive = args.archive.resolve(strict=True)
    root = Path(__file__).resolve().parents[1]
    dest = root / "vendor/easyar"
    for name, expected in FILES.items():
        target = dest / name
        target.parent.mkdir(parents=True, exist_ok=True)
        if target.is_file() and hashlib.sha256(target.read_bytes()).hexdigest() == expected:
            continue
        temporary = target.with_suffix(target.suffix + ".partial")
        try:
            with temporary.open("wb") as output:
                subprocess.run([args.sevenzip, "e", "-so", str(archive),
                                "-i!variants/Community/Android/" + name], stdout=output, check=True)
            if hashlib.sha256(temporary.read_bytes()).hexdigest() != expected:
                raise RuntimeError("Wrong SDK version or extraction failure: " + name)
            temporary.replace(target)
        finally:
            temporary.unlink(missing_ok=True)
    print("EasyAR 4.11.0 Android Community jar + arm64 runtime ready (ignored by Git).")


if __name__ == "__main__":
    main()
