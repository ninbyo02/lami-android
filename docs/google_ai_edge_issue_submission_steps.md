# Google AI Edge Issue Submission Steps

Date: 2026-05-17

## Target

Primary repository:

```text
google-ai-edge/LiteRT-LM
```

Cross-link candidates, only if maintainers ask:

```text
google-ai-edge/LiteRT
google-ai-edge/gallery
```

## Title

Use:

```text
[Android][SM8750][Backend.NPU] Engine.initialize SIGABRT: No usable Dispatch runtime found with same-source custom stack
```

## Steps

1. Open `https://github.com/google-ai-edge/LiteRT-LM/issues`.
2. Click `New issue`.
3. Paste the title above.
4. Paste the body from:

   ```text
   docs/google_ai_edge_issue_body_litertlm_sm8750_npu.md
   ```

5. Attach the latest light bundle:

   ```text
   artifacts/npu_issue_bundle/<timestamp>_light.zip
   ```

6. Add labels if available:
   - `Android`
   - `Backend.NPU`
   - `Qualcomm`
   - `QNN`
   - `SM8750`
   - `crash`
   - `dispatch-runtime`
7. Do not attach the 193MB full bundle initially.
8. Do not attach APKs, model files, QNN SDK libraries, or native `.so` files unless maintainers explicitly request them and licensing permits.

## If Maintainers Ask For More

Provide, as requested:

- full tombstone/dropbox/logcat extracts
- full artifact bundle
- source/build helper scripts
- exact built native stack metadata
- QAIRT 2.44 acquisition status
- QAIRT 2.46 source/ref search artifacts

Prepared references:

```text
docs/litert_qnn_qairt_coupling_findings.md
docs/litert_qairt246_ref_search_results.md
docs/qairt_244_acquisition_notes.md
docs/litert_custom_build_qairt244_compare.md
```

## After Posting

Record the issue URL in this file or a follow-up tracking doc once available.

Do not post from automation in this repository. The actual GitHub issue should be created manually after final review.
