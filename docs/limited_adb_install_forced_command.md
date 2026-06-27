# Limited ADB install over Hermes forced-command SSH

This note describes the prepared design for installing LAMI Android to a wireless-debugging device through the restricted `lami-build` PC account.

## Goal

Allow Hermes to perform only narrow ADB install operations without restoring broad shell access.

Allowed commands after deployment:

```text
adb-devices
qairt244-artifacts
stage-qairt244-custom-jni [artifact-dir-basename]
build-qairt244-custom-jni
install-future <10.5.5.3|192.168.52.52> <port> [standard|npuExperiment|galleryStackExperiment|galleryAlignedNpuProbe|customBuildExperiment]
```

## Repository-side changes

`update.sh update` accepts an allowlisted host:

```bash
./update.sh update --host 192.168.52.52 --port <wireless-debugging-port>
```

Allowed hosts are intentionally narrow:

```text
10.5.5.3
192.168.52.52
```

## PC deployment plan

The template is stored at:

```text
scripts/lami_build_remote_control_limited_adb.sh
```

To deploy it on the PC, merge its `adb-devices` / `install-future` / qairt244 artifact cases into:

```text
/home/lami-build/lami-build-control/remote_control.sh
```

Do not remove the existing build commands:

```text
status
build-branch <branch>
test-branch <branch>
logs
list-logs
help
```

Keep `authorized_keys` forced-command restrictions enabled:

```text
command="/home/lami-build/lami-build-control/remote_control.sh",no-port-forwarding,no-X11-forwarding,no-agent-forwarding,no-pty ...
```

## Usage after deployment

From Hermes:

```bash
ssh -i /opt/data/.ssh/lami_build_pc_ed25519 -p 2222 lami-build@192.168.52.99 adb-devices
ssh -i /opt/data/.ssh/lami_build_pc_ed25519 -p 2222 lami-build@192.168.52.99 qairt244-artifacts
ssh -i /opt/data/.ssh/lami_build_pc_ed25519 -p 2222 lami-build@192.168.52.99 stage-qairt244-custom-jni
ssh -i /opt/data/.ssh/lami_build_pc_ed25519 -p 2222 lami-build@192.168.52.99 build-qairt244-custom-jni
ssh -i /opt/data/.ssh/lami_build_pc_ed25519 -p 2222 lami-build@192.168.52.99 'install-future 192.168.52.52 <port> standard'
```

## qairt244 custom JNI commands

`qairt244-artifacts` lists local `artifacts/litert_custom_build/*` directories and marks whether each `built_libs/liblitertlm_jni.so` exports the required DEV-only JNI symbol:

```text
Qairt244ShortMultitokenSmoke_nativeRunEditablePrompt
```

`stage-qairt244-custom-jni [artifact-dir-basename]` stages a previously built artifact into:

```text
app/src/customBuildExperimentDebug/jniLibs/arm64-v8a
```

The `standardDebug` Gradle build overlays that same directory into the standard hidden NPU route. The staging command refuses artifacts whose `liblitertlm_jni.so` lacks the qairt244 JNI symbol, preventing an APK that would fail with `No implementation found ... nativeRunEditablePrompt`.

`build-qairt244-custom-jni` is the narrow rebuild+stage path. It uses fixed locations on the Build PC:

The command auto-detects the LiteRT-LM checkout and QAIRT SDK from the
`$HOME`, `/home/sato`, and `/home/lami-build` candidate paths documented in
`docs/build_pc_qairt244_forced_command_update.md`, then writes to
`$HOME/repos/lami-android/artifacts/litert_custom_build/<timestamp>_qairt244_128token_128input_utf8prompt`.
If LiteRT-LM is missing, the command clones
`https://github.com/google-ai-edge/LiteRT-LM.git`, checks out `v0.11.0`, resets
that external checkout, and applies
`patches/qairt244_litertlm_utf8_128token_128input.patch`. It does not download
QAIRT; QAIRT `2.44.0.260225` must already exist in one of the candidate paths.

It builds only the limited target list in `scripts/build_litert_custom_artifacts.sh`, verifies the qairt244 JNI symbol, then stages with `scripts/stage_litert_custom_build_stack_for_experiment.sh`.

## Safety notes

- `host` is allowlisted to avoid generic LAN scanning.
- `port` must be numeric and within `1..65535`.
- `flavor` is allowlisted.
- qairt244 artifact names are basename-only and must match `<YYYYMMDD>_<HHMMSS>_<label>`.
- qairt244 staging requires `readelf` to prove `Qairt244ShortMultitokenSmoke_nativeRunEditablePrompt` is exported before the artifact is accepted.
- The install command still relies on Android wireless debugging being enabled and trusted on the device.
- If the device has not previously trusted the PC, Android may require an on-device confirmation.
