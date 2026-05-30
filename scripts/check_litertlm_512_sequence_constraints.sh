#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
OUT_DIR="$ROOT_DIR/artifacts/qairt244_litertlm_512_sequence_constraints/$TIMESTAMP"
PATTERN='512|seq|sequence|sequential|prefill|context|max[_-]?tokens|max[_-]?seq[_-]?len|input[_-]?length|max[_-]?length|kv[_-]?cache|token|prompt|decode'
MODEL_PATHS=()

usage() {
  cat <<'EOF'
Usage:
  scripts/check_litertlm_512_sequence_constraints.sh [--out-dir <dir>] <model.litertlm> [...]

Scans local .litertlm files for static strings/metadata that may indicate a
512 sequence/prefill/context/input-length constraint. This is read-only and
does not copy model binaries into git artifacts.

Example with an explicit target path:
  scripts/check_litertlm_512_sequence_constraints.sh \
    /path/to/gemma-4-E2B-it_qualcomm_sm8750.litertlm

Outputs:
  artifacts/qairt244_litertlm_512_sequence_constraints/<timestamp>/
    summary.md
    model_inventory.tsv
    <model>_file.txt
    <model>_sha256.txt
    <model>_zip_listing.txt
    <model>_strings_hits.txt
    <model>_metadata_candidates.txt
EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    --out-dir)
      OUT_DIR="${2:-}"
      shift 2
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      MODEL_PATHS+=("$1")
      shift
      ;;
  esac
done

mkdir -p "$OUT_DIR"

safe_name() {
  printf '%s' "$1" | sed 's#/#_#g; s#[^A-Za-z0-9._-]#_#g; s#_\\{2,\\}#_#g; s#^_##; s#_$##'
}

scan_model() {
  local model="$1"
  local name
  name="$(safe_name "$(basename "$model")")"
  {
    printf 'model_path=%s\n' "$model"
    file "$model" 2>/dev/null || true
    du -h "$model" 2>/dev/null || true
  } >"$OUT_DIR/${name}_file.txt"
  sha256sum "$model" >"$OUT_DIR/${name}_sha256.txt" 2>/dev/null || true
  unzip -l "$model" >"$OUT_DIR/${name}_zip_listing.txt" 2>&1 || true
  strings "$model" 2>/dev/null |
    grep -Eai "$PATTERN" |
    sort -u |
    head -1000 >"$OUT_DIR/${name}_strings_hits.txt" || true
  {
    printf '# metadata candidate scan for %s\n\n' "$model"
    printf '## zip entries matching sequence/prefill/context\n\n'
    grep -Eai "$PATTERN" "$OUT_DIR/${name}_zip_listing.txt" || true
    printf '\n## string hits containing 512 or sequence terms\n\n'
    grep -Eai '512|seq|sequence|prefill|context|max[_-]?seq|max[_-]?tokens|input[_-]?length' \
      "$OUT_DIR/${name}_strings_hits.txt" || true
  } >"$OUT_DIR/${name}_metadata_candidates.txt"
  printf '%s\t%s\t%s\t%s\n' \
    "$model" \
    "$(du -h "$model" 2>/dev/null | awk '{print $1}')" \
    "$(sha256sum "$model" 2>/dev/null | awk '{print $1}')" \
    "$(grep -Eai '512|seq|sequence|prefill|context|max[_-]?seq|max[_-]?tokens|input[_-]?length' "$OUT_DIR/${name}_strings_hits.txt" | wc -l)"
}

{
  printf 'model_path\tsize\tsha256\tsequence_candidate_hit_count\n'
  for model in "${MODEL_PATHS[@]}"; do
    if [ ! -f "$model" ]; then
      printf '%s\tmissing\tmissing\t0\n' "$model"
      continue
    fi
    scan_model "$model"
  done
} >"$OUT_DIR/model_inventory.tsv"

{
  printf '# LiteRT-LM 512 sequence constraint static scan\n\n'
  printf -- '- artifact: `%s`\n' "${OUT_DIR#$ROOT_DIR/}"
  printf -- '- model_count: `%s`\n' "${#MODEL_PATHS[@]}"
  printf -- '- scope: local .litertlm static scan only; no NPU execution; no app route changes.\n\n'
  if [ "${#MODEL_PATHS[@]}" -eq 0 ]; then
    printf 'No model paths were provided. Pass the SM8750 `.litertlm` path explicitly.\n\n'
  fi
  printf '## Inventory\n\n'
  printf '| model_path | size | sha256 | sequence_candidate_hit_count |\n'
  printf '| --- | ---: | --- | ---: |\n'
  tail -n +2 "$OUT_DIR/model_inventory.tsv" |
    awk -F '\t' '{ printf "| `%s` | `%s` | `%s` | %s |\n", $1, $2, $3, $4 }'
  printf '\n## Interpretation\n\n'
  printf -- '- Static string hits are only hints; absence of strings does not disprove a compiled graph shape limit.\n'
  printf -- '- A strong 512 sequence/prefill hypothesis needs runtime evidence that decode/prefill behavior changes at the final-input boundary.\n'
  printf -- '- This script does not stage binaries and does not add large model files to git.\n'
} >"$OUT_DIR/summary.md"

printf 'summary=%s\n' "$OUT_DIR/summary.md"
