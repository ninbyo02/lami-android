# NPU Legacy S1-S5 Deprecation Inventory

Scope: inventory and planning only. This document does not change runtime,
ChatScreen, Settings UI behavior, route execution, enum values, stored
preferences, native code, or artifacts.

## Current State

The user-facing backend selector is consolidated around:

```text
Automatic
CPU
GPU Experimental
NPU Experimental
```

The legacy NPU S1-S5 entries remain for internal compatibility and developer
override:

- NPU S1 response only
- NPU S2 DB save
- NPU S3 Markdown
- NPU S4 Streaming
- NPU S5 TTS

These are not hardware backends. They are historical standard-route phases and
developer controls.

## Inventory Script

Run:

```text
scripts/review_npu_legacy_s1_s5_inventory.sh
```

The script uses rg-based static analysis and emits:

```text
NPU_LEGACY_S1_S5_INVENTORY_STATUS
LEGACY_REFERENCE_COUNT
LEGACY_RUNTIME_REFERENCE_COUNT
LEGACY_SETTINGS_REFERENCE_COUNT
LEGACY_TEST_REFERENCE_COUNT
LEGACY_DOC_REFERENCE_COUNT
LEGACY_SAFE_TO_REMOVE_NOW
LEGACY_DEPRECATION_STAGE
KEEP_FOR_COMPATIBILITY
DEVELOPER_ONLY_REFERENCES
USER_FACING_REFERENCES
CLEANUP_CANDIDATES
REMOVAL_BLOCKERS
SAFE_NEXT_ACTION
```

This is intentionally a lightweight grep inventory, not a compiler-level static
analyzer.

## Why S1-S5 Are Not Removed Now

Do not remove S1-S5 yet because they still protect:

- persisted preference compatibility
- enum parsing compatibility
- developer phase override workflows
- old artifact and diagnostic parser compatibility
- final promotion and rollout monitor compatibility
- internal legacy backend evidence such as `selected_backend=NPU_S5`
- rollback paths while the dev gate remains active

Immediate deletion would risk breaking existing developer settings and copied
diagnostic artifacts without improving the completed standard route.

## Classification

### Keep For Compatibility

Keep these until a migration window exists:

- enum values for `NPU_S1` through `NPU_S5`
- stored preference parsing and migration compatibility
- source marker handling that distinguishes user-facing `NPU Experimental` from
  developer legacy phase choices
- tests that assert legacy values remain parseable

### Developer Only

These references are acceptable while the route remains staged:

- developer phase selector labels
- debug diagnostics
- compact/full dump fixtures
- rollout and final promotion diagnostics
- docs explaining legacy phases

### Cleanup Candidates

Clean these first:

- user-facing labels that still read as separate normal backends
- docs that present S1-S5 as current backend choices rather than historical
  phases
- stale comments that imply S1-S5 are hardware backends
- diagnostics that mention only `NPU_S5` without a completed-route summary key

### Do Not Remove Yet

Do not remove:

- route execution compatibility
- final promotion artifact parser compatibility
- rollout monitor compatibility
- legacy debug override
- preference keys and enum values

## User-Facing Policy

Normal users should see one NPU option:

```text
NPU Experimental
```

S1-S5 should remain hidden from normal backend selection and treated as
developer-only legacy phase controls.

## Preference Key Compatibility

Preference keys and enum values must stay readable throughout the deprecation
window. Existing installs may still contain legacy values, and copied artifacts
may still contain:

```text
selected_backend=NPU_S5
route_family=npu_s5
```

Completed-route interpretation should use:

```text
npu_standard_route_user_facing_selected_backend=NPU Experimental
npu_standard_route_completed_route_family=npu_standard_route_completed
```

## Deprecation Stages

Stage 0:

Current state. S1-S5 are hidden from normal user Settings. Developer-only legacy
options remain.

Stage 1:

Rename all remaining user-visible legacy labels to `DEV` / `Legacy`. Keep enum
values and stored preference compatibility.

Stage 2:

Keep preference parsing but remove legacy options from active user-facing
selection UI. Developer controls may still expose phase overrides.

Stage 3:

Keep artifact parser compatibility only. Runtime selection should rely on
`NPU Experimental` plus completed-route diagnostics.

Stage 4:

Consider removal only after the migration window, rollout monitor health, dev
gate removal stability, and artifact compatibility review all pass.

## Safe Next Action

Use the inventory script before any cleanup PR. If it reports user-facing
references, clean labels and docs first. If it reports runtime or settings
references, keep them unless a migration plan explicitly covers those paths.

## R5a Relationship

R5a UX acceptance reviews explicit `NPU Experimental` usability after the
completed route can run without the dev gate:

```text
scripts/review_npu_experimental_ux_acceptance.sh --device-runs artifacts/device_runs
```

Even if R5a reports low risk, it does not authorize removing S1-S5 enum values,
preference parsing, developer overrides, or artifact parser compatibility.
Legacy cleanup remains staged:

- keep S1-S5 as developer-only compatibility
- keep `selected_backend=NPU_S5` / `route_family=npu_s5` parser compatibility
- use completed-route summary keys for rollout interpretation
- do not add NPU to Automatic as part of legacy cleanup
