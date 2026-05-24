#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR" || exit 1

mode="full"
if [[ "${1:-}" == "--light" ]]; then
  mode="light"
elif [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  echo "Usage: $0 [--light]"
  echo
  echo "Default: package full diagnostic directories."
  echo "--light: package markdown docs and compact summaries only."
  exit 0
elif [[ -n "${1:-}" ]]; then
  echo "unknown option: $1" >&2
  echo "Usage: $0 [--light]" >&2
  exit 2
fi

timestamp="$(date +%Y%m%d_%H%M%S)"
suffix=""
if [[ "$mode" == "light" ]]; then
  suffix="_light"
fi
bundle_dir="artifacts/npu_issue_bundle/${timestamp}${suffix}"
zip_path="${bundle_dir}.zip"

mkdir -p "$bundle_dir/docs" "$bundle_dir/artifacts"

copy_path() {
  local src="$1"
  local dst="$2"
  if [[ -e "$src" ]]; then
    mkdir -p "$(dirname "$dst")"
    cp -R "$src" "$dst"
    echo "copied: $src -> $dst"
  else
    echo "missing: $src" | tee -a "$bundle_dir/missing.txt"
  fi
}

copy_path "docs/google_ai_edge_issue_body_litertlm_sm8750_npu.md" "$bundle_dir/docs/google_ai_edge_issue_body_litertlm_sm8750_npu.md"
copy_path "docs/google_ai_edge_issue_report_litertlm_sm8750_npu.md" "$bundle_dir/docs/google_ai_edge_issue_report_litertlm_sm8750_npu.md"
copy_path "docs/google_ai_edge_issue_short_summary.md" "$bundle_dir/docs/google_ai_edge_issue_short_summary.md"
copy_path "docs/google_ai_edge_issue_posting_checklist.md" "$bundle_dir/docs/google_ai_edge_issue_posting_checklist.md"
copy_path "docs/google_ai_edge_issue_submission_steps.md" "$bundle_dir/docs/google_ai_edge_issue_submission_steps.md"
copy_path "docs/litert_gallery_native_stack_experiment_plan.md" "$bundle_dir/docs/litert_gallery_native_stack_experiment_plan.md"
copy_path "docs/litert_npu_dispatch_runtime_compatibility_analysis.md" "$bundle_dir/docs/litert_npu_dispatch_runtime_compatibility_analysis.md"
copy_path "docs/litertlm_gallery_java_api_surface_mismatch.md" "$bundle_dir/docs/litertlm_gallery_java_api_surface_mismatch.md"
copy_path "docs/litert_custom_build_insertion_experiment.md" "$bundle_dir/docs/litert_custom_build_insertion_experiment.md"
copy_path "docs/litert_qnn_qairt_coupling_findings.md" "$bundle_dir/docs/litert_qnn_qairt_coupling_findings.md"
copy_path "docs/litert_qairt246_ref_search_results.md" "$bundle_dir/docs/litert_qairt246_ref_search_results.md"
copy_path "docs/qairt_244_acquisition_notes.md" "$bundle_dir/docs/qairt_244_acquisition_notes.md"
copy_path "docs/litert_custom_build_qairt244_compare.md" "$bundle_dir/docs/litert_custom_build_qairt244_compare.md"

if [[ "$mode" == "full" ]]; then
  copy_path "artifacts/gallery_dispatch_requirements/20260516_210635" "$bundle_dir/artifacts/gallery_dispatch_requirements_20260516_210635"
  copy_path "artifacts/npu_diagnostics/20260516_210643_gallerynpu" "$bundle_dir/artifacts/npu_diagnostics_20260516_210643_gallerynpu"
  copy_path "artifacts/litertlm_api_surface_compare/20260516_201159" "$bundle_dir/artifacts/litertlm_api_surface_compare_20260516_201159"
  copy_path "artifacts/litertlm_flavor_dependencies/20260516_204821" "$bundle_dir/artifacts/litertlm_flavor_dependencies_20260516_204821"
else
  mkdir -p "$bundle_dir/artifacts/gallery_dispatch_requirements_20260516_210635"
  copy_path "artifacts/gallery_dispatch_requirements/20260516_210635/requirements_summary.md" "$bundle_dir/artifacts/gallery_dispatch_requirements_20260516_210635/requirements_summary.md"
  copy_path "artifacts/gallery_dispatch_requirements/20260516_210635/gallery_libLiteRtDispatch_Qualcomm_strings.txt" "$bundle_dir/artifacts/gallery_dispatch_requirements_20260516_210635/gallery_libLiteRtDispatch_Qualcomm_strings.txt"
  copy_path "artifacts/gallery_dispatch_requirements/20260516_210635/gallery_libLiteRt_strings.txt" "$bundle_dir/artifacts/gallery_dispatch_requirements_20260516_210635/gallery_libLiteRt_strings.txt"

  mkdir -p "$bundle_dir/artifacts/npu_diagnostics_20260516_210643_gallerynpu"
  copy_path "artifacts/npu_diagnostics/20260516_210643_gallerynpu/crash_summary.md" "$bundle_dir/artifacts/npu_diagnostics_20260516_210643_gallerynpu/crash_summary.md"
  copy_path "artifacts/npu_diagnostics/20260516_210643_gallerynpu/abort_text_candidates.txt" "$bundle_dir/artifacts/npu_diagnostics_20260516_210643_gallerynpu/abort_text_candidates.txt"
  copy_path "artifacts/npu_diagnostics/20260516_210643_gallerynpu/loaded_libs_matrix.tsv" "$bundle_dir/artifacts/npu_diagnostics_20260516_210643_gallerynpu/loaded_libs_matrix.tsv"
  copy_path "artifacts/npu_diagnostics/20260516_210643_gallerynpu/loaded_libs_summary.md" "$bundle_dir/artifacts/npu_diagnostics_20260516_210643_gallerynpu/loaded_libs_summary.md"
  copy_path "artifacts/npu_diagnostics/20260516_210643_gallerynpu/native_lib_build_ids.txt" "$bundle_dir/artifacts/npu_diagnostics_20260516_210643_gallerynpu/native_lib_build_ids.txt"
  copy_path "artifacts/npu_diagnostics/20260516_210643_gallerynpu/stage_file.txt" "$bundle_dir/artifacts/npu_diagnostics_20260516_210643_gallerynpu/stage_file.txt"
  copy_path "artifacts/npu_diagnostics/20260516_210643_gallerynpu/logcat_litert_qnn_extract.txt" "$bundle_dir/artifacts/npu_diagnostics_20260516_210643_gallerynpu/logcat_litert_qnn_extract.txt"

  mkdir -p "$bundle_dir/artifacts/litertlm_api_surface_compare_20260516_201159"
  copy_path "artifacts/litertlm_api_surface_compare/20260516_201159/summary.txt" "$bundle_dir/artifacts/litertlm_api_surface_compare_20260516_201159/summary.txt"
  copy_path "artifacts/litertlm_api_surface_compare/20260516_201159/gallery_native_create_engine.txt" "$bundle_dir/artifacts/litertlm_api_surface_compare_20260516_201159/gallery_native_create_engine.txt"
  copy_path "artifacts/litertlm_api_surface_compare/20260516_201159/aar_0.11.0_com_google_ai_edge_litertlm_LiteRtLmJni_javap.txt" "$bundle_dir/artifacts/litertlm_api_surface_compare_20260516_201159/aar_0.11.0_LiteRtLmJni_javap.txt"
  copy_path "artifacts/litertlm_api_surface_compare/20260516_201159/aar_0.10.0_com_google_ai_edge_litertlm_LiteRtLmJni_javap.txt" "$bundle_dir/artifacts/litertlm_api_surface_compare_20260516_201159/aar_0.10.0_LiteRtLmJni_javap.txt"

  mkdir -p "$bundle_dir/artifacts/litertlm_flavor_dependencies_20260516_204821"
  copy_path "artifacts/litertlm_flavor_dependencies/20260516_204821/summary.txt" "$bundle_dir/artifacts/litertlm_flavor_dependencies_20260516_204821/summary.txt"

  mkdir -p "$bundle_dir/artifacts/npu_diagnostics_20260517_005032_customnpu"
  copy_path "artifacts/npu_diagnostics/20260517_005032_customnpu/crash_summary.md" "$bundle_dir/artifacts/npu_diagnostics_20260517_005032_customnpu/crash_summary.md"
  copy_path "artifacts/npu_diagnostics/20260517_005032_customnpu/abort_text_candidates.txt" "$bundle_dir/artifacts/npu_diagnostics_20260517_005032_customnpu/abort_text_candidates.txt"
  copy_path "artifacts/npu_diagnostics/20260517_005032_customnpu/register_ascii_fragments.txt" "$bundle_dir/artifacts/npu_diagnostics_20260517_005032_customnpu/register_ascii_fragments.txt"
  copy_path "artifacts/npu_diagnostics/20260517_005032_customnpu/loaded_libs_matrix.tsv" "$bundle_dir/artifacts/npu_diagnostics_20260517_005032_customnpu/loaded_libs_matrix.tsv"
  copy_path "artifacts/npu_diagnostics/20260517_005032_customnpu/loaded_libs_summary.md" "$bundle_dir/artifacts/npu_diagnostics_20260517_005032_customnpu/loaded_libs_summary.md"
  copy_path "artifacts/npu_diagnostics/20260517_005032_customnpu/native_lib_build_ids.txt" "$bundle_dir/artifacts/npu_diagnostics_20260517_005032_customnpu/native_lib_build_ids.txt"
  copy_path "artifacts/npu_diagnostics/20260517_005032_customnpu/stage_file.txt" "$bundle_dir/artifacts/npu_diagnostics_20260517_005032_customnpu/stage_file.txt"
  copy_path "artifacts/npu_diagnostics/20260517_005032_customnpu/last_stage.txt" "$bundle_dir/artifacts/npu_diagnostics_20260517_005032_customnpu/last_stage.txt"
  copy_path "artifacts/npu_diagnostics/20260517_005032_customnpu/logcat_litert_qnn_extract.txt" "$bundle_dir/artifacts/npu_diagnostics_20260517_005032_customnpu/logcat_litert_qnn_extract.txt"

  mkdir -p "$bundle_dir/artifacts/qairt_qnn_coupling_20260517_012057"
  copy_path "artifacts/qairt_qnn_coupling/20260517_012057/summary.md" "$bundle_dir/artifacts/qairt_qnn_coupling_20260517_012057/summary.md"
  copy_path "artifacts/qairt_qnn_coupling/20260517_012057/qairt_version_summary.md" "$bundle_dir/artifacts/qairt_qnn_coupling_20260517_012057/qairt_version_summary.md"
  copy_path "artifacts/qairt_qnn_coupling/20260517_012057/qnn_lib_matrix.tsv" "$bundle_dir/artifacts/qairt_qnn_coupling_20260517_012057/qnn_lib_matrix.tsv"
  copy_path "artifacts/qairt_qnn_coupling/20260517_012057/needed_matrix.tsv" "$bundle_dir/artifacts/qairt_qnn_coupling_20260517_012057/needed_matrix.tsv"
  copy_path "artifacts/qairt_qnn_coupling/20260517_012057/loaded_libs_matrix.tsv" "$bundle_dir/artifacts/qairt_qnn_coupling_20260517_012057/loaded_libs_matrix.tsv"
  copy_path "artifacts/qairt_qnn_coupling/20260517_012057/custom_apk_packaged_libs.tsv" "$bundle_dir/artifacts/qairt_qnn_coupling_20260517_012057/custom_apk_packaged_libs.tsv"
  copy_path "artifacts/qairt_qnn_coupling/20260517_012057/model_metadata_probe.txt" "$bundle_dir/artifacts/qairt_qnn_coupling_20260517_012057/model_metadata_probe.txt"
  copy_path "artifacts/qairt_qnn_coupling/20260517_012057/context.env" "$bundle_dir/artifacts/qairt_qnn_coupling_20260517_012057/context.env"

  mkdir -p "$bundle_dir/artifacts/litert_qairt246_ref_search_20260517_062055"
  copy_path "artifacts/litert_qairt246_ref_search/20260517_062055/summary.md" "$bundle_dir/artifacts/litert_qairt246_ref_search_20260517_062055/summary.md"
  copy_path "artifacts/litert_qairt246_ref_search/20260517_062055/results.tsv" "$bundle_dir/artifacts/litert_qairt246_ref_search_20260517_062055/results.tsv"
  copy_path "artifacts/litert_qairt246_ref_search/20260517_062055/litert_current_qairt_metadata.txt" "$bundle_dir/artifacts/litert_qairt246_ref_search_20260517_062055/litert_current_qairt_metadata.txt"
  copy_path "artifacts/litert_qairt246_ref_search/20260517_062055/litertlm_main_litert_ref_qairt_metadata.txt" "$bundle_dir/artifacts/litert_qairt246_ref_search_20260517_062055/litertlm_main_litert_ref_qairt_metadata.txt"
  copy_path "artifacts/litert_qairt246_ref_search/20260517_062055/repo_heads.txt" "$bundle_dir/artifacts/litert_qairt246_ref_search_20260517_062055/repo_heads.txt"

  mkdir -p "$bundle_dir/artifacts/qairt244_acquisition_20260517_074537"
  copy_path "artifacts/qairt244_acquisition/20260517_074537/tooling_probe.txt" "$bundle_dir/artifacts/qairt244_acquisition_20260517_074537/tooling_probe.txt"
  copy_path "artifacts/qairt244_acquisition/20260517_074537/existing_sdk_search.txt" "$bundle_dir/artifacts/qairt244_acquisition_20260517_074537/existing_sdk_search.txt"
  copy_path "artifacts/qairt244_acquisition/20260517_074537/qpm_search.txt" "$bundle_dir/artifacts/qairt244_acquisition_20260517_074537/qpm_search.txt"
fi

cat > "$bundle_dir/README.md" <<'EOF'
# LiteRT-LM SM8750 NPU issue artifact bundle

This bundle contains the prepared GitHub issue body and supporting local diagnostics for the isolated LiteRT-LM `Backend.NPU` Engine.initialize dry-run crashes on SM8750.

The latest evidence includes:

- Gallery SM8750 native stack + matching `litertlm-android:0.11.0` Java API still aborts.
- Same-source/tag custom stack from LiteRT-LM v0.11.0 and pinned LiteRT also aborts at dispatch delegate kernel creation.
- QAIRT 2.44 exact SDK is missing locally.
- QPM / Qualcomm tooling was not detected locally.
- No public QAIRT 2.46 source/ref was found in the bounded search.

Safety scope:

- No `Conversation` was created.
- No `Session` was created.
- No `generateResponse` call was made.
- No normal app inference path was wired to `Backend.NPU`.
- The failure is from explicit `Engine.initialize` dry-run only.

Potential privacy note:

- Paths under `/home/sato` are masked to `/home/<user>` in copied text files where practical.
- Android app-private paths and package names are intentionally preserved because they are relevant to the issue.
EOF

cat > "$bundle_dir/summary.md" <<EOF
# Bundle summary

- Created: ${timestamp}
- Mode: ${mode}
- Branch: $(git branch --show-current 2>/dev/null || echo unknown)
- Commit: $(git log -1 --oneline 2>/dev/null || echo unknown)
- Issue body: docs/google_ai_edge_issue_body_litertlm_sm8750_npu.md
- Detailed report: docs/google_ai_edge_issue_report_litertlm_sm8750_npu.md

Key classification:

- primary: no-usable-dispatch-runtime
- likely underlying: dispatch-runtime-compatibility-mismatch or QAIRT/QNN generation mismatch
- confidence: medium

Gallery stack Build IDs:

- liblitertlm_jni.so: 76e4dccd9c5f9cba468d9cae7becfec0
- libLiteRt.so: 869121bd7f4b0b77fa581218117a5c14
- libLiteRtDispatch_Qualcomm.so: 643ad77b8ac2f54bd1b61e4133c77b3a
- libQnnSystem.so: 0d409cdd664b8b0a
- libQnnHtp.so: f2c90c1775a109e1
- libQnnHtpPrepare.so: 9ae62cf17f972404
- libQnnHtpV79Stub.so: 10d7ad6f9195411a

Same-source/tag custom stack Build IDs:

- liblitertlm_jni.so: b78167f717866bbc1d9a981f01fb0334
- libLiteRt.so: a03032ad1eeefda446478aea308c2ed0
- libLiteRtDispatch_Qualcomm.so: e999216e6d32c2f38702cd8538299e7d
- libLiteRtCompilerPlugin_Qualcomm.so: 9053b81d7cbccdc3b5460c5e7395e293
- libGemmaModelConstraintProvider.so: f9e5e73e668032550042319e43012011

Latest blocker:

- exact QAIRT 2.44.0.260225 SDK: not available locally
- QPM / Qualcomm Software Center CLI: not detected locally
- public QAIRT 2.46 LiteRT/LiteRT-LM source/ref: not found in bounded search
EOF

mask_text_files() {
  find "$bundle_dir" -type f \( -name "*.md" -o -name "*.txt" -o -name "*.log" -o -name "*.tsv" \) -print0 |
    while IFS= read -r -d '' file; do
      perl -0pi -e 's#/home/sato#/home/<user>#g' "$file"
    done
}

mask_text_files

if command -v zip >/dev/null 2>&1; then
  (
    cd "$(dirname "$bundle_dir")" || exit 1
    zip -qr "$(basename "$zip_path")" "$(basename "$bundle_dir")"
  )
  echo "zip: $zip_path"
else
  echo "zip: unavailable"
fi

echo "bundle_dir: $bundle_dir"
