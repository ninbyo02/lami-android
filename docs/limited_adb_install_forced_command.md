# Limited ADB install over Hermes forced-command SSH

This note describes the prepared design for installing LAMI Android to a wireless-debugging device through the restricted `lami-build` PC account.

## Goal

Allow Hermes to perform only narrow ADB install operations without restoring broad shell access.

Allowed commands after deployment:

```text
adb-devices
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

To deploy it on the PC, merge its `adb-devices` / `install-future` cases into:

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
ssh -i /opt/data/.ssh/lami_build_pc_ed25519 -p 2222 lami-build@192.168.52.99 'install-future 192.168.52.52 <port> standard'
```

## Safety notes

- `host` is allowlisted to avoid generic LAN scanning.
- `port` must be numeric and within `1..65535`.
- `flavor` is allowlisted.
- The install command still relies on Android wireless debugging being enabled and trusted on the device.
- If the device has not previously trusted the PC, Android may require an on-device confirmation.
