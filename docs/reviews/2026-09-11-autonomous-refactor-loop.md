# Autonomous Refactor Loop

## Purpose

This loop turns the current manual refactor cadence into a repeatable, review-gated process. It measures structural debt, selects one eligible finding, asks a coding agent for a minimal behavior-preserving change, validates the result, and opens exactly one PR.

The initial rollout does not auto-merge. A human review and normal Android CI remain required before merge.

## Main entry points

- `scripts/refactor_inventory.py`: scans production Kotlin and selects the next eligible finding.
- `scripts/refactor_guard.py`: rejects scope/risk/diff regressions.
- `scripts/run_refactor_iteration.sh`: executes one full local iteration through PR creation.
- `.github/workflows/refactor-loop.yml`: continuously publishes the current inventory after main changes and on a 6-hour schedule.
- `.github/refactor/contract.json`: machine-readable thresholds and safety limits.
- `docs/REFACTORING_CONTRACT.md`: human-readable governance contract.

## Safe first-run commands

Planning only:

```bash
LAMI_REFACTOR_PLAN_ONLY=1 ./scripts/run_refactor_iteration.sh
```

Create one Risk 1 PR:

```bash
./scripts/run_refactor_iteration.sh
```

Validate and commit locally without pushing:

```bash
LAMI_REFACTOR_NO_PUSH=1 ./scripts/run_refactor_iteration.sh
```

Risk 2 and Risk 3 require explicit acknowledgements so they cannot be enabled accidentally:

```bash
LAMI_REFACTOR_MAX_RISK=2 LAMI_REFACTOR_ACK_RISK_2=yes ./scripts/run_refactor_iteration.sh
```

Risk 3 additionally requires `LAMI_REFACTOR_ACK_RISK_3=yes`. Risk elevation does not remove review or device gates.

## Loop behavior

The local controller refuses to start from a dirty tree and uses a filesystem lock so only one iteration can run at a time. It also queries GitHub and exits without doing work when any `refactor/auto-*` PR is already open.

After that PR is reviewed, CI-validated, and merged, the next invocation rescans the new `main` and chooses the next finding. This gives a bounded loop rather than an agent recursively editing its own unreviewed output.

The planner workflow runs after every push to `main`, on demand, and every six hours. Its artifacts contain the full inventory, a readable report, and the next-agent prompt, but the workflow has read-only repository permissions and cannot mutate code.
