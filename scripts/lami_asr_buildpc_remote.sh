#!/usr/bin/env bash
set -euo pipefail

HOST=""
USER_NAME="lami-build"
PORT="2222"
SSH_KEY=""
REMOTE_REPO="/home/lami-build/repos/lami-asr-android"
REF="future"
GRADLE_TASK=":app:assembleStandardDebug"
APPLY=0

usage() {
  cat <<'EOF'
Usage: scripts/lami_asr_buildpc_remote.sh --host HOST [options]

Safely builds lami-asr-android on a remote build PC without resetting or
modifying the canonical checkout. The script is dry-run by default.

Options:
  --host HOST              Required remote host or IP
  --user USER              SSH user (default: lami-build)
  --port PORT              SSH port (default: 2222)
  --ssh-key PATH           Optional SSH private key
  --repo PATH              Remote canonical repo path
  --ref REF                Remote git ref to build (default: future)
  --gradle-task TASK       Gradle task (default: :app:assembleStandardDebug)
  --apply                  Execute; otherwise print the SSH command only
  -h, --help               Show this help

Safety contract:
  * canonical repo must exist and be clean
  * fetches the requested ref from origin
  * builds from a temporary detached git worktree
  * never reset --hard, stash, clean, force-push, or delete the canonical repo
  * removes only the temporary worktree it created
EOF
}

while (($#)); do
  case "$1" in
    --host) HOST="${2:?missing host}"; shift 2 ;;
    --user) USER_NAME="${2:?missing user}"; shift 2 ;;
    --port) PORT="${2:?missing port}"; shift 2 ;;
    --ssh-key) SSH_KEY="${2:?missing key path}"; shift 2 ;;
    --repo) REMOTE_REPO="${2:?missing repo path}"; shift 2 ;;
    --ref) REF="${2:?missing ref}"; shift 2 ;;
    --gradle-task) GRADLE_TASK="${2:?missing gradle task}"; shift 2 ;;
    --apply) APPLY=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "unknown argument: $1" >&2; usage >&2; exit 2 ;;
  esac
done

[[ -n "$HOST" ]] || { echo "--host is required" >&2; exit 2; }
[[ "$PORT" =~ ^[0-9]+$ ]] || { echo "invalid --port: $PORT" >&2; exit 2; }

printf -v q_repo '%q' "$REMOTE_REPO"
printf -v q_ref '%q' "$REF"
printf -v q_task '%q' "$GRADLE_TASK"

read -r -d '' REMOTE_SCRIPT <<EOF || true
set -euo pipefail
repo=$q_repo
ref=$q_ref
task=$q_task

[[ -d "\$repo/.git" ]] || { echo "not a git repo: \$repo" >&2; exit 10; }
cd "\$repo"
[[ -z "\$(git status --porcelain)" ]] || { echo "canonical repo is dirty; refusing build" >&2; exit 11; }

git fetch --no-tags origin "\$ref"
sha="\$(git rev-parse FETCH_HEAD)"
worktree="\${TMPDIR:-/tmp}/lami-asr-build-\${sha:0:12}-\$\$"
cleanup() {
  git -C "\$repo" worktree remove --force "\$worktree" >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM

git worktree add --detach "\$worktree" "\$sha"
cd "\$worktree"
[[ -x ./gradlew ]] || chmod +x ./gradlew
./gradlew "\$task" --no-daemon
printf 'ASR_BUILD_OK sha=%s task=%s worktree=%s\n' "\$sha" "\$task" "\$worktree"
EOF

ssh_args=(-p "$PORT" -o BatchMode=yes -o IdentitiesOnly=yes -o StrictHostKeyChecking=accept-new)
[[ -n "$SSH_KEY" ]] && ssh_args+=(-i "$SSH_KEY")

echo "target=${USER_NAME}@${HOST}:${PORT}"
echo "repo=${REMOTE_REPO}"
echo "ref=${REF}"
echo "task=${GRADLE_TASK}"

if (( APPLY == 0 )); then
  echo "mode=dry-run"
  printf 'ssh'
  printf ' %q' "${ssh_args[@]}" "${USER_NAME}@${HOST}" "bash -s"
  printf '\n'
  printf '%s\n' "$REMOTE_SCRIPT"
  exit 0
fi

echo "mode=apply"
ssh "${ssh_args[@]}" "${USER_NAME}@${HOST}" 'bash -s' <<<"$REMOTE_SCRIPT"
