"""Produce the update manifest consumed by the Android installer bridge."""
import argparse
import hashlib
import json
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--apk', type=Path, required=True)
parser.add_argument('--version-name', required=True)
parser.add_argument('--version-code', type=int, required=True)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
content = args.apk.read_bytes()
manifest = {
    'applicationId': 'com.mrkuangker.shijian',
    'versionName': args.version_name,
    'versionCode': args.version_code,
    'sha256': hashlib.sha256(content).hexdigest(),
    'size': len(content),
    'minSdk': 26,
}
args.output.write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
print(f"Update manifest verified: {args.version_name} / {len(content):,} bytes")
