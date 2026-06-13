#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
EDGE_APK_DIR="$ROOT_DIR/artifacts/external/edge_gallery_apks"
EDGE_STATIC_DIR="$ROOT_DIR/artifacts/edge_gallery_static"
LAMI_APK="$ROOT_DIR/app/build/outputs/apk/standard/debug/app-standard-debug.apk"
DRY_RUN=0

usage() {
  printf 'usage: %s [--edge-apks <dir>] [--edge-static <dir>] [--lami-apk <apk>] [--dry-run]\n' "$0"
  printf 'default edge apks: %s\n' "$EDGE_APK_DIR"
  printf 'default edge static output: %s\n' "$EDGE_STATIC_DIR"
  printf 'default lami apk: %s\n' "$LAMI_APK"
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --edge-apks)
      shift
      EDGE_APK_DIR="${1:-}"
      ;;
    --edge-static|--output)
      shift
      EDGE_STATIC_DIR="${1:-}"
      ;;
    --lami-apk)
      shift
      LAMI_APK="${1:-}"
      ;;
    --dry-run)
      DRY_RUN=1
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      printf 'unknown argument: %s\n' "$1" >&2
      usage >&2
      exit 2
      ;;
  esac
  shift || true
done

MODEL_IDENTITY_REPORT="$EDGE_STATIC_DIR/model_identity_report.md"
FULL_NATIVE_DIFF_REPORT="$EDGE_STATIC_DIR/full_native_runtime_diff.md"
GPU_ARTISAN_ACCESS_REPORT="$EDGE_STATIC_DIR/gpu_artisan_access_path.md"

if [ "$DRY_RUN" = "1" ]; then
  printf 'Would inspect Edge Gallery APKs from: %s\n' "$EDGE_APK_DIR"
  printf 'Would read/write Edge Gallery static artifacts in: %s\n' "$EDGE_STATIC_DIR"
  printf 'Would compare LAMI APK when present: %s\n' "$LAMI_APK"
  printf 'Would write: %s\n' "$MODEL_IDENTITY_REPORT"
  printf 'Would write: %s\n' "$FULL_NATIVE_DIFF_REPORT"
  printf 'Would write: %s\n' "$GPU_ARTISAN_ACCESS_REPORT"
  exit 0
fi

mkdir -p "$EDGE_STATIC_DIR"

zip_entries() {
  local apk="$1"
  if command -v zipinfo >/dev/null 2>&1; then
    zipinfo -1 "$apk" 2>/dev/null
  else
    unzip -Z1 "$apk" 2>/dev/null
  fi
}

sha_for() {
  local file="$1"
  if [ -f "$file" ] && command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$file" | awk '{print $1}'
  else
    printf 'unavailable'
  fi
}

size_for() {
  local file="$1"
  if [ -f "$file" ]; then
    wc -c <"$file" 2>/dev/null | awk '{print $1}'
  else
    printf 'unavailable'
  fi
}

build_id_for() {
  local file="$1"
  if [ -f "$file" ] && command -v readelf >/dev/null 2>&1; then
    readelf -n "$file" 2>/dev/null | awk '/Build ID:/ {print $3; exit}'
  else
    printf 'unavailable'
  fi
}

contains_literal() {
  local file="$1"
  local literal="$2"
  if [ -f "$file" ] && grep -Faiq "$literal" "$file" 2>/dev/null; then
    printf 'yes'
  else
    printf 'no'
  fi
}

contains_regex() {
  local file="$1"
  local pattern="$2"
  if [ -f "$file" ] && grep -Eaiq "$pattern" "$file" 2>/dev/null; then
    printf 'yes'
  else
    printf 'no'
  fi
}

collect_edge_text_corpus() {
  local out="$1"
  : >"$out"

  if [ -d "$EDGE_STATIC_DIR" ]; then
    find "$EDGE_STATIC_DIR" -type f \( -name '*.txt' -o -name '*.tsv' \) 2>/dev/null |
      sort |
      while IFS= read -r source; do
        [ -f "$source" ] || continue
        printf '\n===== %s =====\n' "$source" >>"$out"
        grep -Eai 'Gemma|gemma|E2B|E4B|\.litertlm|huggingface|ai\.google\.dev|modelFile|modelName|modelPath|model_download|backend|constraint|GPU_ARTISAN|CPU_ARTISAN|GOOGLE_TENSOR_ARTISAN|Artisan|LlmGpuArtisanExecutor|RuntimeConfig|EngineConfig|PreferredEngineType|kv_cache|qualcomm|sm8750' "$source" >>"$out" 2>/dev/null || true
      done
  fi

  if [ -d "$EDGE_APK_DIR" ]; then
    find "$EDGE_APK_DIR" -maxdepth 1 -type f -name '*.apk' 2>/dev/null |
      sort |
      while IFS= read -r apk; do
        zip_entries "$apk" |
          grep -E '(^classes[0-9]*\.dex$|^lib/arm64-v8a/.*\.so$)' |
          while IFS= read -r entry; do
            printf '\n===== %s:%s =====\n' "$apk" "$entry" >>"$out"
            unzip -p "$apk" "$entry" 2>/dev/null |
              strings -a 2>/dev/null |
              grep -Eai 'Gemma|gemma|E2B|E4B|\.litertlm|huggingface|ai\.google\.dev|modelFile|modelName|modelPath|model_download|backend|constraint|GPU_ARTISAN|CPU_ARTISAN|GOOGLE_TENSOR_ARTISAN|Artisan|LlmGpuArtisanExecutor|RuntimeConfig|EngineConfig|PreferredEngineType|kv_cache|qualcomm|sm8750' >>"$out" 2>/dev/null || true
          done
      done
  fi
}

collect_apk_model_entries() {
  local out="$1"
  : >"$out"
  if [ ! -d "$EDGE_APK_DIR" ]; then
    printf 'edge_apk_dir_missing=%s\n' "$EDGE_APK_DIR" >"$out"
    return
  fi
  find "$EDGE_APK_DIR" -maxdepth 1 -type f -name '*.apk' 2>/dev/null |
    sort |
    while IFS= read -r apk; do
      zip_entries "$apk" |
        grep -Eai '(\.litertlm($|[/?#])|\.task($|[/?#])|\.tflite($|[/?#])|\.gguf($|[/?#])|tokenizer.*(\.model|\.json|\.txt|\.bin)$|sentencepiece|spiece|gemma.*\.(litertlm|task|tflite|gguf|bin)$)' |
        sed "s#^#$(basename "$apk"):#" >>"$out" || true
    done
}

extract_arm64_libs_from_apk_dir() {
  local apk_dir="$1"
  local out_dir="$2"
  local sources="$3"
  mkdir -p "$out_dir"
  : >"$sources"
  [ -d "$apk_dir" ] || return
  find "$apk_dir" -maxdepth 1 -type f -name '*.apk' 2>/dev/null |
    sort |
    while IFS= read -r apk; do
      zip_entries "$apk" |
        grep '^lib/arm64-v8a/.*\.so$' |
        while IFS= read -r entry; do
          local lib
          lib="$(basename "$entry")"
          printf '%s\t%s\t%s\n' "$lib" "$(basename "$apk")" "$entry" >>"$sources"
          if [ ! -f "$out_dir/$lib" ]; then
            unzip -p "$apk" "$entry" >"$out_dir/$lib" 2>/dev/null || true
          fi
        done
    done
}

extract_arm64_libs_from_apk() {
  local apk="$1"
  local out_dir="$2"
  local sources="$3"
  mkdir -p "$out_dir"
  : >"$sources"
  [ -f "$apk" ] || return
  zip_entries "$apk" |
    grep '^lib/arm64-v8a/.*\.so$' |
    while IFS= read -r entry; do
      local lib
      lib="$(basename "$entry")"
      printf '%s\t%s\t%s\n' "$lib" "$(basename "$apk")" "$entry" >>"$sources"
      if [ ! -f "$out_dir/$lib" ]; then
        unzip -p "$apk" "$entry" >"$out_dir/$lib" 2>/dev/null || true
      fi
    done
}

lib_summary() {
  local dir="$1"
  local lib="$2"
  local sources="$3"
  local path="$dir/$lib"
  if [ ! -f "$path" ]; then
    printf 'no'
    return
  fi
  local source_hint
  source_hint="$(grep -F "$lib	" "$sources" 2>/dev/null | awk -F '\t' '{print $2 ":" $3}' | paste -sd ',' -)"
  [ -n "$source_hint" ] || source_hint='unknown_source'
  printf 'yes; size=%s; sha256=%s; build_id=%s; source=%s' \
    "$(size_for "$path")" \
    "$(sha_for "$path")" \
    "$(build_id_for "$path")" \
    "$source_hint"
}

risk_for_lib() {
  local lib="$1"
  local edge_dir="$2"
  local lami_dir="$3"
  local edge_path="$edge_dir/$lib"
  local lami_path="$lami_dir/$lib"
  if [ ! -f "$edge_path" ] && [ ! -f "$lami_path" ]; then
    printf 'none'
  elif [ -f "$edge_path" ] && [ -f "$lami_path" ]; then
    if [ "$(sha_for "$edge_path")" = "$(sha_for "$lami_path")" ]; then
      printf 'low'
    else
      case "$lib" in
        libLiteRt.so|liblitertlm_jni.so)
          printf 'high'
          ;;
        libLiteRtDispatch_Qualcomm.so|libLiteRtCompilerPlugin_Qualcomm.so|libGemmaModelConstraintProvider.so)
          printf 'medium-high'
          ;;
        libQnn*.so)
          printf 'medium'
          ;;
        *)
          printf 'medium'
          ;;
      esac
    fi
  else
    case "$lib" in
      libLiteRt.so|liblitertlm_jni.so)
        printf 'high'
        ;;
      libQnn*.so)
        printf 'medium'
        ;;
      *)
        printf 'unknown'
        ;;
    esac
  fi
}

note_for_lib() {
  local lib="$1"
  case "$lib" in
    libLiteRt.so)
      printf 'Core LiteRT runtime. Must stay aligned with LiteRT-LM JNI.'
      ;;
    liblitertlm_jni.so)
      printf 'LiteRT-LM JNI/executor stack. Single-file replacement is unsafe.'
      ;;
    libLiteRtDispatch_Qualcomm.so|libLiteRtCompilerPlugin_Qualcomm.so|libGemmaModelConstraintProvider.so)
      printf 'Qualcomm/model constraint support. Relevant to executor selection, but not proof of GPU_ARTISAN access.'
      ;;
    libQnnGpu.so)
      printf 'QNN GPU payload when present. Edge Gallery GPU evidence is LiteRT/LiteRT-LM based, so this is not the primary hypothesis.'
      ;;
    libQnn*.so)
      printf 'QNN payload, mostly NPU/Qualcomm stack context for this investigation.'
      ;;
    *Gpu*|*GPU*|*OpenCL*|*OpenCl*|*Vulkan*|*WebGPU*|*WebGpu*|*Accelerator*)
      printf 'GPU/OpenCL/WebGPU/accelerator-related library name.'
      ;;
    *)
      printf 'Runtime library observed in arm64-v8a APK payload.'
      ;;
  esac
}

write_model_identity_report() {
  local corpus="$1"
  local model_entries="$2"
  {
    printf '# Edge Gallery model identity report\n\n'
    printf 'Generated by `scripts/inspect_edge_gallery_model_identity.sh`.\n\n'
    printf '## Inputs\n\n'
    printf '%s\n' "- Edge Gallery APK directory: \`$EDGE_APK_DIR\`"
    printf '%s\n' "- Edge Gallery static directory: \`$EDGE_STATIC_DIR\`"
    printf '%s\n\n' "- LAMI APK for runtime comparison: \`$LAMI_APK\`"
    printf '## Exact / keyword presence\n\n'
    printf '| Hint | Present | Notes |\n'
    printf '| --- | --- | --- |\n'
    printf '| `.litertlm` | %s | Indicates LiteRT-LM model file handling, not a concrete model identity. |\n' "$(contains_literal "$corpus" ".litertlm")"
    printf '| `Gemma 4` | %s | UI/download text hint. |\n' "$(contains_regex "$corpus" "Gemma[ -]?4")"
    printf '| `E2B` | %s | May be marketing/config text; not file identity by itself. |\n' "$(contains_literal "$corpus" "E2B")"
    printf '| `E4B` | %s | Appears in the same Gemma 4 family strings when present. |\n' "$(contains_literal "$corpus" "E4B")"
    printf '| `huggingface.co/litert-community` | %s | Static download/source hint. |\n' "$(contains_literal "$corpus" "huggingface.co/litert-community")"
    printf '| `GPU_ARTISAN` | %s | Runtime/executor hint, not model file proof. |\n' "$(contains_literal "$corpus" "GPU_ARTISAN")"
    printf '| `Artisan model detected` | %s | Suggests model metadata can drive executor rewrite when present. |\n' "$(contains_literal "$corpus" "Artisan model detected")"
    printf '| `backend constraint` | %s | Suggests runtime/model constraint handling. |\n' "$(contains_regex "$corpus" "backend constraint")"
    printf '| `sm8750` | %s | Device/model-specific static hint. |\n' "$(contains_regex "$corpus" "sm8750|SM8750")"
    printf '| `qualcomm` | %s | Qualcomm-related static hint. |\n\n' "$(contains_regex "$corpus" "qualcomm|Qualcomm")"

    printf '## APK packaged model candidates\n\n'
    if [ -s "$model_entries" ]; then
      printf '```text\n'
      head -200 "$model_entries"
      printf '```\n\n'
    else
      printf 'No packaged model-like entries were found in the APK split list.\n\n'
    fi

    printf '## Static model-name / download hints\n\n'
    printf '```text\n'
    grep -Eai 'Gemma|gemma|E2B|E4B|\.litertlm|huggingface|ai\.google\.dev|model_download|modelFile|modelName|modelPath' "$corpus" 2>/dev/null |
      sed 's/[[:space:]]\+/ /g' |
      sort -u |
      head -160
    printf '```\n\n'

    printf '## Backend / constraint hints\n\n'
    printf '```text\n'
    grep -Eai 'GPU_ARTISAN|CPU_ARTISAN|GOOGLE_TENSOR_ARTISAN|Artisan|backend constraint|Model requires one of|Supported backends are|Preferred engine types|RuntimeConfig|EngineConfig' "$corpus" 2>/dev/null |
      sed 's/[[:space:]]\+/ /g' |
      sort -u |
      head -160
    printf '```\n\n'

    printf '## Identity conclusion\n\n'
    printf '%s\n' '- `same_model_claim=not_supported_by_static_apk`'
    printf '%s\n' '- `model_binary_in_apk=no_or_not_visible_from_static_split_list`'
    printf '%s\n' '- `download_url_specificity=generic_litert_community_or_gemma_links_only`'
    printf '%s\n' '- `edge_gallery_model_identity=unconfirmed`'
    printf '%s\n\n' '- `edge_gallery_model_appears_generic_or_artisan_specific=inconclusive`'
    printf 'The static APK/split artifacts show Gemma 4 / E2B / E4B and `.litertlm` handling hints, but they do not provide a model file SHA, size, or exact filename proving that Edge Gallery used the same `%s` file as LAMI. Treat Edge Gallery model identity as unknown until app data or an official model source gives size/hash/source evidence.\n\n' 'gemma-4-E2B-it.litertlm'

    printf '## Safe app-data inventory procedure\n\n'
    printf 'No logcat is required.\n\n'
    printf '```sh\n'
    printf "adb shell pm list packages | grep -i 'gallery\\|edge\\|google'\n"
    printf 'adb shell run-as <edge_gallery_package> ls -la\n'
    printf 'adb shell run-as <edge_gallery_package> find shared_prefs files databases -maxdepth 4 -print\n'
    printf '```\n\n'
    printf 'If `run-as` fails, record `run_as_available=false` and the exact shell message. Do not use adb backup or copy native runtime files from Edge Gallery into LAMI.\n'
  } >"$MODEL_IDENTITY_REPORT"
}

write_full_native_runtime_diff() {
  local work_dir="$1"
  local edge_lib_dir="$work_dir/edge_libs"
  local lami_lib_dir="$work_dir/lami_libs"
  local edge_sources="$work_dir/edge_sources.tsv"
  local lami_sources="$work_dir/lami_sources.tsv"
  mkdir -p "$work_dir"
  extract_arm64_libs_from_apk_dir "$EDGE_APK_DIR" "$edge_lib_dir" "$edge_sources"
  extract_arm64_libs_from_apk "$LAMI_APK" "$lami_lib_dir" "$lami_sources"

  local libs_file="$work_dir/libs.txt"
  {
    printf '%s\n' \
      libLiteRt.so \
      liblitertlm_jni.so \
      libLiteRtDispatch_Qualcomm.so \
      libLiteRtCompilerPlugin_Qualcomm.so \
      libGemmaModelConstraintProvider.so \
      libQnnSystem.so \
      libQnnGpu.so \
      libQnnHtp.so \
      libQnnHtpPrepare.so \
      libQnnHtpV79Stub.so \
      libQnnHtpV79Skel.so \
      libQnnDsp.so
    find "$edge_lib_dir" "$lami_lib_dir" -maxdepth 1 -type f -name '*.so' 2>/dev/null |
      sed 's#.*/##' |
      grep -E '(^libQnn.*\.so$|Gpu|GPU|OpenCL|OpenCl|Vulkan|WebGPU|WebGpu|Accelerator|LiteRt|litertlm|GemmaModelConstraintProvider)' || true
  } | sort -u >"$libs_file"

  {
    printf '# Edge Gallery vs LAMI full native runtime diff\n\n'
    printf 'Generated by `scripts/inspect_edge_gallery_model_identity.sh`.\n\n'
    printf '## Inputs\n\n'
    printf '%s\n' "- Edge Gallery APK directory: \`$EDGE_APK_DIR\`"
    printf '%s\n\n' "- LAMI APK: \`$LAMI_APK\`"
    if [ ! -f "$LAMI_APK" ]; then
      printf '> LAMI APK was not present when this report was generated. Run `./gradlew :app:assembleStandardDebug` and rerun the script for complete LAMI columns.\n\n'
    fi
    printf '## Runtime library matrix\n\n'
    printf '| Library | Edge Gallery | LAMI standardDebug | Risk | Notes |\n'
    printf '| --- | --- | --- | --- | --- |\n'
    while IFS= read -r lib; do
      [ -n "$lib" ] || continue
      printf '| `%s` | %s | %s | %s | %s |\n' \
        "$lib" \
        "$(lib_summary "$edge_lib_dir" "$lib" "$edge_sources")" \
        "$(lib_summary "$lami_lib_dir" "$lib" "$lami_sources")" \
        "$(risk_for_lib "$lib" "$edge_lib_dir" "$lami_lib_dir")" \
        "$(note_for_lib "$lib")"
    done <"$libs_file"
    printf '\n## Interpretation\n\n'
    printf '%s\n' '- `libLiteRt.so` and `liblitertlm_jni.so` are treated as a matched runtime stack. Do not replace either file individually in the standard flavor.'
    printf '%s\n' '- QNN libraries are kept in the matrix for context, but Edge Gallery GPU evidence is currently LiteRT/LiteRT-LM GPU/artisan evidence rather than `libQnnGpu.so` evidence.'
    printf '%s\n' '- A SHA/build-id mismatch in core LiteRT/LiteRT-LM libraries means the next safe experiment is an isolated flavor with a full runtime stack, not ad hoc native library replacement.'
  } >"$FULL_NATIVE_DIFF_REPORT"
}

write_gpu_artisan_access_path() {
  local corpus="$1"
  {
    printf '# GPU_ARTISAN access path static report\n\n'
    printf 'Generated by `scripts/inspect_edge_gallery_model_identity.sh`.\n\n'
    printf '## Keyword evidence\n\n'
    printf '| Keyword | Present |\n'
    printf '| --- | --- |\n'
    for keyword in \
      GPU_ARTISAN \
      CPU_ARTISAN \
      GOOGLE_TENSOR_ARTISAN \
      LlmGpuArtisanExecutor \
      PreferredEngineType \
      RuntimeConfig \
      BackendConstraint \
      ExecutorSelection \
      Artisan \
      GpuExecutor \
      'Artisan model detected' \
      'backend constraint mismatch' \
      'backend constraint is matched' \
      'Model requires one of' \
      'Supported backends are'; do
      printf '| `%s` | %s |\n' "$keyword" "$(contains_literal "$corpus" "$keyword")"
    done
    printf '\n## Access-path samples\n\n'
    printf '```text\n'
    grep -Eai 'GPU_ARTISAN|CPU_ARTISAN|GOOGLE_TENSOR_ARTISAN|LlmGpuArtisanExecutor|PreferredEngineType|RuntimeConfig|BackendConstraint|ExecutorSelection|Artisan model detected|backend constraint|Model requires one of|Supported backends are|GpuExecutor' "$corpus" 2>/dev/null |
      sed 's/[[:space:]]\+/ /g' |
      sort -u |
      head -220
    printf '```\n\n'
    printf '## Findings\n\n'
    printf '%s\n' '- `hidden_java_kotlin_api=unconfirmed`'
    printf '%s\n' '- `native_or_runtime_selector_evidence=present`'
    printf '%s\n' '- `model_metadata_driven_selector=possible_not_proven`'
    printf '%s\n' '- `edge_gallery_app_code_driven_selector=unproven_from_strings_only`'
    printf '%s\n\n' '- `lami_public_gpu_artisan_access=not_observed_currently`'
    printf 'The static strings strongly show a `GPU_ARTISAN`/`LlmGpuArtisanExecutor` route inside the Edge Gallery runtime stack. They do not prove a stable public Java/Kotlin API that LAMI can call. Combined with LAMI reflection reporting only `CPU,GPU,NPU`, the current safest reading is that `GPU_ARTISAN` is native/internal or metadata-driven unless future decompilation/API inspection proves otherwise.\n\n'
    printf 'Do not route normal LAMI chat to `GPU_ARTISAN` based only on these strings. If a probe is attempted later, keep it DEV-only and isolated from the standard runtime stack.\n'
  } >"$GPU_ARTISAN_ACCESS_REPORT"
}

TMP_DIR="${TMPDIR:-/tmp}/lami-edge-gallery-phase6-$$"
mkdir -p "$TMP_DIR"
CORPUS_FILE="$TMP_DIR/edge_gallery_phase6_corpus.txt"
MODEL_ENTRIES_FILE="$TMP_DIR/edge_gallery_model_entries.txt"

collect_edge_text_corpus "$CORPUS_FILE"
collect_apk_model_entries "$MODEL_ENTRIES_FILE"
write_model_identity_report "$CORPUS_FILE" "$MODEL_ENTRIES_FILE"
write_full_native_runtime_diff "$TMP_DIR/native"
write_gpu_artisan_access_path "$CORPUS_FILE"

printf 'Wrote %s\n' "$MODEL_IDENTITY_REPORT"
printf 'Wrote %s\n' "$FULL_NATIVE_DIFF_REPORT"
printf 'Wrote %s\n' "$GPU_ARTISAN_ACCESS_REPORT"
