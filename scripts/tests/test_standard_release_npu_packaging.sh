#!/usr/bin/env bash
set -euo pipefail

apk=${1:?usage: test_standard_release_npu_packaging.sh APK disabled|enabled [SOURCE_DIR]}
mode=${2:?mode must be disabled or enabled}
source_dir=${3:-}
vendor_regex='^(base/)?lib/arm64-v8a/(libQnn.*|libqnn_.*|libLiteRtDispatch_Qualcomm\.so|libLiteRtCompilerPlugin_Qualcomm\.so|libGemmaModelConstraintProvider\.so|liblami_qairt244_npu_jni\.so)$'

test -f "$apk"

assert_cdsprpc_visibility() {
  case "$apk" in
    *.apk)
      analyzer=${APK_ANALYZER:-}
      if [[ -z "$analyzer" ]]; then
        analyzer=$(command -v apkanalyzer || true)
      fi
      if [[ -z "$analyzer" || ! -x "$analyzer" ]]; then
        echo "apkanalyzer is required to verify Standard APK native-library visibility" >&2
        exit 1
      fi
      manifest=$("$analyzer" manifest print "$apk")
      declared=false
      if tr '\n' ' ' <<<"$manifest" \
        | grep -Eq '<uses-native-library[^>]*android:name="libcdsprpc\.so"[^>]*android:required="false"'; then
        declared=true
      fi
      if [[ "$mode" == "enabled" && "$declared" != "true" ]]; then
        echo "enabled Standard NPU candidate must request optional libcdsprpc.so visibility" >&2
        exit 1
      fi
      if [[ "$mode" == "disabled" && "$declared" == "true" ]]; then
        echo "normal Standard Release must not request vendor libcdsprpc.so visibility" >&2
        exit 1
      fi
      extracted=false
      if grep -Fq 'android:extractNativeLibs="true"' <<<"$manifest"; then
        extracted=true
      fi
      if [[ "$mode" == "enabled" && "$extracted" != "true" ]]; then
        echo "enabled Standard NPU candidate must extract its nativeLibraryDir" >&2
        exit 1
      fi
      echo "standard_release_cdsprpc_visibility=$declared"
      echo "standard_release_native_extraction=$extracted"
      ;;
  esac
}

assert_cdsprpc_visibility

case "$mode" in
  disabled)
    if unzip -Z1 "$apk" | grep -E "$vendor_regex"; then
      echo "normal Standard Release contains Qualcomm/NPU vendor runtime" >&2
      exit 1
    fi
    if [[ -n "$source_dir" && -f "$source_dir/libLiteRt.so" ]]; then
      entry="lib/arm64-v8a/libLiteRt.so"
      entries=$(unzip -Z1 "$apk")
      if ! grep -Fxq "$entry" <<<"$entries"; then
        entry="base/$entry"
      fi
      grep -Fxq "$entry" <<<"$entries"
      candidate_hash=$(sha256sum "$source_dir/libLiteRt.so" | cut -d' ' -f1)
      packaged_hash=$(unzip -p "$apk" "$entry" | sha256sum | cut -d' ' -f1)
      if [[ "$candidate_hash" == "$packaged_hash" ]]; then
        echo "normal Standard Release contains stale custom LiteRT core from an enabled candidate build" >&2
        exit 1
      fi
      echo "standard_release_custom_litert_core=none"
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

    if ! readelf -Ws "$source_dir/libLiteRtDispatch_Qualcomm.so"       | grep -Eq 'GLOBAL.*DEFAULT.*LiteRtDispatchGetApi'; then
      echo "Qualcomm Dispatch runtime does not export LiteRtDispatchGetApi" >&2
      exit 1
    fi
    system_dependency_regex='^(lib(android|c|dl|EGL|GLESv2|GLESv3|log|m)\.so|libc\+\+\.so\.1|libc\+\+abi\.so\.1|libcdsprpc\.so)$'
    for source_library in "$source_dir"/*.so; do
      library=$(basename "$source_library")
      case "$library" in
        liblami_qairt244_smoke.so|liblitertlm_jni.so) continue ;;
      esac
      for dependency in $(readelf -d "$source_library" 2>/dev/null         | sed -n 's/.*Shared library: \[\(.*\)\].*/\1/p'); do
        if [[ "$dependency" =~ $system_dependency_regex ]]; then
          continue
        fi
        if [[ ! -f "$source_dir/$dependency" ]]; then
          echo "unresolved native dependency: $library -> $dependency" >&2
          exit 1
        fi
        if ! grep -Eq "^(base/)?lib/arm64-v8a/$dependency$" <<<"$entries"; then
          echo "unpackaged native dependency: $library -> $dependency" >&2
          exit 1
        fi
      done
    done
    echo "standard_release_native_dependency_closure=verified"
    echo "standard_release_dispatch_get_api_export=verified"
    echo "standard_release_npu_runtime=verified"
    ;;
  *)
    echo "mode must be disabled or enabled" >&2
    exit 2
    ;;
esac
