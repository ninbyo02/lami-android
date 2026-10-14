#!/usr/bin/env python3
"""Read-only collision and native inventory preflight. Never installs or launches."""
import argparse
from pathlib import Path
import subprocess
import zipfile

PACKAGE = 'io.github.ninbyo02.lami.assistantprobe'

def preflight(apk: Path, adb: str, serial: str) -> tuple[bool, str]:
    with zipfile.ZipFile(apk) as archive:
        required = {'lib/arm64-v8a/liblami_resident_cp_diag.so',
                    'lib/arm64-v8a/libQnnHtp.so',
                    'lib/arm64-v8a/libQnnSystem.so',
                    'lib/arm64-v8a/libQnnHtpV79Stub.so'}
        missing = required.difference(archive.namelist())
    if missing:
        return False, 'missing libraries: ' + ','.join(sorted(missing))
    result = subprocess.run([adb, '-s', serial, 'shell', 'pm', 'path', PACKAGE],
                            capture_output=True, text=True, timeout=15, check=False)
    if result.returncode:
        return False, 'cannot establish package inventory'
    if 'package:' in result.stdout:
        return False, f'{PACKAGE} already installed; reject overwrite'
    if result.stdout.strip():
        return False, 'unexpected package inventory'
    return True, 'no package collision (installation NOT authorized or performed)'

if __name__ == '__main__':
    p = argparse.ArgumentParser()
    p.add_argument('apk', type=Path)
    p.add_argument('--serial', required=True)
    p.add_argument('--adb', default='adb')
    a = p.parse_args()
    safe, reason = preflight(a.apk, a.adb, a.serial)
    print(('PREFLIGHT_OK: ' if safe else 'PREFLIGHT_BLOCKED: ') + reason)
    raise SystemExit(0 if safe else 2)
