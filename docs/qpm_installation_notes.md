# Qualcomm Package Manager Installation Notes

Date: 2026-05-17

## Current Status

QPM is not installed on this host.

Probe artifact:

```text
artifacts/qpm_installation/20260517_085521/preinstall_probe.txt
```

Detected:

- `qpm`: not found
- `qpm-cli`: not found
- `qualcomm-package-manager`: not found
- `software-center`: not found
- local installer under `~/Downloads` or `/tmp`: not found

No LiteRT build was run, no app was installed, no native libraries were changed,
and no `Engine.initialize` or NPU inference was executed.

Safe catalog helper run:

```text
artifacts/qpm_search/20260517_085749/
```

Result:

- `qpm-cli` missing
- QAIRT catalog search not run
- QAIRT `2.44.0.260225` catalog presence remains unknown

## Official Installer Source

Qualcomm's current QPM3 documentation says:

1. Open the QPM Portal:

   ```text
   https://qpm.qualcomm.com/
   ```

2. Log in with Qualpass / Qualcomm credentials.
3. Open the Tools tab and filter for `QPM`.
4. Select `Qualcomm Package Manager 3`.
5. Select the latest version and download the Linux installer.
6. Install the downloaded Linux `.deb` with `dpkg` or `apt`.

The public QPM portal loads without credentials, but the Linux installer download
is behind the portal login. No unauthenticated official direct `.deb` URL was
identified in this pass.

Do not use unofficial mirrors or copied installers. Do not save Qualcomm login
tokens, cookies, or credentials in this repository.

## Expected Installer Names

QPM3 documentation uses the generic name `installer.deb`. Older public
ecosystem notes mention QPM2-style names such as:

```text
QualcommPackageManager.<version>.Linux-x86.deb
qualcommpackagemanager.lnx_<version>_installer_<build>.tar
```

For the current QAIRT path, prefer QPM3 from `https://qpm.qualcomm.com/` unless
Qualcomm documentation for your account specifically directs otherwise.

Recommended local download directory:

```text
/home/sato/Downloads/qpm/
```

## Ubuntu 24.04 Compatibility Notes

Qualcomm's QPM3 page describes Linux installation by `.deb`; it does not list a
specific Ubuntu 24.04 dependency set in the public page reviewed here.

Historical QPM2 workflows often required desktop/keyring compatibility packages
such as:

- `xterm`
- `libsecret` / `gnome-keyring` related packages
- `libnss3`
- OpenSSL compatibility libraries

Treat those historical notes as fallback clues only. For QPM3 on Ubuntu 24.04,
start with:

```bash
sudo apt install ./QualcommPackageManager*.deb
```

If dependency resolution fails, inspect the exact missing package names from
`apt`/`dpkg` and install only those required by the official installer.

## Safe Install Procedure

After downloading the official QPM Linux installer into `~/Downloads/qpm/`:

```bash
cd ~/Downloads/qpm
ls -lh
sudo apt install ./QualcommPackageManager*.deb
```

or, if the official download is a tar containing the `.deb`:

```bash
cd ~/Downloads/qpm
tar -xf qualcommpackagemanager*.tar
sudo apt install ./QualcommPackageManager*.deb
```

Then check:

```bash
command -v qpm-cli
qpm-cli --help
qpm-cli --version
```

Record:

- installed package version
- `qpm-cli` path
- `qpm` GUI path if present
- any missing dependencies

## Login and Catalog Search

Login is interactive and should not be logged with credentials:

```bash
qpm-cli --login
```

After login:

```bash
bash scripts/qpm_search_qairt_versions.sh
```

The helper masks obvious emails/tokens in logs and runs read-only catalog
commands only. It does not install QAIRT.

When `qpm-cli` is missing, the helper exits with status `0` and writes a
blocked summary under:

```text
artifacts/qpm_search/<timestamp>/
```

## QAIRT 2.44 Target

The target SDK remains:

```text
QAIRT 2.44.0.260225
```

If catalog search finds it, document:

- package name
- exact version string
- license activation requirement
- install/extract command
- install root

The desired final local QAIRT root is:

```text
/home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225
```

After acquisition:

```bash
bash scripts/stage_qairt244_sdk_from_download.sh ~/Downloads/<official-qairt-244-package-or-dir>
bash scripts/check_qairt244_sdk.sh \
  /home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225
```

Only after the SDK validates should the rebuild compare be run:

```bash
bash scripts/run_qairt244_rebuild_compare.sh
```

## Safety Rules

- do not run LiteRT/Bazel build during QPM installation
- do not install an app
- do not modify `app/src/**/jniLibs`
- do not copy SDK libraries into the repo
- do not run `Engine.initialize`
- do not run NPU inference
- do not create `Conversation` or `Session`
- do not call `generateResponse`
- do not set `selectedPath=npu`

## References

- Qualcomm Package Manager 3 portal: <https://qpm.qualcomm.com/>
- Qualcomm Package Manager 3 documentation: <https://qpm.qualcomm.com/assets/userguide/external/book/2.GettingStarted/2.UsingDesktopApp/UsingDesktopApp.html>
- Radxa QAIRT install notes: <https://docs.radxa.com/en/dragon/q6a/app-dev/npu-dev/qairt-install>
