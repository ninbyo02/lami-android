Local Qualcomm QNN/QAIRT libraries for LiteRT-LM NPU testing go here.

Do not commit vendor `.so` files. They are ignored by `.gitignore`.

Expected minimum candidates:

- `libQnnSystem.so`
- `libQnnHtp.so`
- `libQnnHtpPrepare.so`
- `libQnnHtpV*.so`
- a LiteRT Qualcomm dispatch API `.so`, preferably `libLiteRtDispatch_Qualcomm.so`; other names containing `dispatch`, `LiteRtDispatch`, `qnn`, or `qualcomm` are diagnostic candidates only

Run `./gradlew :app:printQnnNpuNativeLibStatus` before installing a build.
