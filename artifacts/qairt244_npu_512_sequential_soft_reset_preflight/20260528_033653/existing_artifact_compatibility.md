# Existing Artifact Compatibility

- 256 clean artifact: `artifacts/qairt244_npu_max_output_256_three_prompt_compare/20260526_211856`
  - expected: all prompts can continue
- 512 sequential timeout artifact: `artifacts/qairt244_npu_max_output_512_three_prompt_codeaware_compare/20260527_014523`
  - expected: prompt 2 stops with TIMEOUT_SUSPECT
- 512 force-stop clean artifact: `artifacts/qairt244_npu_max_output_512_force_stop_between_prompts/20260527_074002`
  - expected: each run is SUCCESS_CLEAN as a clean reference

The force-stop artifact is a compatibility reference only. It does not authorize
sequential 512 baseline promotion.
