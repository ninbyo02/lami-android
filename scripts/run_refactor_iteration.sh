#!/usr/bin/env bash
set -euo pipefail

ROOT=$(git -C "$(dirname "${BASH_SOURCE[0]}")/.." rev-parse --show-toplevel)
BASE_BRANCH=${LAMI_REFACTOR_BASE_BRANCH:-main}
MAX_RISK=${LAMI_REFACTOR_MAX_RISK:-1}
PLAN_ONLY=${LAMI_REFACTOR_PLAN_ONLY:-0}
NO_PUSH=${LAMI_REFACTOR_NO_PUSH:-0}
JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

if (( MAX_RISK >= 2 )) && [[ ${LAMI_REFACTOR_ACK_RISK_2:-} != yes ]]; then
  echo "Risk >=2 requires LAMI_REFACTOR_ACK_RISK_2=yes" >&2
  exit 2
fi
if (( MAX_RISK >= 3 )) && [[ ${LAMI_REFACTOR_ACK_RISK_3:-} != yes ]]; then
  echo "Risk 3 requires LAMI_REFACTOR_ACK_RISK_3=yes" >&2
  exit 2
fi

for command in git python3 gh jq codex flock; do
  command -v "$command" >/dev/null || { echo "Missing required command: $command" >&2; exit 2; }
done

mkdir -p "$ROOT/.git/lami-refactor"
exec 9>"$ROOT/.git/lami-refactor/loop.lock"
if ! flock -n 9; then
  echo "Another refactor iteration is already running; exiting."
  exit 0
fi

workspace=""
artifacts=$(mktemp -d)
cleanup() {
  status=$?
  rm -rf "$artifacts"
  if [[ -n $workspace && -d $workspace ]]; then
    if [[ $status -eq 0 || ${LAMI_REFACTOR_KEEP_FAILED_WORKTREE:-1} == 0 ]]; then
      git -C "$ROOT" worktree remove --force "$workspace" >/dev/null 2>&1 || true
    else
      echo "Failed iteration preserved for inspection: $workspace" >&2
    fi
  fi
}
trap cleanup EXIT

git -C "$ROOT" fetch origin "$BASE_BRANCH"
repo=$(cd "$ROOT" && gh repo view --json nameWithOwner --jq .nameWithOwner)
open_auto_prs=$(gh pr list --repo "$repo" --state open --json headRefName \
  --jq '[.[] | select(.headRefName | startswith("refactor/auto-"))] | length')
if [[ $open_auto_prs != 0 ]]; then
  echo "An automated refactor PR is already open; no new iteration will start."
  exit 0
fi

base_sha=$(git -C "$ROOT" rev-parse "origin/$BASE_BRANCH")
workspace=$(mktemp -d /tmp/lami-refactor-worktree.XXXXXX)
git -C "$ROOT" worktree add --detach "$workspace" "$base_sha" >/dev/null

python3 "$workspace/scripts/refactor_inventory.py" --max-risk "$MAX_RISK" \
  --json "$artifacts/before.json" --markdown "$artifacts/inventory.md" \
  --prompt "$artifacts/prompt.txt"
cat "$artifacts/inventory.md"

candidate_id=$(jq -r '.selected_candidate.id // empty' "$artifacts/before.json")
if [[ -z $candidate_id ]]; then
  if [[ $(jq -r .done "$artifacts/before.json") == true ]]; then
    echo "All measurable refactoring contract gates pass."
  else
    echo "No candidate is eligible at max risk $MAX_RISK. Approval is required before raising risk."
  fi
  exit 0
fi
if [[ $PLAN_ONLY == 1 ]]; then
  echo "--- generated agent prompt ---"
  cat "$artifacts/prompt.txt"
  exit 0
fi

symbol=$(jq -r '.selected_candidate.symbol // .selected_candidate.kind' "$artifacts/before.json")
slug=$(printf '%s' "$symbol" | tr '[:upper:]' '[:lower:]' | sed -E 's/[^a-z0-9]+/-/g; s/^-|-$//g' | cut -c1-42)
branch="refactor/auto-${slug}-$(date -u +%Y%m%d%H%M%S)"
git -C "$workspace" switch -c "$branch" >/dev/null

echo "Running coding agent for: $candidate_id"
if [[ -n ${LAMI_REFACTOR_AGENT_CMD:-} ]]; then
  (cd "$workspace" && bash -lc "$LAMI_REFACTOR_AGENT_CMD") \
    < "$artifacts/prompt.txt" | tee "$artifacts/agent.log"
else
  codex exec --approve-for-me --sandbox workspace-write --ephemeral -C "$workspace" - \
    < "$artifacts/prompt.txt" | tee "$artifacts/agent.log"
fi

if [[ -z $(git -C "$workspace" status --porcelain) ]]; then
  echo "Agent made no changes; no PR will be created."
  exit 3
fi
# Intent-to-add lets the guard inspect new files without staging their contents.
git -C "$workspace" add -N -- .
python3 "$workspace/scripts/refactor_inventory.py" --max-risk "$MAX_RISK" --json "$artifacts/after.json"
(
  cd "$workspace"
  python3 scripts/refactor_guard.py --before "$artifacts/before.json" --after "$artifacts/after.json" \
    --base "$base_sha" --max-risk "$MAX_RISK"
)

(
  cd "$workspace"
  ./gradlew --no-daemon --max-workers=4 \
    -Dorg.gradle.jvmargs='-Xmx6g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8' \
    -Pkotlin.compiler.execution.strategy=in-process -Plami.allowMissingQairt244Jni=true \
    :app:testStandardDebugUnitTest :app:lintStandardDebug
  git diff --check "$base_sha" --
  git add -A
  commit_subject="refactor: reduce ${symbol} structural debt"
  git commit -m "$commit_subject"

  if [[ $NO_PUSH == 1 ]]; then
    echo "Validated local commit created on $branch; push/PR skipped by LAMI_REFACTOR_NO_PUSH=1."
    exit 0
  fi

  git push -u origin "$branch"
  cat > "$artifacts/pr-body.md" <<EOF
Automated Risk $MAX_RISK refactoring iteration under \`docs/REFACTORING_CONTRACT.md\`.

Selected finding: \`$candidate_id\`

Safety envelope:
- isolated git worktree
- one structural responsibility only
- autonomous merge disabled
- structural guard passed
- \`git diff --check\` passed
- Standard Debug unit tests passed
- Standard Debug lint passed

Android CI remains the authoritative Debug/Release merge gate. Review is required before merge.
EOF
  pr_url=$(gh pr create --repo "$repo" --base "$BASE_BRANCH" --head "$branch" \
    --title "$commit_subject" --body-file "$artifacts/pr-body.md")
  echo "Created review-gated refactor PR: $pr_url"
)
