#!/bin/bash
set -euo pipefail

# ==============================================================================
# update.sh (single-dev safe mode)
#
# Design philosophy:
#   - Safe by default: dirty worktree では commit / stash / reset せずに止まる
#   - Explicit escape hatches: --stash / --wip 指定時のみ自動退避する
#   - Reproducible: 動いていたPR/commitを即テスト可能
#
# ==============================================================================
# ===== Config =====
WORK_BRANCH=future
REMOTE_NAME="origin"
WIP_PREFIX="wip(auto):"
WIP_MESSAGE="${WIP_PREFIX} before update.sh"
STASH_MESSAGE="stash(auto): before update.sh"
DEFAULT_PHONE_HOST="10.5.5.3"
PHONE_IP="$DEFAULT_PHONE_HOST" # Backward-compatible alias for older local notes.
ALLOWED_PHONE_HOSTS=("10.5.5.3" "192.168.52.52")
DEFAULT_PORT="40215"
DEFAULT_ANDROID_FLAVOR="standard"
VERBOSE=0
CODEX_GUIDE_LINE="────────────────────────"
CODEX_RECOMMENDED_BRANCH="work/oldest-buildable-good"

# ===== Logging =====
info() { echo "$*"; }
ok()   { echo "✅ $*"; }
warn() { echo "⚠️  $*" >&2; }
die()  { echo "❌ $*" >&2; exit 1; }

usage() {
  cat <<'EOF'
update.sh (single-dev safe mode)

Design philosophy:
  - Safe by default: dirty worktree では commit / stash / reset せずに止まる
  - Explicit escape hatches: --stash / --wip 指定時のみ自動退避する
  - Reproducible: 動いていたPR/commitを即テスト可能

Local update policy:
  - update        : clean worktree の場合のみ ff-only 更新（dirty なら停止）
  - update --stash: git stash push --include-untracked で退避し、更新後に stash pop
  - update --wip  : legacy explicit mode。WIP commit 後に更新（default では使わない）

Usage:
  ./update.sh                      # show help

Subcommands:
  ./update.sh update [options]      # fetch/pull WORK_BRANCH, then build+install
  ./update.sh wip                   # explicitly create local WIP commit if dirty
  ./update.sh stash                 # explicitly stash current changes if dirty
  ./update.sh publish [options]     # commit current changes, then push current branch
  ./update.sh switch [options]      # create/switch branch from PR or commit (optionally push & update WORK_BRANCH)
  ./update.sh test   [options]      # build/install test on temp branch (no detached), then return & cleanup
  ./update.sh here-install [options]# build+install current branch (no pull)
  ./update.sh promote [options]     # merge current branch into main, build, push, then install stable(main)

update options:
  --host HOST          ADB connect host (allowed: 10.5.5.3, 192.168.52.52; default: 10.5.5.3)
  --port|-p PORT        ADB connect port (default: 40215)
  --flavor NAME         Android flavor to install: standard, npuExperiment, galleryStackExperiment, galleryAlignedNpuProbe, or customBuildExperiment (default: standard)
  --clean-install|-c    uninstall selected flavor before its install task
  --stash               stash dirty worktree before update, then stash pop after update
  --wip                 legacy explicit mode: create local WIP commit before update
  --no-wip              compatibility alias for default safe behavior
  --dry-run             stop after fetch/pull (no gradle, no adb)
  --verbose|-v          show verbose logs (adb devices -l, etc.)

Allowed hosts:
  10.5.5.3       default VPN/local debugging host
  192.168.52.52  LAN wireless debugging host

publish options:
  --message|-m MSG      commit message for current changes (required when dirty)
  --remote NAME         remote to push to (default: origin)
  --branch NAME         remote branch name (default: current branch)
  --no-push             commit only; do not push
  --dry-run             show intended commit/push without changing anything
  --no-verify           pass --no-verify to git commit

test options:
  --pr N | --commit SHA
  --build               run selected flavor Kotlin compile task (default if neither --build nor --install)
  --install             run selected flavor install task (device required)
  --flavor NAME         Android flavor to install: standard, npuExperiment, galleryStackExperiment, galleryAlignedNpuProbe, or customBuildExperiment (default: standard)
  --clean-install|-c    uninstall selected flavor before install task (requires --install)
  --port|-p PORT        ADB connect port (default: 40215)
  --keep-temp           keep temp branch after test (default: delete)
  --verbose|-v          show verbose logs (adb devices -l, etc.)

dirty-worktree compatibility options:
  --wip                 legacy explicit mode: create local WIP commit first
  --no-wip              compatibility alias for default safe behavior

Examples:
  ./update.sh update --dry-run
  ./update.sh update --stash
  ./update.sh update --wip
  ./update.sh update --flavor npuExperiment
  ./update.sh update --flavor galleryStackExperiment
  ./update.sh update --flavor galleryAlignedNpuProbe
  ./update.sh update --flavor customBuildExperiment
  ./update.sh wip
  ./update.sh stash
  ./update.sh publish -m "docs: update README"
  ./update.sh publish --dry-run
  ./update.sh test --pr 398 --install -c -v
  ./update.sh here-install -p 42951
  ./update.sh here-install --flavor npuExperiment -p 42951
  ./update.sh promote --install -p 42951

EOF
  exit 0
}

# ------------------------------------------------------------------------------
# Helpers
# ------------------------------------------------------------------------------

require_cmd() {
  local c="$1"
  command -v "$c" >/dev/null 2>&1 || die "Required command not found: $c"
}

ensure_git_repo() {
  git rev-parse --is-inside-work-tree >/dev/null 2>&1 || die "Not a git repository."
}

resolve_adb_install_options() {
  local install_options=""
  if [[ -z "${LAMI_ADB_INSTALL_OPTIONS+x}" ]]; then
    install_options="--no-streaming"
  else
    install_options="${LAMI_ADB_INSTALL_OPTIONS}"
  fi
  echo "$install_options"
}

run_install_debug() {
  local flavor="${1:-$DEFAULT_ANDROID_FLAVOR}"
  local install_options=""
  local task
  task="$(install_task_for_flavor "$flavor")"
  install_options="$(resolve_adb_install_options)"
  if [[ -n "$install_options" ]]; then
    ./gradlew ":app:${task}" "-Pandroid.injected.adb.installOptions=${install_options}"
  else
    ./gradlew ":app:${task}"
  fi
}

normalize_android_flavor() {
  local flavor="${1:-$DEFAULT_ANDROID_FLAVOR}"
  case "$flavor" in
    standard|Standard) echo "standard" ;;
    npuExperiment|NpuExperiment|npu|npu-experiment) echo "npuExperiment" ;;
    galleryStackExperiment|GalleryStackExperiment|gallery|gallery-stack|gallery-stack-experiment|gallerynpu) echo "galleryStackExperiment" ;;
    galleryAlignedNpuProbe|GalleryAlignedNpuProbe|gallery-aligned|gallery-aligned-npu-probe|galleryprobe) echo "galleryAlignedNpuProbe" ;;
    customBuildExperiment|CustomBuildExperiment|custom|custom-build|custom-build-experiment|customnpu) echo "customBuildExperiment" ;;
    trueEngineNpuProbe|TrueEngineNpuProbe|true-engine|true-engine-npu-probe|trueengineprobe) echo "trueEngineNpuProbe" ;;
    *) die "Unknown Android flavor: $flavor (expected: standard, npuExperiment, galleryStackExperiment, galleryAlignedNpuProbe, customBuildExperiment, or trueEngineNpuProbe)" ;;
  esac
}

install_task_for_flavor() {
  local flavor
  flavor="$(normalize_android_flavor "$1")"
  case "$flavor" in
    standard) echo "installStandardDebug" ;;
    npuExperiment) echo "installNpuExperimentDebug" ;;
    galleryStackExperiment) echo "installGalleryStackExperimentDebug" ;;
    galleryAlignedNpuProbe) echo "installGalleryAlignedNpuProbeDebug" ;;
    customBuildExperiment) echo "installCustomBuildExperimentDebug" ;;
    trueEngineNpuProbe) echo "installTrueEngineNpuProbeDebug" ;;
  esac
}

compile_task_for_flavor() {
  local flavor
  flavor="$(normalize_android_flavor "$1")"
  case "$flavor" in
    standard) echo "compileDebugKotlin" ;;
    npuExperiment) echo "compileNpuExperimentDebugKotlin" ;;
    galleryStackExperiment) echo "compileGalleryStackExperimentDebugKotlin" ;;
    galleryAlignedNpuProbe) echo "compileGalleryAlignedNpuProbeDebugKotlin" ;;
    customBuildExperiment) echo "compileCustomBuildExperimentDebugKotlin" ;;
    trueEngineNpuProbe) echo "compileTrueEngineNpuProbeDebugKotlin" ;;
  esac
}

resolve_app_id_for_flavor() {
  local flavor
  flavor="$(normalize_android_flavor "$1")"
  # Keep these explicit after introducing flavors; manifest scraping of generic debug
  # outputs can pick the wrong variant and make clean installs uninstall the wrong app.
  case "$flavor" in
    standard) echo "io.github.ninbyo02.lami" ;;
    npuExperiment) echo "io.github.ninbyo02.lami.npu" ;;
    galleryStackExperiment) echo "io.github.ninbyo02.lami.gallerynpu" ;;
    galleryAlignedNpuProbe) echo "io.github.ninbyo02.lami.galleryprobe" ;;
    customBuildExperiment) echo "io.github.ninbyo02.lami.customnpu" ;;
    trueEngineNpuProbe) echo "io.github.ninbyo02.lami.trueengineprobe" ;;
  esac
}

# ★追加: 実行時にHEADコミット情報を毎回表示
print_head_commit() {
  # repo外なら何もしない（安全）
  git rev-parse --is-inside-work-tree >/dev/null 2>&1 || return 0

  local b sha subject datetime upstream ahead behind
  b="$(git symbolic-ref --short HEAD 2>/dev/null || echo "(detached)")"
  sha="$(git rev-parse --short HEAD 2>/dev/null || echo "???????")"
  subject="$(git log -1 --pretty=%s 2>/dev/null || echo "(no commit)")"
  datetime="$(git log -1 --date=iso --pretty=%cd 2>/dev/null || echo "")"
  upstream="$(git rev-parse --abbrev-ref --symbolic-full-name "@{upstream}" 2>/dev/null || true)"
  if [[ -n "$upstream" ]]; then
    read -r ahead behind < <(git rev-list --left-right --count "HEAD...@{upstream}" 2>/dev/null || echo "0 0")
  else
    upstream="(none)"
    ahead="0"
    behind="0"
  fi

  info "== git HEAD =="
  info "branch: ${b}"
  info "commit: ${sha} ${datetime}"
  info "msg   : ${subject}"
  info "upstream: ${upstream}"
  info "ahead/behind: ${ahead:-0}/${behind:-0}"

  if is_worktree_dirty; then
    warn "worktree: DIRTY (has uncommitted changes)"
  else
    info "worktree: clean"
  fi
  info "========="

  print_codex_branch_guidance
}

is_worktree_dirty() {
  [[ -n "$(git status --porcelain 2>/dev/null || true)" ]]
}

print_codex_branch_guidance() {
  command -v git >/dev/null 2>&1 || return 0
  git rev-parse --is-inside-work-tree >/dev/null 2>&1 || return 0

  local current_branch=""
  current_branch="$(git symbolic-ref --quiet --short HEAD || true)"

  local upstream="(none)"
  local upstream_set=0
  if git rev-parse --abbrev-ref --symbolic-full-name "@{upstream}" >/dev/null 2>&1; then
    upstream_set=1
    upstream="$(git rev-parse --abbrev-ref --symbolic-full-name "@{upstream}" 2>/dev/null || true)"
  fi

  local AHEAD="0"
  local BEHIND="0"
  if [[ "$upstream_set" -eq 1 ]]; then
    read -r AHEAD BEHIND < <(
      git rev-list --left-right --count HEAD...@{upstream} 2>/dev/null
    )
  fi
  AHEAD="${AHEAD:-0}"
  BEHIND="${BEHIND:-0}"
  local AHEAD_BEHIND_DISPLAY="${AHEAD}/${BEHIND}"

  local worktree_status="clean"
  if is_worktree_dirty; then
    worktree_status="dirty"
  fi

  if [[ "$current_branch" == "$CODEX_RECOMMENDED_BRANCH" && "$worktree_status" == "clean" && "$upstream_set" -eq 1 && "$AHEAD" -eq 0 && "$BEHIND" -eq 0 ]]; then
    echo "$CODEX_GUIDE_LINE"
    echo "🤖 Codex 推奨ブランチ"
    echo "  → ${CODEX_RECOMMENDED_BRANCH}"
    echo "  （理由: worktree clean / upstream差分 ${AHEAD_BEHIND_DISPLAY}）"
    echo "$CODEX_GUIDE_LINE"
    return 0
  fi

  echo "⚠️ Codexでの作業は非推奨"
  echo "  - worktree: ${worktree_status}"
  echo "  - upstream: ${upstream}"
  echo "  - ahead/behind: ${AHEAD_BEHIND_DISPLAY}"
  echo "  - current: ${current_branch}"
}

current_branch_or_die() {
  local b=""
  b="$(git symbolic-ref --short HEAD 2>/dev/null || true)"
  [[ -n "$b" ]] || die "Detached HEAD. Please switch to a branch first."
  echo "$b"
}

branch_exists_local() {
  local b="$1"
  git show-ref --verify --quiet "refs/heads/$b"
}

branch_exists_remote() {
  local b="$1"
  git show-ref --verify --quiet "refs/remotes/${REMOTE_NAME}/$b"
}

print_publish_status() {
  local remote="$1"
  local branch="$2"
  local remote_ref="refs/remotes/${remote}/${branch}"

  info "== publish status =="
  info "target: ${remote}/${branch}"

  if git show-ref --verify --quiet "$remote_ref"; then
    local ahead="0"
    local behind="0"
    read -r ahead behind < <(
      git rev-list --left-right --count "HEAD...${remote_ref}" 2>/dev/null
    )
    ahead="${ahead:-0}"
    behind="${behind:-0}"
    info "ahead/behind: ${ahead}/${behind}"
  else
    warn "remote branch not found yet: ${remote}/${branch}"
  fi

  if [[ -n "$(git status --porcelain)" ]]; then
    warn "worktree: dirty"
    git status --short
  else
    info "worktree: clean"
  fi
  info "===================="
}

# Resolve PR number to commit hash by searching commit message containing "#<PR>"
# (works for messages like "... (#398)" or "... #398")
resolve_pr_to_commit() {
  local pr="$1"
  local sha=""
  sha="$(git log --grep "#${pr}\b" -n 1 --format=%H || true)"
  [[ -n "$sha" ]] || die "Could not resolve PR #$pr. Try: git log --grep \"#${pr}\""
  echo "$sha"
}

set_work_branch_in_script() {
  local new_branch="$1"
  local self="$0"
  [[ -f "$self" ]] || die "Script file not found: $self"
  sed -i -E 's|^([[:space:]]*WORK_BRANCH=)".*"|\1"'"$new_branch"'"|' "$self"
  ok "Updated WORK_BRANCH in script: $new_branch"
}

validate_phone_host() {
  local host="$1"
  [[ -n "$host" ]] || die "ADB host is required."
  [[ "$host" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]] || die "ADB host must be an IPv4 address: $host"
  local allowed
  for allowed in "${ALLOWED_PHONE_HOSTS[@]}"; do
    if [[ "$host" == "$allowed" ]]; then
      echo "$host"
      return 0
    fi
  done
  die "ADB host not allowed: $host (allowed: ${ALLOWED_PHONE_HOSTS[*]})"
}

validate_adb_port() {
  local port="$1"
  [[ "$port" =~ ^[0-9]{1,5}$ ]] || die "ADB port must be numeric: $port"
  (( port >= 1 && port <= 65535 )) || die "ADB port out of range: $port"
  echo "$port"
}

adb_connect_and_count() {
  local phone_host
  local port
  if [[ $# -eq 1 ]]; then
    phone_host="$DEFAULT_PHONE_HOST"
    port="$1"
  else
    phone_host="$1"
    port="$2"
  fi
  info "Checking connected devices..." >&2
  adb connect "${phone_host}:${port}" >/dev/null 2>&1 || true

  if [[ "${VERBOSE}" -eq 1 ]]; then
    adb devices -l >&2 || adb devices >&2 || true
  else
    # device list suppressed (use --verbose to show)
    :
  fi

  local cnt
  cnt="$(adb devices | awk 'NR>1 && $2=="device"{c++} END{print c+0}')"
  [[ "$cnt" =~ ^[0-9]+$ ]] || { echo "❌ Device count is not numeric: $cnt" >&2; return 1; }
  echo "$cnt"
}

resolve_app_id() {
  # 1) build.gradle(.kts)
  if [[ -f app/build.gradle.kts ]]; then
    local id
    id=$(grep -E 'applicationId\s*=' -m1 app/build.gradle.kts | sed -E 's/.*applicationId\s*=\s*"([^"]+)".*/\1/' || true)
    if [[ -n "${id:-}" && "$id" != *applicationId* ]]; then
      echo "$id"; return 0
    fi
  fi
  if [[ -f app/build.gradle ]]; then
    local id
    id=$(grep -E 'applicationId\s+"[^"]+"' -m1 app/build.gradle | sed -E 's/.*applicationId\s+"([^"]+)".*/\1/' || true)
    if [[ -n "${id:-}" && "$id" != *applicationId* ]]; then
      echo "$id"; return 0
    fi
  fi

  # 2) merged manifest
  ./gradlew :app:processDebugMainManifest >/dev/null 2>&1 || ./gradlew :app:processDebugManifest >/dev/null 2>&1 || true

  local mf=""
  for p in \
    "app/build/intermediates/merged_manifest/debug/AndroidManifest.xml" \
    "app/build/intermediates/merged_manifests/debug/AndroidManifest.xml"
  do
    [[ -f "$p" ]] && mf="$p" && break
  done

  if [[ -n "$mf" ]]; then
    local id
    id=$(grep -Eo 'package="[^"]+"' -m1 "$mf" | sed -E 's/package="([^"]+)"/\1/' || true)
    if [[ -n "${id:-}" ]]; then
      echo "$id"; return 0
    fi
  fi

  # 3) apk (requires aapt)
  ./gradlew :app:assembleDebug >/dev/null 2>&1 || true
  local apk="app/build/outputs/apk/debug/app-debug.apk"
  if [[ -f "$apk" ]] && command -v aapt >/dev/null 2>&1; then
    local id
    id=$(aapt dump badging "$apk" | sed -n "s/package: name='\([^']*\)'.*/\1/p" | head -n1 || true)
    if [[ -n "${id:-}" ]]; then
      echo "$id"; return 0
    fi
  fi
  return 1
}

do_clean_uninstall_if_requested() {
  local clean="$1"
  local flavor="${2:-$DEFAULT_ANDROID_FLAVOR}"
  if [[ "$clean" -ne 1 ]]; then return 0; fi

  info "Resolving applicationId..."
  local app_id=""
  if app_id="$(resolve_app_id_for_flavor "$flavor")"; then
    ok "applicationId: $app_id"
    info "Uninstalling $app_id ..."
    adb uninstall "$app_id" >/dev/null 2>&1 || true
  elif app_id="$(resolve_app_id)"; then
    ok "applicationId: $app_id"
    info "Uninstalling $app_id ..."
    adb uninstall "$app_id" >/dev/null 2>&1 || true
  else
    die "Could not resolve applicationId (clean install requested)."
  fi
}

print_dirty_summary() {
  git status -sb || true

  local unstaged_stat=""
  local staged_stat=""
  unstaged_stat="$(git diff --stat 2>/dev/null || true)"
  staged_stat="$(git diff --cached --stat 2>/dev/null || true)"

  if [[ -n "$unstaged_stat" || -n "$staged_stat" ]]; then
    echo ""
    info "== git diff --stat =="
    [[ -n "$staged_stat" ]] && git diff --cached --stat || true
    [[ -n "$unstaged_stat" ]] && git diff --stat || true
    info "======================="
  fi
}

abort_dirty_update() {
  warn "Working tree is dirty. update.sh will not commit, stash, or reset automatically."
  print_dirty_summary
  cat <<'EOF'

変更があるため、自動更新しません。
次のいずれかを選んでください:
  ./update.sh update --stash
  ./update.sh update --wip
  手動で commit / stash / reset してから ./update.sh update を再実行
EOF
  exit 1
}

handle_dirty_wip_mode() {
  local command_name="$1"
  local dirty_mode="$2"
  local auto_wip_commit_var="$3" # name of variable to set (bash indirection)

  printf -v "$auto_wip_commit_var" "0"
  if ! is_worktree_dirty; then
    return 0
  fi

  if [[ "$dirty_mode" == "wip" ]]; then
    create_wip_commit_if_dirty
    printf -v "$auto_wip_commit_var" "1"
    return 0
  fi

  warn "Working tree is dirty. ${command_name} will not create a WIP commit by default."
  print_dirty_summary
  cat <<EOF

変更があるため、自動実行しません。
次のいずれかを選んでください:
  ./update.sh ${command_name} --wip
  手動で commit / stash / reset してから再実行
EOF
  exit 1
}

create_wip_commit_if_dirty() {
  if ! is_worktree_dirty; then
    info "worktree is clean; no WIP commit created."
    return 0
  fi

  warn "legacy / explicit mode: creating local WIP commit."
  warn "Default update does not create WIP commits; prefer a normal commit or --stash for temporary local work."
  print_dirty_summary
  info "Creating local WIP commit (no push): ${WIP_MESSAGE}"
  git add -A
  git commit -m "${WIP_MESSAGE}" >/dev/null
  ok "local WIP commit created"
}

stash_current_changes_if_dirty() {
  local stash_created_var="$1" # name of variable to set (bash indirection)

  if ! is_worktree_dirty; then
    info "worktree is clean; no stash created."
    printf -v "$stash_created_var" "0"
    return 0
  fi

  warn "Stashing dirty worktree before update."
  print_dirty_summary
  # Include untracked files so update does not overwrite local new files. Ignored
  # files are intentionally left in place; review them manually if they matter.
  git stash push --include-untracked -m "${STASH_MESSAGE}"
  printf -v "$stash_created_var" "1"
}

pop_update_stash_if_needed() {
  local stash_created="$1"
  [[ "$stash_created" -eq 1 ]] || return 0

  info "Restoring stashed changes..."
  if git stash pop; then
    ok "stash pop completed"
    return 0
  fi

  warn "stash pop failed, likely due to conflicts. Stopping here."
  print_dirty_summary
  cat <<'EOF'

確認してください:
  git status -sb
  git diff
  git stash list

競合を解消したら通常の git 手順で続行してください。
EOF
  exit 1
}

run_update_fast_forward() {
  local upstream=""
  upstream="$(git rev-parse --abbrev-ref --symbolic-full-name "@{upstream}" 2>/dev/null || true)"

  info "Fetching ${REMOTE_NAME}..."
  git fetch "${REMOTE_NAME}" || return 1

  if [[ -n "$upstream" ]]; then
    info "Fast-forwarding from upstream: ${upstream}"
    git merge --ff-only "$upstream" || return 1
    return 0
  fi

  if git show-ref --verify --quiet "refs/remotes/${REMOTE_NAME}/${WORK_BRANCH}"; then
    warn "No upstream is set. Falling back to ${REMOTE_NAME}/${WORK_BRANCH} for compatibility."
    info "Fast-forwarding from ${REMOTE_NAME}/${WORK_BRANCH}"
    git merge --ff-only "${REMOTE_NAME}/${WORK_BRANCH}" || return 1
    return 0
  fi

  warn "No upstream configured and ${REMOTE_NAME}/${WORK_BRANCH} was not found."
  return 1
}

abort_update_failure_with_stash_hint() {
  local stash_created="$1"
  warn "Update failed before completion."
  print_dirty_summary
  if [[ "$stash_created" -eq 1 ]]; then
    cat <<'EOF'

変更は stash に残っている可能性があります。確認してください:
  git status -sb
  git stash list
  git stash show --stat stash@{0}
EOF
  else
    cat <<'EOF'

確認してください:
  git status -sb
  git log --oneline --decorate -5
EOF
  fi
  exit 1
}

guard_work_branch() {
  local cur
  cur="$(current_branch_or_die)"
  if [[ "$cur" != "$WORK_BRANCH" ]]; then
    die "Current branch is '$cur' (expected: '$WORK_BRANCH'). Edit WORK_BRANCH or use switch."
  fi
}

# ------------------------------------------------------------------------------
# Commands
# ------------------------------------------------------------------------------

cmd_update() {
  local phone_host="$DEFAULT_PHONE_HOST"
  local port="$DEFAULT_PORT"
  local flavor="$DEFAULT_ANDROID_FLAVOR"
  local clean=0
  local dirty_mode="stop"
  local dry_run=0

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --host) phone_host="$(validate_phone_host "${2:?Missing host}")"; shift 2 ;;
      --port|-p) port="$(validate_adb_port "${2:?Missing port}")"; shift 2 ;;
      --flavor) flavor="$(normalize_android_flavor "${2:?Missing flavor}")"; shift 2 ;;
      --clean-install|-c) clean=1; shift ;;
      --stash) dirty_mode="stash"; shift ;;
      --wip) dirty_mode="wip"; shift ;;
      --no-wip) dirty_mode="stop"; shift ;;
      --dry-run) dry_run=1; shift ;;
      --verbose|-v) VERBOSE=1; shift ;;
      --help|-h) usage ;;
      *) die "Unknown option for update: $1" ;;
    esac
  done

  require_cmd git
  ensure_git_repo
  guard_work_branch

  echo "🔧 update.sh (single-dev safe mode)"
  echo "📡 Target: ${phone_host}:${port}"
  echo "📦 Android flavor: ${flavor} ($(install_task_for_flavor "$flavor"))"
  [[ "$clean" -eq 1 ]] && echo "🧼 Clean install: ON (uninstall -> $(install_task_for_flavor "$flavor"))"
  [[ "$dry_run" -eq 1 ]] && echo "Dry-run: ON (stop after fetch/pull)"
  case "$dirty_mode" in
    stop) echo "Dirty policy: stop (no automatic commit/stash/reset)" ;;
    stash) echo "Dirty policy: stash then pop" ;;
    wip) echo "Dirty policy: legacy / explicit WIP commit" ;;
  esac

  # ★追加: 実行開始時点のHEADを表示
  print_head_commit

  local auto_wip_commit=0
  local auto_stash=0
  local skip_update_fast_forward=0
  if is_worktree_dirty; then
    case "$dirty_mode" in
      stop)
        if [[ "$flavor" == "customBuildExperiment" ]] &&
          git diff -- app/build.gradle.kts app/src/debug/java/io/github/ninbyo02/lami/ui/screens/home/Qairt244ShortMultitokenSmoke.kt | grep -q 'TRUE_ENGINE_NPU_PROBE_HELD_RUN_ONCE_ENABLED.*true'; then
          warn "dirty WIP accepted for local customBuildExperiment held-engine probe install; skipping fetch/pull."
          skip_update_fast_forward=1
        else
          abort_dirty_update
        fi
        ;;
      stash) stash_current_changes_if_dirty auto_stash ;;
      wip)
        create_wip_commit_if_dirty
        auto_wip_commit=1
        ;;
      *) die "Internal error: unknown dirty mode: $dirty_mode" ;;
    esac
  fi

  if [[ "$skip_update_fast_forward" -eq 1 ]]; then
    warn "Skipping git fetch/merge because this is a dirty local probe install."
  else
    info "Pulling latest changes..."
    if ! run_update_fast_forward; then
      abort_update_failure_with_stash_hint "$auto_stash"
    fi

    pop_update_stash_if_needed "$auto_stash"
  fi

  # ★追加: pull後のHEADも表示（追跡しやすい）
  print_head_commit

  if [[ "$dry_run" -eq 1 ]]; then
    ok "dry-run completed (git fetch/pull only)."
    if [[ "$auto_wip_commit" -eq 1 ]]; then
      echo ""
      warn "NOTE: local WIP commit created: ${WIP_MESSAGE}"
      warn "Undo: git reset --soft HEAD~1"
    fi
    exit 0
  fi

  require_cmd adb
  [[ -x ./gradlew ]] || die "gradlew not found or not executable. Run from repo root."

  local device_count
  device_count="$(adb_connect_and_count "$phone_host" "$port")"
  if [[ "$device_count" -lt 1 ]]; then
    warn "No device detected. Running assembleDebug only."
    ./gradlew :app:assembleDebug
    exit 0
  fi

  do_clean_uninstall_if_requested "$clean" "$flavor"

  info "Device detected ($device_count). Running $(install_task_for_flavor "$flavor")..."
  run_install_debug "$flavor"
  ok "update completed."

  if [[ "$auto_wip_commit" -eq 1 ]]; then
    echo ""
    warn "NOTE: local WIP commit created: ${WIP_MESSAGE}"
    warn "Undo: git reset --soft HEAD~1"
  fi
}

cmd_wip() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --help|-h) usage ;;
      *) die "Unknown option for wip: $1" ;;
    esac
  done

  require_cmd git
  ensure_git_repo
  current_branch_or_die >/dev/null
  print_head_commit
  create_wip_commit_if_dirty
}

cmd_stash() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --help|-h) usage ;;
      *) die "Unknown option for stash: $1" ;;
    esac
  done

  require_cmd git
  ensure_git_repo
  current_branch_or_die >/dev/null
  print_head_commit

  local stash_created=0
  stash_current_changes_if_dirty stash_created
  [[ "$stash_created" -eq 1 ]] && ok "stash created: ${STASH_MESSAGE}"
}

cmd_publish() {
  local msg=""
  local remote="$REMOTE_NAME"
  local branch=""
  local no_push=0
  local dry_run=0
  local no_verify=0

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --message|-m) msg="${2:?Missing commit message}"; shift 2 ;;
      --remote) remote="${2:?Missing remote name}"; shift 2 ;;
      --branch) branch="${2:?Missing branch name}"; shift 2 ;;
      --no-push) no_push=1; shift ;;
      --dry-run) dry_run=1; shift ;;
      --no-verify) no_verify=1; shift ;;
      --help|-h) usage ;;
      *) die "Unknown option for publish: $1" ;;
    esac
  done

  require_cmd git
  ensure_git_repo

  local cur
  cur="$(current_branch_or_die)"
  if [[ -z "$branch" ]]; then
    branch="$cur"
  fi

  print_head_commit

  local dirty=0
  if [[ -n "$(git status --porcelain)" ]]; then
    dirty=1
  fi

  if [[ "$dirty" -eq 1 && -z "$msg" ]]; then
    die "publish requires --message when the working tree has changes."
  fi

  info "Fetching ${remote}..."
  git fetch -q "$remote" || true

  print_publish_status "$remote" "$branch"

  local remote_ref="refs/remotes/${remote}/${branch}"
  local remote_exists=0
  local ahead="0"
  local behind="0"
  if git show-ref --verify --quiet "$remote_ref"; then
    remote_exists=1
    read -r ahead behind < <(
      git rev-list --left-right --count "HEAD...${remote_ref}" 2>/dev/null
    )
    ahead="${ahead:-0}"
    behind="${behind:-0}"
  fi

  if [[ "$behind" -gt 0 ]]; then
    die "Local branch is behind ${remote}/${branch} (${behind} commit(s)). Pull/rebase first."
  fi

  if [[ "$dry_run" -eq 1 ]]; then
    info "Dry-run: no commit or push will be performed."
    if [[ "$dirty" -eq 1 ]]; then
      info "Would stage all changes and commit with message:"
      info "  $msg"
    else
      info "No working tree changes to commit."
    fi
    if [[ "$no_push" -eq 1 ]]; then
      info "Would skip push (--no-push)."
    elif [[ "$remote_exists" -eq 1 ]]; then
      info "Would push HEAD to ${remote}/${branch}."
    else
      info "Would create remote tracking branch ${remote}/${branch}."
    fi
    return 0
  fi

  if [[ "$dirty" -eq 1 ]]; then
    info "Staging all changes..."
    git add -A

    info "Creating commit..."
    if [[ "$no_verify" -eq 1 ]]; then
      git commit --no-verify -m "$msg"
    else
      git commit -m "$msg"
    fi
    ok "commit created"
  else
    info "No working tree changes to commit."
  fi

  if [[ "$no_push" -eq 1 ]]; then
    warn "Skipped push (--no-push)."
    return 0
  fi

  info "Pushing ${cur} to ${remote}/${branch}..."
  if [[ "$remote_exists" -eq 1 ]]; then
    git push "$remote" "HEAD:${branch}"
  else
    git push -u "$remote" "HEAD:${branch}"
  fi
  ok "pushed: ${remote}/${branch}"
}

cmd_switch() {
  local pr=""
  local commit=""
  local branch=""
  local push=0
  local set_work=0
  local force=0

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --pr) pr="${2:?Missing PR number}"; shift 2 ;;
      --commit) commit="${2:?Missing commit sha}"; shift 2 ;;
      --branch|-b) branch="${2:?Missing branch name}"; shift 2 ;;
      --push) push=1; shift ;;
      --set-work-branch) set_work=1; shift ;;
      --force) force=1; shift ;;
      --help|-h) usage ;;
      *) die "Unknown option for switch: $1" ;;
    esac
  done

  require_cmd git
  ensure_git_repo

  # ★追加: 実行開始時点のHEADを表示
  print_head_commit

  git fetch -q "${REMOTE_NAME}" || true

  local base_commit=""
  if [[ -n "$pr" ]]; then
    base_commit="$(resolve_pr_to_commit "$pr")"
    info "Resolved PR #$pr -> $base_commit"
  elif [[ -n "$commit" ]]; then
    base_commit="$commit"
  else
    die "switch requires --pr N or --commit SHA"
  fi

  local shortsha
  shortsha="$(git rev-parse --short "$base_commit")"

  if [[ -z "$branch" ]]; then
    if [[ -n "$pr" ]]; then
      branch="work/from-pr-${pr}-${shortsha}"
    else
      branch="work/from-${shortsha}"
    fi
    info "Auto branch name: $branch"
  fi

  if branch_exists_local "$branch"; then
    if [[ "$force" -eq 0 ]]; then
      die "Local branch already exists: $branch (use --force to reuse/reset)"
    fi
    warn "Local branch exists; resetting it to $base_commit (--force)."
    git switch "$branch"
    git reset --hard "$base_commit"
  else
    info "Creating branch '$branch' from $base_commit"
    git switch -c "$branch" "$base_commit"
    ok "Created local branch: $branch"
  fi

  # ★追加: 切替後のHEADを表示
  print_head_commit

  local remote_exists=0
  if branch_exists_remote "$branch"; then
    remote_exists=1
  fi

  if [[ "$push" -eq 1 ]]; then
    if [[ "$remote_exists" -eq 1 && "$force" -eq 0 ]]; then
      die "Remote branch already exists: ${REMOTE_NAME}/$branch (use --force to overwrite)"
    fi

    info "Pushing to ${REMOTE_NAME}..."
    if [[ "$remote_exists" -eq 1 && "$force" -eq 1 ]]; then
      warn "Overwriting remote branch with --force-with-lease: ${REMOTE_NAME}/$branch"
      git push --force-with-lease -u "${REMOTE_NAME}" "$branch"
    else
      git push -u "${REMOTE_NAME}" "$branch"
    fi
    ok "Remote branch ready: ${REMOTE_NAME}/$branch"
  else
    warn "Skipped push (--push not specified)."
    [[ "$remote_exists" -eq 1 ]] && warn "Note: remote already exists: ${REMOTE_NAME}/$branch"
  fi

  if [[ "$set_work" -eq 1 ]]; then
    set_work_branch_in_script "$branch"
  else
    info "WORK_BRANCH not changed (use --set-work-branch to update script)."
  fi

  echo ""
  ok "Branch prepared: $branch"
  echo "BRANCH_NAME=$branch"
}

cmd_test() {
  local pr=""
  local commit=""
  local port="$DEFAULT_PORT"
  local flavor="$DEFAULT_ANDROID_FLAVOR"
  local clean=0
  local do_build=0
  local do_install=0
  local keep_temp=0

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --pr) pr="${2:?Missing PR number}"; shift 2 ;;
      --commit) commit="${2:?Missing commit sha}"; shift 2 ;;
      --port|-p) port="${2:?Missing port}"; shift 2 ;;
      --flavor) flavor="$(normalize_android_flavor "${2:?Missing flavor}")"; shift 2 ;;
      --clean-install|-c) clean=1; shift ;;
      --build) do_build=1; shift ;;
      --install) do_install=1; shift ;;
      --keep-temp) keep_temp=1; shift ;;
      --verbose|-v) VERBOSE=1; shift ;;
      --help|-h) usage ;;
      *) die "Unknown option for test: $1" ;;
    esac
  done

  require_cmd git
  ensure_git_repo

  # ★追加: 実行開始時点のHEADを表示
  print_head_commit

  git fetch -q "${REMOTE_NAME}" || true

  [[ -x ./gradlew ]] || die "gradlew not found or not executable. Run from repo root."

  if [[ "$do_build" -eq 0 && "$do_install" -eq 0 ]]; then
    do_build=1
  fi

  if [[ "$clean" -eq 1 && "$do_install" -eq 0 ]]; then
    die "--clean-install requires --install (test mode)."
  fi

  local target_commit=""
  if [[ -n "$pr" ]]; then
    target_commit="$(resolve_pr_to_commit "$pr")"
    info "Resolved PR #$pr -> $target_commit"
  elif [[ -n "$commit" ]]; then
    target_commit="$commit"
  else
    die "test requires --pr N or --commit SHA"
  fi

  local shortsha
  shortsha="$(git rev-parse --short "$target_commit")"

  local orig_branch
  orig_branch="$(current_branch_or_die)"

  local tmp_branch
  if [[ -n "$pr" ]]; then
    tmp_branch="tmp/test-pr-${pr}-${shortsha}"
  else
    tmp_branch="tmp/test-${shortsha}"
  fi

  if branch_exists_local "$tmp_branch"; then
    die "Temp branch already exists: $tmp_branch (delete it or use another target)"
  fi

  info "Creating temp branch: $tmp_branch (from $target_commit)"
  git switch -c "$tmp_branch" "$target_commit"

  # ★追加: temp branchへ切替後のHEADも表示
  print_head_commit

  if [[ "$do_build" -eq 1 ]]; then
    local compile_task
    compile_task="$(compile_task_for_flavor "$flavor")"
    info "Running ${compile_task}..."
    ./gradlew ":app:${compile_task}"
    ok "build ok"
  fi

  if [[ "$do_install" -eq 1 ]]; then
    require_cmd adb
    local device_count
    device_count="$(adb_connect_and_count "$port")"
    [[ "$device_count" -ge 1 ]] || die "No device detected for install test."
    do_clean_uninstall_if_requested "$clean" "$flavor"
    info "Running $(install_task_for_flavor "$flavor")..."
    run_install_debug "$flavor"
    ok "install ok"
  fi

  info "Restoring original branch: $orig_branch"
  git switch -q "$orig_branch"

  # ★追加: 復帰後のHEADも表示
  print_head_commit

  if [[ "$keep_temp" -eq 1 ]]; then
    warn "Keeping temp branch: $tmp_branch (--keep-temp)"
  else
    info "Deleting temp branch: $tmp_branch"
    git branch -D "$tmp_branch" >/dev/null 2>&1 || true
  fi

  ok "test completed."
}

# Install current branch "as-is" (no pull), for UI verification.
cmd_here_install() {
  local port="$DEFAULT_PORT"
  local flavor="$DEFAULT_ANDROID_FLAVOR"
  local clean=0
  local dirty_mode="stop"
  local build_only=0

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --port|-p) port="${2:?Missing port}"; shift 2 ;;
      --flavor) flavor="$(normalize_android_flavor "${2:?Missing flavor}")"; shift 2 ;;
      --clean-install|-c) clean=1; shift ;;
      --wip) dirty_mode="wip"; shift ;;
      --no-wip) dirty_mode="stop"; shift ;;
      --build-only) build_only=1; shift ;;
      --verbose|-v) VERBOSE=1; shift ;;
      --help|-h) usage ;;
      *) die "Unknown option for here-install: $1" ;;
    esac
  done

  require_cmd git
  ensure_git_repo
  [[ -x ./gradlew ]] || die "gradlew not found or not executable. Run from repo root."

  # ★追加: 実行開始時点のHEADを表示
  print_head_commit

  local auto_wip_commit=0
  handle_dirty_wip_mode "here-install" "$dirty_mode" auto_wip_commit

  local cur
  cur="$(current_branch_or_die)"
  info "here-install on current branch: $cur (no pull)"

  local compile_task
  compile_task="$(compile_task_for_flavor "$flavor")"
  info "Running ${compile_task}..."
  ./gradlew ":app:${compile_task}"
  ok "build ok"

  if [[ "$build_only" -eq 1 ]]; then
    ok "here-install completed (build-only)."
    [[ "$auto_wip_commit" -eq 1 ]] && warn "NOTE: local WIP commit created. Undo: git reset --soft HEAD~1"
    return 0
  fi

  require_cmd adb
  local device_count
  device_count="$(adb_connect_and_count "$port")"
  [[ "$device_count" -ge 1 ]] || die "No device detected for install."

  do_clean_uninstall_if_requested "$clean" "$flavor"
  info "Running $(install_task_for_flavor "$flavor")..."
  run_install_debug "$flavor"
  ok "install ok (current branch)"

  [[ "$auto_wip_commit" -eq 1 ]] && warn "NOTE: local WIP commit created. Undo: git reset --soft HEAD~1"
  ok "here-install completed."
}

# Promote current feature branch into main, then optionally install stable(main).
cmd_promote() {
  local base="main"
  local no_push=0
  local dirty_mode="stop"
  local do_install=0
  local port="$DEFAULT_PORT"
  local flavor="$DEFAULT_ANDROID_FLAVOR"
  local clean=0
  local msg=""

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --base) base="${2:?Missing base branch}"; shift 2 ;;
      --no-push) no_push=1; shift ;;
      --wip) dirty_mode="wip"; shift ;;
      --no-wip) dirty_mode="stop"; shift ;;
      --install) do_install=1; shift ;;
      --port|-p) port="${2:?Missing port}"; shift 2 ;;
      --flavor) flavor="$(normalize_android_flavor "${2:?Missing flavor}")"; shift 2 ;;
      --clean-install|-c) clean=1; shift ;;
      --message) msg="${2:?Missing message}"; shift 2 ;;
      --verbose|-v) VERBOSE=1; shift ;;
      --help|-h) usage ;;
      *) die "Unknown option for promote: $1" ;;
    esac
  done

  require_cmd git
  ensure_git_repo
  [[ -x ./gradlew ]] || die "gradlew not found or not executable. Run from repo root."

  # ★追加: 実行開始時点のHEADを表示
  print_head_commit

  local auto_wip_commit=0
  handle_dirty_wip_mode "promote" "$dirty_mode" auto_wip_commit

  local from_branch
  from_branch="$(current_branch_or_die)"
  [[ "$from_branch" != "$base" ]] || die "You are already on '$base'. Promote from a feature branch."

  git fetch -q "${REMOTE_NAME}" || true

  info "Switching to base: $base"
  git switch -q "$base"
  git pull --ff-only "${REMOTE_NAME}" "$base" >/dev/null 2>&1 || true

  # ★追加: baseへ切替後のHEADを表示
  print_head_commit

  if [[ -z "$msg" ]]; then
    msg="merge: ${from_branch} -> ${base}"
  fi

  info "Merging with --no-ff: $from_branch"
  git merge --no-ff "$from_branch" -m "$msg"

  # ★追加: merge後のHEADを表示
  print_head_commit

  info "Running compileDebugKotlin on $base..."
  ./gradlew :app:compileDebugKotlin
  ok "build ok on $base"

  if [[ "$no_push" -eq 1 ]]; then
    warn "Skipped push (--no-push)."
  else
    info "Pushing $base to ${REMOTE_NAME}..."
    git push "${REMOTE_NAME}" "$base"
    ok "pushed: ${REMOTE_NAME}/${base}"
  fi

  if [[ "$do_install" -eq 1 ]]; then
    require_cmd adb
    local device_count
    device_count="$(adb_connect_and_count "$port")"
    [[ "$device_count" -ge 1 ]] || die "No device detected for install."
    do_clean_uninstall_if_requested "$clean" "$flavor"
    info "Installing stable build from $base with $(install_task_for_flavor "$flavor")..."
    run_install_debug "$flavor"
    ok "stable install ok: $base"
  fi

  info "Returning to original branch: $from_branch"
  git switch -q "$from_branch"

  # ★追加: 復帰後のHEADを表示
  print_head_commit

  [[ "$auto_wip_commit" -eq 1 ]] && warn "NOTE: local WIP commit created. Undo: git reset --soft HEAD~1"
  ok "promote completed."
}

# ------------------------------------------------------------------------------
# Main
# ------------------------------------------------------------------------------

if [[ $# -lt 1 ]]; then
  usage
fi

cmd="$1"; shift || true
case "$cmd" in
  update) cmd_update "$@" ;;
  wip) cmd_wip "$@" ;;
  stash) cmd_stash "$@" ;;
  publish) cmd_publish "$@" ;;
  commit-push) cmd_publish "$@" ;;
  switch) cmd_switch "$@" ;;
  test)   cmd_test "$@" ;;
  here-install) cmd_here_install "$@" ;;
  promote) cmd_promote "$@" ;;
  --help|-h|help) usage ;;
  *)
    usage
    ;;
esac
