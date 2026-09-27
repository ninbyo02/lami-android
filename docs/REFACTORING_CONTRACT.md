# LAMI Android Autonomous Refactoring Contract

Status: Initial safe automation contract

## Goal

The refactoring loop reduces structural debt without changing product behavior. "Done" means the measurable quality gates in `.github/refactor/contract.json` have no remaining violations and the architecture-specific review gates below are satisfied. It does not mean that no further stylistic improvement is possible.

## One iteration, one responsibility

Each iteration selects exactly one highest-priority eligible finding, creates a dedicated branch, applies a minimal refactor, validates it, and opens one pull request. A new automated iteration must not start while another `refactor/auto-*` pull request is open.

The loop must prefer local, behavior-preserving transformations before moving ownership across architectural boundaries. Line-count reduction alone is never sufficient evidence of improvement.

## Risk levels

Risk 1 is mechanical presentation cleanup: component extraction, pure helper extraction, parameter bundling, deterministic formatting, or moving declarations without lifecycle changes. Risk 1 may be implemented automatically, but merge remains review-gated in the initial rollout.

Risk 2 changes state or orchestration ownership: state holders, ViewModels, coroutine/Job ownership, repositories, coordinators, or services. It requires explicit opt-in by raising the loop's maximum risk.

Risk 3 touches inference/runtime lifecycle, database/schema/migrations, TTS ordering, network semantics, native/JNI code, model packaging, or backend fallback behavior. It requires architecture review and, where applicable, real-device evidence.

## Hard invariants

An autonomous iteration must not intentionally change user-visible behavior, strings, execution order, persistence semantics, timeout/cancellation precedence, TTS ordering, backend selection, model configuration, database schema, or public protocol contracts unless that behavior change is the separately approved task.

It must not commit, push, merge, or alter secrets from inside the coding agent. Git operations after validation are owned by the outer loop controller.

## Validation gates

Every generated change must pass `git diff --check`, the structural guard, Standard Debug unit tests, and Standard Debug lint before a pull request is created. Existing Android CI remains the authoritative Debug/Release merge gate.

The structural guard rejects oversized diffs, blocked paths for the selected risk level, new structural violations, and changes that fail to improve the selected finding.

## Completion gates

The machine-readable thresholds currently require Kotlin production files to remain within the configured file/function size limits, Composables to stay within the parameter budget, UI files to keep remembered state below the configured density, and UI files to stop directly owning mutable `Job` state.

Architecture completion additionally requires screen-level UI to consume state/events instead of owning inference engines, persistence, TTS, networking, or runtime lifecycle directly. Runtime, memory, communication, and platform-specific adapters must be independently testable. These architecture gates are reviewed before declaring the overall refactor program complete.

## Rollout policy

Initial mode is `max-risk=1`, PR generation only, no automatic merge. After repeated clean runs, Risk 1 may later be promoted to CI-gated auto-merge. Risk 2 and Risk 3 remain separately governed until enough regression and device evidence exists.
