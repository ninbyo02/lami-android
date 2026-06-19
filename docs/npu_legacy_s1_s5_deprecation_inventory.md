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
NPU Beta
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
NPU_EXPERIMENTAL_USER_FACING_REFERENCE_COUNT
NPU_BETA_USER_FACING_REFERENCE_COUNT
USER_FACING_NPU_EXPERIMENTAL_REMAINING
USER_FACING_NPU_BETA_COUNT
LEGACY_USER_FACING_BACKEND_WORDING_COUNT
KEEP_COUNT
DEPRECATE_COUNT
CLEANUP_CANDIDATE_COUNT
DO_NOT_REMOVE_YET_COUNT
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

Current R4b inventory snapshot after user-facing wording cleanup:

```text
NPU_LEGACY_S1_S5_INVENTORY_STATUS=legacy_references_present_with_cleanup_candidates
LEGACY_REFERENCE_COUNT=1575
LEGACY_RUNTIME_REFERENCE_COUNT=600
LEGACY_SETTINGS_REFERENCE_COUNT=24
LEGACY_TEST_REFERENCE_COUNT=331
LEGACY_DOC_REFERENCE_COUNT=419
NPU_EXPERIMENTAL_USER_FACING_REFERENCE_COUNT=0
NPU_BETA_USER_FACING_REFERENCE_COUNT=82
USER_FACING_NPU_EXPERIMENTAL_REMAINING=0
USER_FACING_NPU_BETA_COUNT=82
LEGACY_USER_FACING_BACKEND_WORDING_COUNT=121
KEEP_COUNT=442
DEPRECATE_COUNT=121
CLEANUP_CANDIDATE_COUNT=121
DO_NOT_REMOVE_YET_COUNT=600
LEGACY_SAFE_TO_REMOVE_NOW=false
SAFE_NEXT_ACTION=rename_or_hide_user_facing_legacy_labels_before_cleanup
```

There are no remaining current Settings-display uses of the old Experimental
NPU label. The larger cleanup count comes from
legacy S1-S5 wording that can still read like normal backend labels in old docs,
comments, or tests. Runtime, enum, preference, and diagnostics references remain
out of scope for immediate cleanup.

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
- source marker handling that distinguishes user-facing NPU Beta from
  developer legacy phase choices
- `npu_standard_route_selection_mode=user_facing_npu_experimental`
- `selected_backend=NPU_S5` / `route_family=npu_s5`
- `DEV: NPU S1` through `DEV: NPU S5` labels
- tests that assert legacy values remain parseable

### Deprecate

Deprecate these as wording, not as parser keys:

- the old Experimental wording as a current user-facing Settings label
- S1-S5 as backend labels in user-facing context
- docs that imply S1-S5 are normal backend choices

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
- outdated docs/comments using the old Experimental wording where the current Settings
  label should be `NPU Beta`
- tests asserting the old Experimental wording as a user-facing display label
- docs that present S1-S5 as current backend choices rather than historical
  phases
- docs that still describe phase 8 as only developer-gated after R3b/R5c
- docs that omit the kill switch safe block or dev-gate removal status
- stale comments that imply S1-S5 are hardware backends
- diagnostics that mention only `NPU_S5` without a completed-route summary key

### Do Not Remove Yet

Do not remove:

- route execution compatibility
- final promotion artifact parser compatibility
- rollout monitor compatibility
- legacy debug override
- preference keys and enum values
- diagnostics keys and artifact parser compatibility
- sample artifact compatibility

## User-Facing Policy

Normal users should see one NPU option:

```text
NPU Beta
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
NPU Beta plus completed-route diagnostics.

Stage 4:

Consider removal only after the migration window, rollout monitor health, dev
gate removal stability, and artifact compatibility review all pass.

## Safe Next Action

Use the inventory script before any cleanup PR. If it reports user-facing
references, clean labels and docs first. If it reports runtime or settings
references, keep them unless a migration plan explicitly covers those paths.

## R5a Relationship

R5a UX acceptance reviews explicit NPU usability after the
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

## R5b Settings UX Relationship

R5b reviews wording only. The recommended future user-facing backend list is:

```text
Automatic
CPU
GPU Experimental
NPU Beta
```

R5c implements this display-only rename. `NPU Experimental` is now a legacy
user-facing label and compatibility term in diagnostics/docs, not the current
Settings label.

This does not remove or rename S1-S5 enum values, preference keys, diagnostics,
or developer phase overrides. It only identifies a later display-label cleanup:

- KEEP: compatibility references, developer phase controls, parser fixtures,
  and historical docs.
- DEPRECATE: any normal-user-facing interpretation of S1-S5 as independent
  hardware backends.
- CLEANUP_CANDIDATE: labels, comments, or docs that mention S1-S5 without
  `DEV` / `Legacy` context, and old Experimental display assertions after
  R5c.

See `docs/npu_settings_ux_label_review.md` for the option comparison and
recommendation.
