# NPUエンジン生成時のQNN System API不一致

2026-10-17のCPU/NPU機能比較で、インストール済みAPKの独立C++ Conversation診断がエンジン生成時にSIGABRTで終了しました。Tombstoneの先頭フレームは `DispatchDelegate::CreateDelegateKernelInterface`、ネイティブ段階ログは `before_engine_create`、送信試行は0回でした。

端末の `log.tag=S` により通常のログ採取では理由が見えなかったため、`log.tag.litert=V` のみ一時的に指定して再現し、採取後に元の値に戻しました。以下のログを確認しました。

```
Qnn System library version 1.4.0 is mismatched.
The minimum supported version is 1.8.0.
Failed to set up QNN manager
Failed to initialize Dispatch API
Failed to create a dispatch delegate kernel: No usable Dispatch runtime found
```

Dispatchライブラリの欠落ではなく、Dispatchの要求とAPK内QNN runtimeの不一致です。既存APKのQNN7ライブラリは `config/standard_gpu_npu_runtime.json` の固定ハッシュとすべて異なりました。QAIRT 2.44.0.260225 SDKのQNN一式はすべて固定ハッシュと一致し、SDKヘッダのSystem APIは1.8でした。

## ビルド時の防止

GPU OpenCLの有効・無効とは独立して、NPU用JNIを梱包するStandard Debug／NPUを明示的に有効化したStandard Releaseでは、QNN7ライブラリの固定ハッシュを検証します。NPU用JNIがない明示的な非NPUビルドにはこの検証を要求しません。

```bash
python3 scripts/stage_standard_gpu_opencl.py --qnn-only --native-dir NATIVE_DIRECTORY
python3 scripts/stage_standard_gpu_opencl.py --qnn-only --apk CANDIDATE.apk
```

`--qnn-only` は読み取り検証専用です。OpenCLライブラリの有無やハッシュを要求せず、`--artifact` との併用を拒否します。既存のGPU+NPU全体検証は従来どおり全ライブラリを対象にします。

固定ハッシュの成功は梱包整合性の証拠です。NPU推論・ドライバ互換性・数値一致の証明にはなりません。実機確認には別パッケージ `io.github.ninbyo02.lami.npuvalidation.assistantprobe` を使い、通常のLAMIを上書きしません。

## 修正候補の実機結果

QAIRT 2.44のQNN一式だけを揃えた専用APKで、別々のscenarioIdによる2回の比較に合格しました。両回ともCPU/NPUは42→84と回答し、NPUはengine_created=true、send_success_count=2です。実機ログでコンテキストバイナリとSDKの2.44.0一致を確認しました。通常のLAMIやGPU経路はこの修正で更新・検証していません。記録は `docs/reviews/qairt244-runtime-alignment-20261017/` にあります。
