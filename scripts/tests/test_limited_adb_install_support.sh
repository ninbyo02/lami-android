#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

help_text="$(./update.sh --help)"
[[ "$help_text" == *"--host HOST"* ]] || fail "update.sh help should document --host HOST"
[[ "$help_text" == *"Allowed hosts"* ]] || fail "update.sh help should document allowed hosts"

grep -q '^DEFAULT_PHONE_HOST=' update.sh || fail "update.sh should expose DEFAULT_PHONE_HOST"
grep -q 'validate_phone_host' update.sh || fail "update.sh should validate ADB host values"
grep -q 'validate_adb_port' update.sh || fail "update.sh should validate ADB port values"
grep -q 'adb connect "${phone_host}:${port}"' update.sh || fail "update.sh should use the selected phone host for adb connect"

test -f scripts/lami_build_remote_control_limited_adb.sh || fail "limited ADB remote_control template missing"
grep -q 'install-future' scripts/lami_build_remote_control_limited_adb.sh || fail "template should expose install-future"
grep -q 'adb-devices' scripts/lami_build_remote_control_limited_adb.sh || fail "template should expose adb-devices"
grep -q 'validate_host' scripts/lami_build_remote_control_limited_adb.sh || fail "template should validate host allowlist"

echo "limited ADB install support checks passed"
