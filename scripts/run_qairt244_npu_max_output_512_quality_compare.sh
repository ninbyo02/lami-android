#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

case "${1:-}" in
  --help|-h)
    cat <<'EOF'
Usage:
  scripts/run_qairt244_npu_max_output_512_quality_compare.sh --preflight-only --artifact <native-build-artifact> [--sm8750-evidence <file-or-dir>]

This max512 phase is guard/preflight-only. The runner refuses device selection,
app launch, NPU generation, Engine.initialize, RunDecode, and quality compare
execution. It delegates to the max512 guard preflight and exits after static
artifact recognition.
EOF
    exit 0
    ;;
esac

exec "$ROOT_DIR/scripts/run_qairt244_npu_max_output_512_guard_preflight.sh" --preflight-only "$@"
