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

The installer does **not** modify `authorized_keys`, `sshd_config`, or the QAIRT extension.

## What the deploy verb does

1. Acquires the existing mode-`0600` build lock without following symlinks and verifies owner, link count, and pathname/FD inode identity.
2. Verifies a clean repository and the exact origin URL.
3. Disables Git hooks/fsmonitor/credential helpers/file protocol, removes ignored build inputs, fetches only `origin`'s `future` branch, checks out the fetched commit detached, cleans again, and creates fixed `local.properties` bytes for `/opt/android-sdk`.
4. Connects and probes only `192.168.3.19:<port>` with explicit `adb -s` selection.
5. Requires model `NX733J` and a stable, non-empty `ro.serialno`.
6. Safely removes a stale fixed standard APK, then runs only `:app:assembleStandardDebug` with a one-hour timeout and 4 MiB output cap; `local.properties` is rechecked after the build.
7. Opens the fixed APK through a repository-anchored `dirfd/openat` chain with `O_NOFOLLOW`, copies it into an anonymous memfd, applies `F_SEAL_WRITE|GROW|SHRINK|SEAL`, and verifies the sealed FD hash.
8. Re-probes the exact device, writes and fsyncs a private provenance log, then passes only that immutable FD to ADB as `/proc/self/fd/N` using `pass_fds`; the sealed FD hash is rechecked after ADB returns.
9. Re-probes after installation, clears fresh logcat, starts the fixed MainActivity, verifies exact launch output, one numeric PID and exact top-resumed activity, observes for 3 seconds, then rechecks bounded all-process/PID logcat, PID stability, and the exact foreground activity.
10. Closes the anonymous sealed APK FD on every exit path; no pathname APK snapshot is retained.

Any failed prerequisite exits nonzero. In particular, dirty/wrong-origin repositories, wrong or drifting devices, build failure, unsafe APK paths, hash drift, install failure, launch failure, or fresh crash evidence never produce the success marker.

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

The included tests use a byte-exact fixture of the reviewed baseline gate plus fake command/device/filesystem behavior. They do not contact SSH, ADB, GitHub, or a physical device, and they do not activate this package on the laptop.
