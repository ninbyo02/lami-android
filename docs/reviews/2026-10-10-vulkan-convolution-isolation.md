# Vulkan convolution isolation: host artifacts

```json
{
  "baseline": {
    "path": "/home/sato/project/lami-android-voice-dataset/qat/pcm-vulkan-20261009/hybrid-xnnpack/speech-decoder-fixed16-vulkan.pte",
    "size_bytes": 456562820,
    "sha256": "5aac6bebf8cdf35f7dd5029b74a1d9d1485b0a9113bd328b13b73438a14b0a72",
    "delegates": {
      "VulkanBackend": 30,
      "XnnpackBackend": 13
    }
  },
  "convolution_blocked": {
    "path": "/home/sato/project/lami-android-voice-dataset/qat/pcm-vulkan-convblock-20261010/speech-decoder-fixed16-vulkan-convblock.pte",
    "size_bytes": 456599044,
    "sha256": "02d91c99019beab090fd640f12995aaaa6ffdbad6fb43e27d7c9701f1828fbc0",
    "delegates": {
      "VulkanBackend": 53,
      "XnnpackBackend": 36
    }
  },
  "limitations": [
    "No device forward, numeric output parity, or audio quality established for new model",
    "Delegate counts alone do not prove execution speed or crash cause"
  ]
}
```

Next: test device loading only after backing up and verifying restoration of installed APK, voice manifest, and probe files. Do not change default TTS.
