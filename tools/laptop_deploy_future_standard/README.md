# Laptop `deploy-future-standard` successor package

This package adds exactly one persistent ForceCommand verb to the laptop build gate:

```text
deploy-future-standard <port>
```

The host is fixed to `192.168.3.19`. The port must use canonical ASCII decimal notation in `1..65535` (no leading zero). No arbitrary host, path, package, Git ref, shell, uninstall, or data-deletion operation is exposed.

## Fixed trust and deployment contract

- Active gate: `/usr/local/sbin/lami-build-gate`
- Required baseline SHA-256: `cb863e73ed1a72fe586dbe92d0a93eb413f666fca534b4a0cad0aaf060be0473`
- Installed extension: `/usr/local/libexec/lami-deploy-future-standard.py`
- Build account/repository: `lami-build`, `/home/lami-build/repos/lami-android`
- Existing lock: `/home/lami-build/.build.lock` (live `0664`; installer hardens it to `0600` while holding the lock)
- Exact origin: `https://github.com/ninbyo02/lami-android.git`
- Exact ref: `refs/heads/future`
- ADB client/server: `/opt/android-sdk/platform-tools/adb`, `tcp:127.0.0.1:5037`
- Exact endpoint: `192.168.3.19:<port>`
- Expected model: `NX733J`
- Package/activity: `io.github.ninbyo02.lami/io.github.ninbyo02.lami.MainActivity`
- QAIRT build extension: `/usr/local/libexec/lami-build-qairt244-forced-commands.sh`
- Required QAIRT extension SHA-256: `99631431604344db84bd09d185c81bd4533698054ebadf235b8df904d134659c`

The installer does **not** modify `authorized_keys`, `sshd_config`, or the QAIRT extension.

## What the deploy verb does

1. Acquires the existing mode-`0600` build lock without following symlinks and verifies owner, link count, and pathname/FD inode identity.
2. Verifies a clean repository and the exact origin URL.
3. Disables Git hooks/fsmonitor/credential helpers/file protocol, removes ignored build inputs, fetches only `origin`'s `future` branch, checks out the fetched commit detached, and cleans again.
4. Immediately after that final clean, opens the fixed root-owned QAIRT extension with `O_NOFOLLOW`, requires exact owner `root:root`, mode `0755`, link count one, pathname/FD identity, and the pinned SHA-256, then copies the exact opened bytes to a fully sealed memfd. A separate fixed wrapper is also sealed and invoked only as `/usr/bin/bash --noprofile --norc /proc/self/fd/<wrapper> /proc/self/fd/<extension>` with those two FDs inherited; no SSH/user argument or shell text reaches it.
5. Runs only `lami_qairt244_build_custom_jni` under the fixed clean environment and PATH, with a two-hour timeout and 16 MiB hard output cap. The extension's own temporary logs are confined to a private scratch directory that is removed and directory-fsynced on every exit; the bounded durable native log remains mode `0600` under `.deploy-logs`.
6. Requires the six fixed staged custom-stack libraries, hashes exact `openat(O_NOFOLLOW)` bytes through sealed memfds, and requires both fixed `nativeRunEditablePrompt` and `nativeRunPersistentProbe` GLOBAL/DEFAULT JNI symbols from the sealed `liblami_qairt244_npu_jni.so`. Missing files, unsafe metadata, hash drift, or missing symbols stop before Gradle.
7. Connects and probes only `192.168.3.19:<port>` with explicit `adb -s` selection and requires model `NX733J` plus a stable, non-empty `ro.serialno`.
8. Safely removes a stale fixed standard APK, creates fixed `local.properties` bytes for `/opt/android-sdk`, then runs only `:app:assembleStandardDebug` with a one-hour timeout and 4 MiB output cap; `local.properties` is rechecked after the build. Before accepting the APK, the runtime reopens/reseals every staged native library, rechecks both JNI symbols, and requires all post-Gradle hashes/evidence to match the pre-Gradle evidence.
9. Opens the fixed APK through a repository-anchored `dirfd/openat` chain with `O_NOFOLLOW`, copies it into an anonymous memfd, applies `F_SEAL_WRITE|GROW|SHRINK|SEAL`, and verifies the sealed FD hash.
10. Re-probes the exact device and writes/fsyncs a private provenance log containing the QAIRT extension hash, bounded native-log hash, every required staged-library hash, and fixed JNI-symbol evidence in addition to commit/APK/device identity. It then passes only the immutable APK FD to ADB as `/proc/self/fd/N` using `pass_fds`; the sealed FD hash is rechecked after ADB returns.
11. Re-probes after installation, clears fresh logcat, starts the fixed MainActivity, verifies exact launch output, one numeric PID and exact top-resumed activity, observes for 3 seconds, then rechecks bounded all-process/PID logcat, PID stability, and the exact foreground activity.
12. Closes all extension/wrapper/library/APK FDs and removes native scratch state on every exit path; no pathname APK snapshot is retained.

Any failed prerequisite exits nonzero. In particular, dirty/wrong-origin repositories, an unavailable or changed QAIRT extension, native build/stage failure, missing native symbol evidence, wrong or drifting devices, Gradle failure, unsafe APK paths, hash drift, install failure, launch failure, or fresh crash evidence never produce the success marker.

## Promotion HOLD (2026-08-10)

This package is a checkpoint and **must not be promoted to the live root gate yet**.

- A real Android Build Tools 35 ADB invocation rejected the otherwise correctly inherited sealed path `/proc/self/fd/N` with `filename doesn't end .apk or .apex`. The rejection occurred before installation. The manual acceptance install succeeded only through a private `.apk`-suffixed alias to the inherited sealed FD. That namespace alias is not accepted here as the automated solution because it has not yet preserved the original same-UID substitution boundary.
- An independent security architecture review requested a Git-tracked native-bundle manifest plus a root-managed content-addressed cache instead of invoking the existing QAIRT build extension on every deployment. The current post-clean QAIRT implementation and that stricter provenance design have not yet been reconciled.
- Therefore the generated successor, installer, and runtime in this directory are testable checkpoint artifacts only. Do not update `future`, run the root installer, or invoke the generated deploy successor from this checkpoint.

The reviewed palette APK was separately installed manually with sealed-FD SHA verification, matching signer verification, explicit NX733J serial selection, `adb install -r`, post-install byte-for-byte APK verification, cold launch, stable PID/top-resumed checks, a three-second fresh-crash window, and visual 7×4 palette acceptance. That manual acceptance does not clear this automation HOLD.

## Verify the package before privileged use

From this directory:

```bash
sha256sum -c SHA256SUMS
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -v -s tests -p 'test_*.py'
python3 -m py_compile deploy_future_standard.py install.py build_package.py tests/test_*.py
shellcheck generated/lami-build-gate.successor
bash -n generated/lami-build-gate.successor
```

`generated/lami-build-gate.successor` is produced and checked by `build_package.py`:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 build_package.py
sha256sum -c SHA256SUMS
```

## Install from the laptop's physical/admin console

First confirm the live gate is still the reviewed baseline:

```bash
sudo stat -Lc 'type=%F owner=%U:%G mode=%a links=%h' /usr/local/sbin/lami-build-gate
sudo sha256sum /usr/local/sbin/lami-build-gate
```

Expected SHA-256:

```text
cb863e73ed1a72fe586dbe92d0a93eb413f666fca534b4a0cad0aaf060be0473
```

Do not run the root installer directly from the `lami-build`-owned build repository. Copy the verified package to a private, mode-`0700`, `sato`-owned staging directory first. The extension file must be owned by the invoking sudo user and have exact mode `0644`; the installer verifies owner, mode, link count, pathname/FD inode identity, and its independently pinned SHA-256 before any destination change.

Then, from that verified `sato`-owned package directory:

```bash
sudo /usr/bin/python3 -I ./install.py
```

The no-argument installer requires root, exclusively locks the live `lami-build` build lock, verifies its exact owner/type/link count and live mode `0664`, hardens it to `0600`, verifies the gate baseline hash plus root ownership and exact mode `0755`, writes a fixed backup, installs the extension first and gate second via same-directory atomic replacements with file/directory fsync, and verifies final bytes and metadata. Any later failure restores the prior gate/extension and the lock's prior mode. Re-running performs idempotent byte/metadata verification and reports `status=already-installed`.

## Post-install verification

```bash
sudo stat -Lc 'type=%F owner=%U:%G mode=%a links=%h' \
  /usr/local/sbin/lami-build-gate \
  /usr/local/libexec/lami-deploy-future-standard.py
sudo sha256sum \
  /usr/local/sbin/lami-build-gate \
  /usr/local/libexec/lami-deploy-future-standard.py
```

Verify existing verbs still work and invalid variants remain denied through the existing forced-command SSH path. Examples that must be rejected include missing/extra arguments, leading-zero ports, whitespace variants, shell separators/substitutions, alternate hosts, and ports outside `1..65535`.

Run the only new verb with the current rotating Wireless Debugging port:

```text
deploy-future-standard 37123
```

Revalidate the current port immediately before use; the example port is not persistent.

## Scope of repository verification

The included tests use a byte-exact fixture of the reviewed baseline gate plus fake QAIRT build, command, device, and filesystem behavior. They verify the sealed-FD invocation boundary, exact post-clean/pre-Gradle ordering, required staged files/symbols/hashes, failure cleanup, and provenance fields. They do not contact SSH, ADB, GitHub, or a physical device, do not perform the real native build, and do not activate this package on the laptop.
