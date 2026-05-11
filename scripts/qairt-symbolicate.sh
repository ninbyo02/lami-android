#!/usr/bin/env bash

set +e

TOMBSTONE="${1:-}"
OFFSET_ARG="${2:-}"
LIB_ARG="${3:-${LIBLITERTLM_JNI_SO:-}}"
VERSION="${LITERTLM_VERSION:-0.10.0}"
LIB_NAME="liblitertlm_jni.so"

usage() {
  cat <<'EOF'
Usage:
  scripts/qairt-symbolicate.sh <tombstone> [lib_offset] [liblitertlm_jni.so]

Examples:
  scripts/qairt-symbolicate.sh tombstone.txt 0x27fabc /tmp/liblitertlm_jni.so
  LIBLITERTLM_JNI_SO=/tmp/liblitertlm_jni.so scripts/qairt-symbolicate.sh tombstone.txt 000000000027fabc

If lib_offset is omitted, the script tries to read the first "pc <hex> ... liblitertlm_jni.so"
line from the tombstone. If the .so path is omitted, it tries the Gradle AAR cache.
EOF
}

if [ -z "${TOMBSTONE}" ] || [ ! -f "${TOMBSTONE}" ]; then
  usage
  exit 2
fi

find_tool() {
  local name="$1"
  if command -v "${name}" >/dev/null 2>&1; then
    command -v "${name}"
    return 0
  fi
  find "${ANDROID_HOME:-$HOME/Android/Sdk}" "${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}" \
    -path "*/ndk/*/toolchains/llvm/prebuilt/*/bin/${name}" -type f 2>/dev/null | sort -V | tail -n 1
}

print_ndk_help() {
  cat <<'EOF'
NDK LLVM tools were not found.
Expected paths:
  $ANDROID_HOME/ndk/<version>/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-symbolizer
  $ANDROID_HOME/ndk/<version>/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-addr2line
  $ANDROID_HOME/ndk/<version>/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf
  $ANDROID_HOME/ndk/<version>/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-nm

Install manually if needed:
  sdkmanager "ndk;27.2.12479018"
EOF
}

normalize_offset() {
  local value="$1"
  value="${value#pc}"
  value="${value#0x}"
  value="${value#0X}"
  printf '0x%s\n' "${value}"
}

extract_offset_from_tombstone() {
  awk '/liblitertlm_jni\.so/ && / pc / { for (i = 1; i <= NF; i++) if ($i == "pc") { print $(i + 1); exit } }' "${TOMBSTONE}"
}

find_cached_lib() {
  local gradle_cache="${GRADLE_USER_HOME:-$HOME/.gradle}/caches"
  local aar
  aar="$(find "${gradle_cache}" -name "litertlm-android-${VERSION}.aar" -type f 2>/dev/null | sort | tail -n 1)"
  if [ -z "${aar}" ]; then
    return 1
  fi
  local entry
  entry="$(zipinfo -1 "${aar}" 2>/dev/null | grep "/${LIB_NAME}$" | grep "arm64-v8a" | head -n 1)"
  if [ -z "${entry}" ]; then
    entry="$(zipinfo -1 "${aar}" 2>/dev/null | grep "/${LIB_NAME}$" | head -n 1)"
  fi
  if [ -z "${entry}" ]; then
    return 1
  fi
  local out="${TMPDIR:-/tmp}/${LIB_NAME%.so}-${VERSION}-$$.so"
  unzip -p "${aar}" "${entry}" > "${out}" 2>/dev/null
  if [ ! -s "${out}" ]; then
    return 1
  fi
  echo "${out}"
  echo "AAR=${aar}" >&2
  echo "AAR_ENTRY=${entry}" >&2
}

OFFSET="${OFFSET_ARG:-$(extract_offset_from_tombstone)}"
if [ -z "${OFFSET}" ]; then
  echo "No lib offset provided and none found in tombstone."
  usage
  exit 2
fi
OFFSET="$(normalize_offset "${OFFSET}")"

LIB_PATH="${LIB_ARG}"
if [ -z "${LIB_PATH}" ]; then
  LIB_PATH="$(find_cached_lib)"
fi
if [ -z "${LIB_PATH}" ] || [ ! -f "${LIB_PATH}" ]; then
  echo "Could not locate ${LIB_NAME}."
  echo "Run scripts/qairt-find-litertlm-aar.sh or pass the .so path as argument 3."
  exit 2
fi

LLVM_SYMBOLIZER="$(find_tool llvm-symbolizer)"
ADDR2LINE="$(find_tool llvm-addr2line)"
READELF="$(find_tool llvm-readelf)"
NM="$(find_tool llvm-nm)"

if [ -z "${ADDR2LINE}" ]; then
  ADDR2LINE="$(find_tool addr2line)"
fi
if [ -z "${READELF}" ]; then
  READELF="$(find_tool readelf)"
fi
if [ -z "${NM}" ]; then
  NM="$(find_tool nm)"
fi

echo "== inputs =="
echo "tombstone=${TOMBSTONE}"
echo "lib=${LIB_PATH}"
echo "offset=${OFFSET}"
echo

if [ -z "${LLVM_SYMBOLIZER}" ] && [ -z "${ADDR2LINE}" ] && [ -z "${READELF}" ] && [ -z "${NM}" ]; then
  print_ndk_help
  exit 0
fi

echo "== tools =="
echo "llvm-symbolizer=${LLVM_SYMBOLIZER:-missing}"
echo "addr2line=${ADDR2LINE:-missing}"
echo "readelf=${READELF:-missing}"
echo "nm=${NM:-missing}"
echo

if [ -n "${READELF}" ]; then
  echo "== readelf notes/build-id =="
  "${READELF}" -n "${LIB_PATH}" 2>/dev/null | sed -n '/Build ID/Ip'
  echo
  echo "== readelf file header =="
  "${READELF}" -h "${LIB_PATH}" 2>/dev/null
  echo
fi

if [ -n "${NM}" ]; then
  echo "== nm JNI symbols =="
  "${NM}" -D --defined-only "${LIB_PATH}" 2>/dev/null | grep -E 'Java_|LiteRtLmJni' | head -n 120
  echo
fi

if [ -n "${ADDR2LINE}" ]; then
  echo "== addr2line =="
  "${ADDR2LINE}" -C -f -e "${LIB_PATH}" "${OFFSET}" 2>&1
  echo
fi

if [ -n "${LLVM_SYMBOLIZER}" ]; then
  echo "== llvm-symbolizer =="
  "${LLVM_SYMBOLIZER}" --obj="${LIB_PATH}" "${OFFSET}" 2>&1
  echo
fi
