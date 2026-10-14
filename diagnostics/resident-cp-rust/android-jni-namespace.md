# Android JNI Namespace Boundary

## Observed Failure And Scope

The SDK-only `app_process` retry stopped while Java loaded public vendor RPC:
`libvndksupport.so` was not found for `/vendor/lib64/libhidlbase.so` in `clns-1`.
The exception preceded nativeCreate/deviceCreate and all graph calls. Exit 137
accompanied the captured Java exception; it does not establish an OOM.

Adding `/vendor/lib64` to Java's search path is not equivalent to an installed
APK's vendor-namespace linkage. However, this must not be called exclusively an
app_process problem: `docs/litert_qairt244_qnn_status_14001.md` records an earlier
APK namespace failure resolving RPC from the HTP stub (`clns-9`). An installed
APK can also fail when its public-library declarations or runtime are wrong.
The new installed-app path has not been exercised on a device.

## Supported Path

Android API 31+ grants vendor libraries to packages through
`<uses-native-library>`. The existing standardDebug and standardNpuRuntime
manifests already request `libcdsprpc.so` with `required=false`, preserving
installation/CPU fallback when it is absent. A declaration does not make private
OS libraries public, nor prove that a particular ROM exports the needed links.

AOSP's normal ApplicationLoaders path passes the package's uses-library list to
libnativeloader, which filters public vendor libraries and links to the exported
vendor namespace. Its custom-classloader fallback has different setup. Current
AOSP source is an architectural comparison, not an inspection of this phone's
exact linker configuration. SDK 2.47's SampleAppSharedBuffer/IOTensor.cpp loads
`libcdsprpc.so` by SONAME and resolves the three rpcmem symbols.

The explicit debug-only `ResidentCpRustApplicationApi.create` uses the installed
application's existing ClassLoader and nativeLibraryDir. It does not create a
custom loader or set Java/LD search paths. It requires FLAG_EXTRACT_NATIVE_LIBS
and an actual nativeLibraryDir directory. APK-resident/unextracted libraries are
unsupported and rejected; merely containing the required libraries in a ZIP is
not sufficient. The adapter loads its JNI library and
matching HTP stub once, refuses bundled RPC/private-OS copies, and the C bridge
resolves RPC by SONAME before SDK providers/deviceCreate. The owning session
retains the RPC handle through context and shared-buffer release. No normal TTS
route or diagnostic component is registered; no native binary is auto-packaged.

References:
- https://developer.android.com/guide/topics/manifest/uses-native-library-element
- https://source.android.com/docs/core/permissions/namespaces_libraries
- https://android.googlesource.com/platform/art/+/refs/heads/main/libnativeloader/native_loader.cpp
- https://android.googlesource.com/platform/art/+/refs/heads/main/libnativeloader/library_namespaces.cpp
- Installed QAIRT 2.47: examples/QNN/SampleApp/SampleAppSharedBuffer/src/Utils/IOTensor.cpp
- https://developer.android.com/guide/topics/manifest/application-element#extractNativeLibs

## PC Regression Commands

Run from the repository root using the existing JDK, Rust, C compiler and SDK.
No installation or device/emulator invocation is needed:

```bash
./gradlew --offline :app:testStandardDebugUnitTest :app:testStandardReleaseUnitTest
cargo test --offline --locked --manifest-path diagnostics/resident-cp-rust/Cargo.toml --features ffi-host-test
python3 scripts/check_resident_cp_jni.py
QAIRT_SDK_ROOT="$SDK" cargo test --offline --locked --manifest-path diagnostics/resident-cp-rust/Cargo.toml --features qnn-sdk
python3 scripts/check_resident_cp_rpc_loader.py --sdk "$SDK"
```

The C loader test mocks every dlopen/dlsym/dlclose, calls lami_qnn_open itself,
observes SDK-library/provider entry counts, and covers unavailable RPC, each of
the three missing rpcmem symbols, and cleanup after partial provider loading.
The RPC failure cases must reach no SDK library/provider, which also rules out
deviceCreate. These are CPU control-flow tests, not SDK/device measurements.

## Next Authorized Device Check

1. Obtain explicit permission for a separate, silent application-JNI diagnostic
   and any required debug APK/test harness installation. Do not reuse app_process
   as proof of the installed application's namespace. Existing APK/model backups
   and their hashes are required; the original route must remain unchanged.
2. Prepare a dedicated debug runtime containing the diagnostic JNI library and
   matching SDK 2.47 HTP/System/V79 stub. Do not mix them with the existing 2.44
   stack in one process. No RPC, HIDL or VNDK libraries may be copied into the APK.
   Keep the official matching DSP skeleton and its supported deployment path.
   Explicitly enable extracted native packaging for this diagnostic artifact
   (modern AGP useLegacyPackaging=true), inspect its merged manifest and native
   inventory, and record FLAG_EXTRACT_NATIVE_LIBS from the actual target package.
   Do not assume the existing standardDebug configuration extracts libraries:
   its currently explicit legacy-packaging override is conditional release-only.
3. An explicit in-app Android test/harness should construct the factory from the
   target application Context, verify the original context/fixture hashes and
   run the existing 16-position first/reuse/cancel/reset/stale/fresh lifecycle
   contracts. The APK caller/harness and matching packaging are still device
   verification prerequisites, not completed or automatically enabled here.
4. Require the shared nonblocking device lock, all phone-hours/thermal/competition
   gates, bounded invocation and cleanup budget. Do not run during weekday
   08:00-19:30 JST; stop before an individually agreed earlier cutoff as well.
5. Record loader stage, deviceCreate result, context opens/graph counts, golden
   equality, cancellation/recovery, final process absence, preservation and lock
   release. Leave voice quality and real-time acceptance separate. If the vendor
   namespace still fails, capture the error and stop; do not copy private
   dependencies, use hidden namespace APIs or modify OS policy.

## Installed-App Acceptance Contract

All of the following are required for this bounded binding/lifecycle check:

- Fresh explicit authorization, physical Android HTP target, correct target
  application Context/ClassLoader, and extracted SDK 2.47-only runtime. A PC
  JVM, emulator, app_process result or report flag alone is not hardware proof.
- Record APK/context/fixture/library hashes, SDK core/system/build versions,
  matching DSP deployment, and the actual loader/deviceCreate result. Hash the
  immutable original context and all input/golden files before native loading.
- No audio, unchanged original APK/model/source identity, no security-policy
  changes, and no private-OS library copying. A diagnostic APK installation is
  a separate permission and must not be concealed as a no-install operation.
- Frozen 16-position first frame: one context open, 16 graph calls, 24 registered
  buffers; first hidden-output bytes exactly match the original golden fixture.
- Reset/reuse: still one context open, 32 cumulative graph calls and two resets;
  repeat hidden bytes match the same golden. No additional open is permitted.
- Partial frame/cancel/reset: one extra step plus 16 steps, giving 49 cumulative
  graph calls, four resets and one cancel. Golden bytes match again. Cancellation
  is between SDK steps, not an assertion of in-flight hardware preemption.
- Release rejects stale driver/token reuse. A fresh context executes one step
  successfully: its own counters show one open, one graph call and 24 registered
  buffers. Maximum aggregate planned execution is 50 graph calls.
- Preserve raw numeric outputs/logs locally, verify finite values and counters,
  and check final owned-process absence, preservation and shared-lock release
  before the agreed cutoff. Do not upload models, tensors, audio or raw logs.

Any loader error, wrong identity/version, counter mismatch, nonfinite output,
stale-token acceptance, unexpected graph count, preservation change or unresolved
process is a failure, not a partial pass. A failure before deviceCreate is zero
graph execution and cannot be described as context-reuse validation. Even a full
pass establishes only this frozen CP binding/lifecycle contract; Android Main,
heads/sampling, PCM pipeline, perceptual voice quality and real-time performance
remain separate unverified gates. The installed-app caller and artifact packaging
still need explicit implementation/build authorization; they were not deployed
or tested on a phone by the PC work documented here.
