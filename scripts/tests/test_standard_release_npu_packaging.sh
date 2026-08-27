#!/usr/bin/env bash
set -euo pipefail

apk=${1:?usage: test_standard_release_npu_packaging.sh APK disabled|enabled [SOURCE_DIR]}
mode=${2:?mode must be disabled or enabled}
source_dir=${3:-}
vendor_regex='^(base/)?lib/arm64-v8a/(libQnn.*|libqnn_.*|libLiteRtDispatch_Qualcomm\.so|libLiteRtCompilerPlugin_Qualcomm\.so|libGemmaModelConstraintProvider\.so|liblami_qairt244_npu_jni\.so)$'

test -f "$apk"
case "$mode" in
  disabled)
    if unzip -Z1 "$apk" | grep -E "$vendor_regex"; then
      echo "normal Standard Release contains Qualcomm/NPU vendor runtime" >&2
      exit 1
    fi
    echo "standard_release_vendor_runtime=none"
    ;;
  enabled)
    test -d "$source_dir"
    entries=$(unzip -Z1 "$apk")
    required=(
      liblami_qairt244_npu_jni.so
      libLiteRtDispatch_Qualcomm.so
      libQnnHtpV79Skel.so
      libQnnHtpV79Stub.so
      libQnnSystem.so
    )
    for library in "${required[@]}"; do
      entry="lib/arm64-v8a/$library"
      if ! grep -Fxq "$entry" <<<"$entries"; then
        entry="base/$entry"
      fi
      grep -Fxq "$entry" <<<"$entries"
      source_hash=$(sha256sum "$source_dir/$library" | cut -d' ' -f1)
      apk_hash=$(unzip -p "$apk" "$entry" | sha256sum | cut -d' ' -f1)
      if [[ "$source_hash" != "$apk_hash" ]]; then
        echo "runtime hash mismatch: $library" >&2
        echo "source=$source_hash apk=$apk_hash" >&2
        exit 1
      fi
      echo "$library=$apk_hash"
    done
    echo "standard_release_npu_runtime=verified"
    ;;
  *)
    echo "mode must be disabled or enabled" >&2
    exit 2
    ;;
esac
