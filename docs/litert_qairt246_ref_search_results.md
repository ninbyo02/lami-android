# LiteRT QAIRT 2.46 Ref Search Results

Date: 2026-05-17

Artifact:

```text
artifacts/litert_qairt246_ref_search/20260517_062055/
```

## Searched Repositories

| Repository | Local checkout | Remote refs fetched | Notes |
| --- | --- | --- | --- |
| LiteRT | `/home/sato/project/litert-custom-build/LiteRT` | tags and `origin/main` | Current checkout remains unchanged at the LiteRT-LM v0.11.0 pinned commit. |
| LiteRT-LM | `/home/sato/project/litert-custom-build/LiteRT-LM` | tags and `origin/main` | Current checkout remains unchanged at `v0.11.0`. |

No checkout was switched, no worktree was modified outside fetch refs, and no
Bazel build was executed.

## Local QAIRT

Available local QAIRT:

```text
/home/sato/compose/qairt/workspace/sdk/qairt/2.46.0.260424
```

The previous custom build used this SDK through an overlay path while building
source that expected QAIRT `2.44.0.260225`.

## Source Metadata Result

LiteRT `v0.11.0` pinned ref:

```text
47615eb6eaec25e8dfcd1aba922c560a57cba0a2
```

expects QAIRT:

```text
2.44.0.260225
```

LiteRT public `origin/main`:

```text
de0a5af1a0eec5c72d109ec54fa7a9d6e1e3e87d
```

also expects QAIRT:

```text
2.44.0.260225
```

LiteRT-LM public `origin/main`:

```text
f3c0a998e5ca0083aee5197e99eccb829f897b53
```

pins LiteRT:

```text
d865fd82cd7fe6752908b3a0836895461c305679
```

That LiteRT ref also expects QAIRT:

```text
2.44.0.260225
```

## Exact QAIRT 2.46 Search Result

Bounded history search found no exact evidence for:

- `2.46.0.260424`
- `260424`
- `260424121129`

in the public LiteRT QAIRT metadata files:

- `ci/tools/python/vendor_sdk/qualcomm/setup.py`
- `third_party/qairt/workspace.bzl`
- `litert/vendors/CMakeLists.txt`
- `litert/version.bzl`
- `litert/google/npu_runtime_libraries/fetch_qualcomm_library.sh`
- `litert/google/npu_runtime_libraries/fetch_qualcomm_library_jit.sh`

## Query / Cquery

Query/cquery was not run for a QAIRT 2.46 candidate because no exact candidate
source ref was identified.

The existing query path remains available for a future confirmed candidate:

```bash
bash scripts/query_litert_custom_build_targets.sh \
  /home/sato/project/litert-custom-build/LiteRT-LM-qairt246-candidate
```

Safety status:

- `bazel build`: not run
- native artifacts generated: no
- app integration: no
- `Engine.initialize`: not run
- NPU inference: not run

## Recommendation

Current recommendation:

1. Prioritize exact QAIRT `2.44.0.260225` acquisition, because all public
   LiteRT refs inspected still expect that SDK.
2. If the user wants to continue with QAIRT 2.46, ask maintainers for the
   source/ref that corresponds to QAIRT `2.46.0.260424`.
3. Do not build public HEAD with a QAIRT 2.46 overlay as a high-confidence
   compatibility experiment. It would repeat the known overlay risk.

## Risk Analysis

The absence of source metadata for QAIRT 2.46 means a build using local QAIRT
`2.46.0.260424` would remain an overlay build. The previous overlay build
already produced same-source/tag LiteRT-LM artifacts, but
`Engine.initialize()` still failed with:

```text
Failed to create a dispatch delegate kernel: No usable Dispatch runtime found
```

Without a matching source/ref, another overlay build is unlikely to clarify
generation coupling enough to justify app insertion.
