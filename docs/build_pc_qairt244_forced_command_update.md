# Build PC live forced-command update for qairt244 hooks

この手順は、Build PC上でユーザーが実行するためのコピペ用です。

目的:

- live forced-command本体 `/home/lami-build/lami-build-control/remote_control.sh` はrepo外に残す
- qairt244の処理本体はrepo管理の `scripts/lami_build_qairt244_forced_commands.sh` に置く
- live本体には最小の `source` と `case` 分岐だけ追加する
- 既存の `status` / `build-branch` / `test-branch` / `git-*` / `install-future` を消さない

## 1. Build PCで実行するコピペコマンド

Build PCに通常ログインできる端末で実行してください。

```bash
set -euo pipefail

REPO="$HOME/repos/lami-android"
CONTROL_DIR="$HOME/lami-build-control"
LIVE="$CONTROL_DIR/remote_control.sh"
BACKUP="$CONTROL_DIR/remote_control.sh.bak.$(date +%Y%m%d_%H%M%S)"

cd "$REPO"
git fetch origin
git checkout future
git pull --ff-only origin future

test -f "$REPO/scripts/lami_build_qairt244_forced_commands.sh"
test -f "$LIVE"

cp -a "$LIVE" "$BACKUP"
echo "backup=$BACKUP"

python3 - <<'PY'
from pathlib import Path
import os

repo = Path(os.environ["HOME"]) / "repos/lami-android"
live = Path(os.environ["HOME"]) / "lami-build-control/remote_control.sh"
text = live.read_text()

source_line = 'source "$REPO/scripts/lami_build_qairt244_forced_commands.sh"'
if source_line not in text:
    marker = 'case "$CMD" in\n'
    if marker not in text:
        raise SystemExit('ERROR: case "$CMD" in not found in live remote_control.sh')
    text = text.replace(marker, source_line + '\n\n' + marker, 1)

case_block = '''  qairt244-artifacts|stage-qairt244-custom-jni*)
    lami_qairt244_dispatch "$CMD"
    ;;
  build-qairt244-custom-jni)
    lami_qairt244_dispatch "$CMD"
    ;;
'''
if 'qairt244-artifacts|stage-qairt244-custom-jni*)' not in text:
    marker = 'case "$CMD" in\n'
    if marker not in text:
        raise SystemExit('ERROR: case "$CMD" in not found while inserting qairt244 cases')
    text = text.replace(marker, marker + case_block, 1)

help_lines = '''  qairt244-artifacts
  stage-qairt244-custom-jni [artifact-dir-basename]
  build-qairt244-custom-jni
  qairt244-sdk-status
'''
if 'stage-qairt244-custom-jni [artifact-dir-basename]' not in text:
    # Prefer inserting near adb-devices in the static help text if present.
    if '  adb-devices\n' in text:
        text = text.replace('  adb-devices\n', '  adb-devices\n' + help_lines, 1)
    elif 'allowed commands:' in text:
        text = text.replace('allowed commands:\n', 'allowed commands:\n' + help_lines, 1)
    else:
        print('WARNING: help text marker not found; command will work but help may not list qairt244 hooks')

live.write_text(text)
PY

bash -n "$LIVE"
bash -n "$REPO/scripts/lami_build_qairt244_forced_commands.sh"

echo "--- qairt244 lines in live controller ---"
grep -nE 'qairt244|lami_build_qairt244' "$LIVE" || true

echo "OK: live remote_control.sh updated"
```

## 2. Hermes側から確認するコマンド

更新後、Hermes/Telegram側から以下を実行して確認します。

```bash
ssh -i /opt/data/.ssh/lami_build_pc_ed25519 -p 2222 lami-build@192.168.52.99 help
ssh -i /opt/data/.ssh/lami_build_pc_ed25519 -p 2222 lami-build@192.168.52.99 qairt244-artifacts
```

`help` に以下が出れば反映済みです。

```text
qairt244-artifacts
stage-qairt244-custom-jni [artifact-dir-basename]
build-qairt244-custom-jni
```

## 3. 既存artifactがある場合

```bash
ssh -i /opt/data/.ssh/lami_build_pc_ed25519 -p 2222 lami-build@192.168.52.99 stage-qairt244-custom-jni
```

またはartifact名を指定:

```bash
ssh -i /opt/data/.ssh/lami_build_pc_ed25519 -p 2222 lami-build@192.168.52.99 'stage-qairt244-custom-jni 20260524_114833_qairt244_16token'
```

## 4. artifactが無い場合

時間がかかりますが、専用コマンドで再ビルドできます。

```bash
ssh -i /opt/data/.ssh/lami_build_pc_ed25519 -p 2222 lami-build@192.168.52.99 build-qairt244-custom-jni
```

このコマンドは以下の候補パスから存在するものを自動選択します。LiteRT-LM checkout が無い場合は、固定URL `https://github.com/google-ai-edge/LiteRT-LM.git` から `$HOME/project/litert-custom-build/LiteRT-LM` へcloneし、`v0.11.0` をcheckoutして `patches/qairt244_litertlm_utf8_128token.patch` を適用します。

```text
LiteRT-LM checkout candidates:
  $HOME/project/litert-custom-build/LiteRT-LM
  /home/sato/project/litert-custom-build/LiteRT-LM
  /home/lami-build/project/litert-custom-build/LiteRT-LM

QAIRT SDK candidates:
  $HOME/compose/qairt/workspace/sdk/qairt/2.44.0.260225
  /home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225
  /home/lami-build/compose/qairt/workspace/sdk/qairt/2.44.0.260225

output root:
  $HOME/repos/lami-android/artifacts/litert_custom_build/<timestamp>_qairt244_128token_utf8prompt
```

QAIRT SDKは自動downloadしません。`2.44.0.260225` が候補パスに無い場合は、Qualcomm SDKの配置が別途必要です。

## 5. ロールバック

何か問題があった場合は、手順1で表示されたbackupを戻してください。

```bash
cp -a "$HOME/lami-build-control/remote_control.sh.bak.YYYYMMDD_HHMMSS" "$HOME/lami-build-control/remote_control.sh"
bash -n "$HOME/lami-build-control/remote_control.sh"
```
