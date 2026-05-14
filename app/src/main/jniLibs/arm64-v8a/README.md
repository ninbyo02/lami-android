Local Qualcomm QNN/QAIRT libraries for LiteRT-LM NPU testing go here.

Do not commit vendor `.so` files. They are ignored by `.gitignore`.

Expected minimum candidates:

- `libQnnSystem.so`
- `libQnnHtp.so`
- `libQnnHtpPrepare.so`
- `libQnnHtpV*.so`
- a LiteRT Qualcomm dispatch API `.so` whose name contains `dispatch` and one of `litert`, `qnn`, or `qualcomm`

Run `./gradlew :app:printQnnNpuNativeLibStatus` before installing a build.
