# QPM QAIRT 2.44 Install Workflow

Date: 2026-05-17

## Status

Blocked at QPM acquisition/login:

- `qpm-cli` is not installed locally.
- no official QPM Linux installer was found locally.
- the QPM portal requires Qualcomm/Qualpass login for installer download.
- no QAIRT install was attempted.

Current artifacts:

```text
artifacts/qpm_installation/20260517_085521/
artifacts/qpm_search/20260517_085749/
```

## Goal

Use Qualcomm Package Manager to confirm and acquire the exact QAIRT SDK expected
by public LiteRT Qualcomm metadata:

```text
QAIRT 2.44.0.260225
```

## Workflow

### 1. Install QPM

Download the official Linux QPM3 installer from:

```text
https://qpm.qualcomm.com/
```

Use the Tools tab and search for:

```text
QPM
Qualcomm Package Manager 3
```

Install the official `.deb`:

```bash
cd ~/Downloads/qpm
sudo apt install ./QualcommPackageManager*.deb
```

### 2. Confirm CLI

```bash
command -v qpm-cli
qpm-cli --version
qpm-cli --help
```

### 3. Login

```bash
qpm-cli --login
```

Do not capture or commit credentials, cookies, or tokens.

### 4. Search Catalog

```bash
bash scripts/qpm_search_qairt_versions.sh
```

Manual search terms if needed:

```text
qairt
Qualcomm AI Runtime
Qualcomm_AI_Runtime_SDK
2.44
2.44.0.260225
260225
```

Expected catalog decision:

- if QAIRT `2.44.0.260225` is present, record the exact package id and license
  activation requirement before installing;
- if only newer QAIRT versions are present, ask maintainers whether a public
  LiteRT source/ref matches that generation;
- if QAIRT `2.44.0.260225` is absent, continue with the official issue path.

### 5. License Activation

If QPM reports a license activation requirement, record the product identifier
but do not paste credentials into repo logs.

Known historical patterns look like:

```bash
qpm-cli --license-activate <product-id>
```

Use only the product id returned by the official QPM catalog for QAIRT.

### 6. Install / Extract Target SDK

Install or extract into a staging/download location first. The final desired
root is:

```text
/home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225
```

If QPM produces a zip or extracted SDK directory:

```bash
bash scripts/stage_qairt244_sdk_from_download.sh ~/Downloads/<official-qairt-244-package-or-dir>
```

### 7. Verify

```bash
bash scripts/check_qairt244_sdk.sh \
  /home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225
```

### 8. Rebuild Compare

Only after verification:

```bash
bash scripts/run_qairt244_rebuild_compare.sh
```

That wrapper performs build/static comparison only. It does not insert artifacts
into any app flavor.

## Expected Outputs

QPM search:

```text
artifacts/qpm_search/<timestamp>/
```

SDK check:

```text
artifacts/qairt244_sdk_check/<timestamp>/
```

Rebuild compare:

```text
artifacts/litert_custom_build/<timestamp>_qairt244/
artifacts/qairt244_rebuild_compare/<timestamp>/
```

## Do Not Do During QPM Workflow

- no LiteRT/Bazel build until QAIRT 2.44 SDK verifies
- no app install
- no `jniLibs` changes
- no native library replacement
- no `Engine.initialize`
- no NPU inference
- no `Conversation`
- no `Session`
- no `generateResponse`
- no `selectedPath=npu`
