# LiteRT / LiteRT-LM QAIRT 2.46 Source Ref Candidates

Date: 2026-05-17

Artifact:

```text
artifacts/litert_qairt246_ref_search/20260517_062055/
```

Build status:

- `bazel build`: not executed
- native artifacts generated: no
- app integration: no
- `Engine.initialize`: not executed
- NPU inference: not executed

## Summary

No public LiteRT or LiteRT-LM source ref with explicit QAIRT `2.46.0.260424`,
`260424`, or `260424121129` evidence was found in bounded QAIRT metadata
paths.

The current public LiteRT `origin/main` and the LiteRT ref pinned by current
LiteRT-LM `origin/main` both still advertise QAIRT `2.44.0.260225`.

## Candidate Table

| Candidate | Repo | Commit/tag | Evidence | Expected QAIRT | Confidence | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| Existing LiteRT-LM v0.11.0 baseline | LiteRT-LM | `v0.11.0` / `c87189528a758db32ead241f4fc9c64836398ee7` | `WORKSPACE` pins LiteRT `47615eb6eaec25e8dfcd1aba922c560a57cba0a2`; LiteRT metadata says `2.44.0.260225`. | `2.44.0.260225` | high for 2.44, none for 2.46 | Already built with a 2.46 overlay; runtime still failed. |
| Current LiteRT public main | LiteRT | `origin/main` / `de0a5af1a0eec5c72d109ec54fa7a9d6e1e3e87d` | `third_party/qairt/workspace.bzl`, `ci/tools/python/vendor_sdk/qualcomm/setup.py`, `litert/vendors/CMakeLists.txt`, and `litert/version.bzl` still reference `2.44.0.260225`. | `2.44.0.260225` | high for 2.44, none for 2.46 | Not a QAIRT 2.46 candidate. |
| Current LiteRT-LM public main | LiteRT-LM | `origin/main` / `f3c0a998e5ca0083aee5197e99eccb829f897b53` | `WORKSPACE` pins LiteRT `d865fd82cd7fe6752908b3a0836895461c305679`. That LiteRT commit also references QAIRT `2.44.0.260225`. | `2.44.0.260225` | high for 2.44, none for 2.46 | Useful for newer LiteRT-LM API work, but not for exact QAIRT 2.46 coupling. |
| Exact QAIRT 2.46 public ref | LiteRT / LiteRT-LM | not found | Bounded history search found no `2.46.0.260424`, `260424`, or `260424121129` in public QAIRT metadata refs. | unknown | none | No checkout/query target selected. |
| Public HEAD with local QAIRT 2.46 overlay | LiteRT / LiteRT-LM | possible only as manual experiment | No source metadata says it expects QAIRT 2.46. | source says 2.44, local overlay 2.46 | low | This is essentially another overlay experiment and does not solve generation-coupling confidence. |

## Evidence Files

- `summary.md`
- `litert_current_qairt_metadata.txt`
- `litert_exact_246_history_search.txt`
- `litert_qairt_version_change_log.txt`
- `litert_qairt_version_commits.txt`
- `litertlm_workspace_refs_by_tag.tsv`
- `litertlm_current_refs.txt`
- `litertlm_main_litert_ref_qairt_metadata.txt`

## Relevant Findings

LiteRT `origin/main`:

```text
de0a5af1a0eec5c72d109ec54fa7a9d6e1e3e87d
```

still contains:

```text
QAIRT_URL = .../2.44.0.260225/v2.44.0.260225.zip
QAIRT_CONTENT_DIR = 'qairt/2.44.0.260225'
strip_prefix = "qairt/2.44.0.260225"
```

LiteRT-LM `origin/main`:

```text
f3c0a998e5ca0083aee5197e99eccb829f897b53
```

pins LiteRT:

```text
d865fd82cd7fe6752908b3a0836895461c305679
```

That LiteRT commit also points at QAIRT `2.44.0.260225`.

## Classification

1. exact 2.46 evidence found: no
2. approximate post-2.44 / pre-latest candidate: no useful public candidate
3. public HEAD candidate: low confidence; still advertises QAIRT 2.44
4. no evidence: current result

## Decision

Do not build a QAIRT 2.46 source/ref candidate yet. There is no source-side
evidence that the public refs currently available are intended to pair with
QAIRT `2.46.0.260424`.

Recommended priority:

1. Continue trying to obtain exact QAIRT `2.44.0.260225`, because it matches
   the public LiteRT metadata.
2. Ask maintainers whether a QAIRT `2.46.0.260424` LiteRT source/ref exists
   publicly or internally.
3. Only if maintainers confirm a ref, run query/cquery on that candidate and
   then build into `artifacts/` for static comparison.
