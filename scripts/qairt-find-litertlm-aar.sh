#!/usr/bin/env bash

set +e

VERSION="${LITERTLM_VERSION:-0.10.0}"
GRADLE_CACHE="${GRADLE_USER_HOME:-$HOME/.gradle}/caches"
PATTERN="litertlm-android-${VERSION}.aar"

echo "== LiteRT-LM AAR cache search =="
echo "gradle_cache=${GRADLE_CACHE}"
echo "pattern=${PATTERN}"
echo

AARS="$(find "${GRADLE_CACHE}" -name "${PATTERN}" -type f 2>/dev/null | sort)"
if [ -z "${AARS}" ]; then
  echo "AAR not found"
  exit 0
fi

printf '%s\n' "${AARS}" | while IFS= read -r aar; do
  echo "== AAR =="
  echo "path=${aar}"
  echo "size=$(wc -c < "${aar}" 2>/dev/null)"
  echo "sha256=$(sha256sum "${aar}" 2>/dev/null | awk '{print $1}')"
  echo "-- liblitertlm_jni.so entries --"
  zipinfo -l "${aar}" 2>/dev/null | grep 'liblitertlm_jni\.so' || true
  echo
done
