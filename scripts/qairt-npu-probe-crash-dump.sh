#!/usr/bin/env bash

set +e

APP_ID="io.github.ninbyo02.lami"
PROBE_ACTIVITY="io.github.ninbyo02.lami.debug.QairtNpuProbeActivity"
PROBE_PROCESS="io.github.ninbyo02.lami:qairt_npu_probe"
WAIT_SECONDS="${WAIT_SECONDS:-35}"

echo "== qairt npu probe crash dump =="
echo "started_at=$(date -Is 2>/dev/null || date)"
echo "wait_seconds=${WAIT_SECONDS}"
echo

echo "== start activity =="
adb shell am start -n "${APP_ID}/${PROBE_ACTIVITY}"
echo

echo "== wait =="
sleep "${WAIT_SECONDS}"
echo

echo "== app files result =="
adb shell run-as "${APP_ID}" cat files/qairt_npu_probe_result.txt
echo

echo "== app last run =="
adb shell run-as "${APP_ID}" cat files/qairt_npu_probe_last_run.txt
echo

echo "== pidof probe process =="
adb shell pidof "${PROBE_PROCESS}"
echo

echo "== tombstones list =="
adb shell ls -lt /data/tombstones 2>/dev/null
echo "tombstones_status=$?"
echo

echo "== dropbox native crash tail =="
adb shell dumpsys dropbox --print data_app_native_crash 2>/dev/null | tail -n 160
echo "dropbox_status=${PIPESTATUS[0]}"
echo

echo "finished_at=$(date -Is 2>/dev/null || date)"
