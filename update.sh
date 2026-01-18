#!/bin/bash
set -euo pipefail

# ==============================================================================
# update.sh (single-dev safe mode)
#
# Design philosophy:
#   - Safe by default: 作業中でも止めずに進める（自動 wip）
#   - Explicit safety: --no-wip 指定時は一切触らない
#   - Reproducible: 動いていたPR/commitを即テスト可能
#
# ==============================================================================
# ===== Config =====
WORK_BRANCH=future
REMOTE_NAME="origin"
WIP_PREFIX="wip(auto):"
PHONE_IP="10.5.5.3"
DEFAULT_PORT="40215"
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
  - Safe by default: 作業中でも止めずに進める（自動 wip）
  - Explicit safety: --no-wip 指定時は一切触らない（汚れていたら中断）
  - Reproducible: 動いていたPR/commitを即テスト可能

What is WIP / --no-wip ?
  - wip(auto): 作業ツリーがdirtyでも、ローカルに一時コミットを作って先へ進む安全策
              （pushしない。戻すなら: git reset --soft HEAD~1）
  - --no-wip : 作業ツリーがdirtyなら“何もしないで止まる”慎重モード（事故防止）

Usage:
  ./update.sh                      # show help

Subcommands:
  ./update.sh update [options]      # fetch/pull WORK_BRANCH, then build+install
  ./update.sh switch [options]      # create/switch branch from PR or commit (optionally push & update WORK_BRANCH)
  ./update.sh test   [options]      # build/install test on temp branch (no detached), then return & cleanup
  ./update.sh here-install [options]# build+install current branch (no pull)
  ./update.sh promote [options]     # merge current branch into main, build, push, then install stable(main)

update options:
  --port|-p PORT        ADB connect port (default: 40215)
  --clean-install|-c    uninstall app before installDebug (requires device)
  --no-wip              abort if working tree dirty (default: auto WIP local commit)
  --dry-run             stop after fetch/pull (no gradle, no adb)
  --verbose|-v          show verbose logs (adb devices -l, etc.)

test options:
  --pr N | --commit SHA
  --build               run :app:compileDebugKotlin (default if neither --build nor --install)
  --install             run :app:installDebug (device required)
  --clean-install|-c    uninstall app before installDebug (requires --install)
  --port|-p PORT        ADB connect port (default: 40215)
  --keep-temp           keep temp branch after test (default: delete)
  --verbose|-v          show verbose logs (adb devices -l, etc.)

Examples:
  ./update.sh update --dry-run
  ./update.sh update --no-wip
  ./update.sh update -c --no-wip
  ./update.sh test --pr 398 --install -c -v
  ./update.sh here-install -p 42951
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

# ★追加: 実行時にHEADコミット情報を毎回表示
print_head_commit() {
  # repo外なら何もしない（安全）
  git rev-parse --is-inside-work-tree >/dev/null 2>&1 || return 0

  local b sha subject datetime
  b="$(git symbolic-ref --short HEAD 2>/dev/null || echo "(detached)")"
  sha="$(git rev-parse --short HEAD 2>/dev/null || echo "???????")"
  subject="$(git log -1 --pretty=%s 2>/dev/null || echo "(no commit)")"
  datetime="$(git log -1 --date=iso --pretty=%cd 2>/dev/null || echo "")"

  info "== git HEAD =="
  info "branch: ${b}"
  info "commit: ${sha} ${datetime}"
  info "msg   : ${subject}"

  if [[ -n "$(git status --porcelain 2>/dev/null || true)" ]]; then
    warn "worktree: DIRTY (has uncommitted changes)"
  else
    info "worktree: clean"
  fi
  info "========="

  print_codex_branch_guidance
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
  if ! git diff --quiet || ! git diff --cached --quiet; then
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

adb_connect_and_count() {
  local port="$1"
  info "Checking connected devices..." >&2
  adb connect "${PHONE_IP}:${port}" >/dev/null 2>&1 || true

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
  if [[ "$clean" -ne 1 ]]; then return 0; fi

  info "Resolving applicationId..."
  local app_id=""
  if app_id="$(resolve_app_id)"; then
    ok "applicationId: $app_id"
    info "Uninstalling $app_id ..."
    adb uninstall "$app_id" >/dev/null 2>&1 || true
  else
    die "Could not resolve applicationId (clean install requested)."
  fi
}

maybe_auto_wip_commit() {
  local no_wip="$1"
  local auto_wip_commit_var="$2" # name of variable to set (bash indirection)

  if [[ -n "$(git status --porcelain)" ]]; then
    warn "Working tree is dirty."
    git status --short

    if [[ "$no_wip" -eq 1 ]]; then
      die "--no-wip is set. Aborting because working tree is dirty."
    fi

    info "Creating local WIP commit (no push)..."
    git add -A
    git commit -m "${WIP_PREFIX} before update.sh" >/dev/null
    printf -v "$auto_wip_commit_var" "1"
  else
    printf -v "$auto_wip_commit_var" "0"
  fi
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
  local port="$DEFAULT_PORT"
  local clean=0
  local no_wip=0
  local dry_run=0

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --port|-p) port="${2:?Missing port}"; shift 2 ;;
      --clean-install|-c) clean=1; shift ;;
      --no-wip) no_wip=1; shift ;;
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
  echo "📡 Target: ${PHONE_IP}:${port}"
  [[ "$clean" -eq 1 ]] && echo "🧼 Clean install: ON (uninstall -> installDebug)"
  [[ "$dry_run" -eq 1 ]] && echo "Dry-run: ON (stop after fetch/pull)"

  # ★追加: 実行開始時点のHEADを表示
  print_head_commit

  local auto_wip_commit=0
  maybe_auto_wip_commit "$no_wip" auto_wip_commit

  info "Pulling latest changes..."
  git fetch "${REMOTE_NAME}" >/dev/null 2>&1 || true
  if git show-ref --verify --quiet "refs/remotes/${REMOTE_NAME}/${WORK_BRANCH}"; then
    git pull --ff-only "${REMOTE_NAME}" "$WORK_BRANCH"
  else
    warn "No remote branch ${REMOTE_NAME}/${WORK_BRANCH}. Skip pull."
  fi

  # ★追加: pull後のHEADも表示（追跡しやすい）
  print_head_commit

  if [[ "$dry_run" -eq 1 ]]; then
    ok "dry-run completed (git fetch/pull only)."
    if [[ "$auto_wip_commit" -eq 1 ]]; then
      echo ""
      warn "NOTE: local WIP commit created: ${WIP_PREFIX} before update.sh"
      warn "Undo: git reset --soft HEAD~1"
    fi
    exit 0
  fi

  require_cmd adb
  [[ -x ./gradlew ]] || die "gradlew not found or not executable. Run from repo root."

  local device_count
  device_count="$(adb_connect_and_count "$port")"
  if [[ "$device_count" -lt 1 ]]; then
    warn "No device detected. Running assembleDebug only."
    ./gradlew :app:assembleDebug
    exit 0
  fi

  do_clean_uninstall_if_requested "$clean"

  info "Device detected ($device_count). Running installDebug..."
  ./gradlew :app:installDebug
  ok "update completed."

  if [[ "$auto_wip_commit" -eq 1 ]]; then
    echo ""
    warn "NOTE: local WIP commit created: ${WIP_PREFIX} before update.sh"
    warn "Undo: git reset --soft HEAD~1"
  fi
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
  local clean=0
  local do_build=0
  local do_install=0
  local keep_temp=0

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --pr) pr="${2:?Missing PR number}"; shift 2 ;;
      --commit) commit="${2:?Missing commit sha}"; shift 2 ;;
      --port|-p) port="${2:?Missing port}"; shift 2 ;;
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
    info "Running compileDebugKotlin..."
    ./gradlew :app:compileDebugKotlin
    ok "build ok"
  fi

  if [[ "$do_install" -eq 1 ]]; then
    require_cmd adb
    local device_count
    device_count="$(adb_connect_and_count "$port")"
    [[ "$device_count" -ge 1 ]] || die "No device detected for install test."
    do_clean_uninstall_if_requested "$clean"
    info "Running installDebug..."
    ./gradlew :app:installDebug
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
  local clean=0
  local no_wip=0
  local build_only=0

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --port|-p) port="${2:?Missing port}"; shift 2 ;;
      --clean-install|-c) clean=1; shift ;;
      --no-wip) no_wip=1; shift ;;
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
  maybe_auto_wip_commit "$no_wip" auto_wip_commit

  local cur
  cur="$(current_branch_or_die)"
  info "here-install on current branch: $cur (no pull)"

  info "Running compileDebugKotlin..."
  ./gradlew :app:compileDebugKotlin
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

  do_clean_uninstall_if_requested "$clean"
  info "Running installDebug..."
  ./gradlew :app:installDebug
  ok "install ok (current branch)"

  [[ "$auto_wip_commit" -eq 1 ]] && warn "NOTE: local WIP commit created. Undo: git reset --soft HEAD~1"
  ok "here-install completed."
}

# Promote current feature branch into main, then optionally install stable(main).
cmd_promote() {
  local base="main"
  local no_push=0
  local no_wip=0
  local do_install=0
  local port="$DEFAULT_PORT"
  local clean=0
  local msg=""

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --base) base="${2:?Missing base branch}"; shift 2 ;;
      --no-push) no_push=1; shift ;;
      --no-wip) no_wip=1; shift ;;
      --install) do_install=1; shift ;;
      --port|-p) port="${2:?Missing port}"; shift 2 ;;
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
  maybe_auto_wip_commit "$no_wip" auto_wip_commit

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
    do_clean_uninstall_if_requested "$clean"
    info "Installing stable build from $base..."
    ./gradlew :app:installDebug
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
  switch) cmd_switch "$@" ;;
  test)   cmd_test "$@" ;;
  here-install) cmd_here_install "$@" ;;
  promote) cmd_promote "$@" ;;
  --help|-h|help) usage ;;
  *)
    die "Unknown subcommand: $cmd (use: update|switch|test|here-install|promote)"
    ;;
esac
