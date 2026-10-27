"""Append DEX files to an already linked APK before zipalign/signing."""
import sys
import zipfile
from pathlib import Path

apk, dex_dir = map(Path, sys.argv[1:])
with zipfile.ZipFile(apk, "a", compression=zipfile.ZIP_DEFLATED) as archive:
    for dex in sorted(dex_dir.glob("*.dex")):
        archive.write(dex, dex.name)
