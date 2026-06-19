# LiteRT-LM Backend.NPU SM8750 official and external findings

- 作成日: 2026-06-02
- 確認日: 2026-06-02
- 対象: LiteRT-LM `Backend.NPU` / `Engine.initialize` / Snapdragon 8 Elite SM8750 / QNN / FastRPC
- 作業範囲: 公式・外部調査のみ。コード、設定、QAIRT/QNN配置、fallback、ChatScreen、S1-S5、native libs差し替えは未変更。

## 結論

事実:

- Google LiteRTのQualcomm NPUページは、`CompiledModel` API経由でQualcomm AI Engine Direct / QNNをサポートし、対応SoCに「Snapdragon 8 Elite Mobile Platform (SM8750)」を明記している。
- Google LiteRT-LMのNPU実行ガイドは、Qualcomm NPU向けにSM8750用のGemma3-1B `.litertlm`を表に載せ、QAIRTランタイム、Qualcomm dispatch API、`LD_LIBRARY_PATH`、`ADSP_LIBRARY_PATH`を揃えて`--backend=npu`で実行する手順を示している。
- HuggingFaceの`litert-community/Gemma3-1B-IT`は、Samsung S25 UltraでLiteRT-LM with NPUのベンチマークを掲載している。これはSM8750世代のGalaxy S25 Ultra相当での実動例として強い。
- Qualcomm公式のSnapdragon 8 Elite製品ページは、SM8750世代のHexagon NPU、INT4/INT8/INT16/FP16対応、AI性能/効率向上を説明している。

推測:

- SM8750で`Backend.NPU`自体は実用可能。ただし、現時点で実用可能といえるのは「Google/Qualcommが想定するSoC別`.litertlm`、LiteRT-LM/LiteRT dispatch、QAIRT/QNN HTP/V79ランタイム、DSP skel/stub、環境変数が同一世代で揃う」場合に限られる。
- Lamiで見ている`Engine.initialize` SIGABRTは、公式情報と既存ローカル調査を合わせると、Java/Kotlin APIの呼び方よりも、dispatch/QNN/HTP/V79/modelの世代不整合、またはDSP側ライブラリ探索失敗に近い。

## 公式情報

### Google LiteRT

出典:

- https://ai.google.dev/edge/litert/next/npu
- https://ai.google.dev/edge/litert/next/qualcomm
- https://ai.google.dev/edge/litert/android
- 確認日: 2026-06-02

事実:

- LiteRT NPUガイドは、NPUをvendor固有コンパイラ/ランタイム/依存ライブラリの複雑さから抽象化する統一インターフェイスとして説明している。
- Qualcomm AI Engine Directは、`CompiledModel` APIでAOTとon-device compilationの両方をサポートする。
- Qualcomm対応SoCとしてSM8850、SM8750、SM8650、SM8550、SM8475、SM8450が列挙されている。
- Android NPU利用ではAPI level 31+、arm64-v8a、Qualcomm runtime libraries、Play Feature Delivery、`useLegacyPackaging = true`などが示されている。
- NPU runtime librariesの例に`qualcomm_runtime_v79`と`qualcomm_runtime_v81`が含まれる。
- LiteRTはNPU不可時のCPU/GPU fallbackを`CompiledModel` API側の機能として説明している。ただし、これはLiteRT-LM `Engine.initialize`がnative abortするケースの例外処理を保証するものではない。

推測:

- SM8750はQualcomm HTP世代としてv79 runtime/skel/stubを要求する可能性が高い。LiteRT公式のruntime library例にも`qualcomm_runtime_v79`があるため、LamiのSM8750実験でV79 payload世代が重要な切り分け軸になる。
- LiteRT-LM `Backend.NPU`が内部でLiteRT dispatch/QNNへ降りる場合、LiteRT単体のfallback説明だけでは`Engine.initialize`時のSIGABRT回避を期待しない方がよい。

### Google LiteRT-LM

出典:

- https://github.com/google-ai-edge/LiteRT-LM
- https://ai.google.dev/edge/litert/next/litert_lm_npu
- 確認日: 2026-06-02

事実:

- LiteRT-LM READMEは、ハードウェアアクセラレーションとしてGPU/NPUを掲げ、v0.7.0でGemma models向けNPU accelerationを追加したと記載している。
- 2026-06-02時点のREADME上の最新リリースはv0.12.0で、Gemma 4 E4BのMTP利用例は`--backend=gpu`で示されている。READMEのリリース一覧では、v0.12.0のCLI更新はCPU/GPU中心の記述で、Android Qualcomm NPUのアプリ組み込み手順そのものはNPU専用ガイド側に分離されている。
- LiteRT-LM NPUガイドは、Qualcomm AI Engine Direct向けに、SM8750/SM8650/SM8550別のGemma3-1B `.litertlm`を提示している。
- Qualcomm手順では、QAIRT SDKを入手し、`@litert//litert/vendors/qualcomm/dispatch:dispatch_api_so`をbuildし、QAIRTの`lib/aarch64-android`、LiteRT-LM prebuilt libraries、dispatch API、`litert_lm_main`を端末へpushする。
- 実行例は`LD_LIBRARY_PATH=$DEVICE_FOLDER ADSP_LIBRARY_PATH=$DEVICE_FOLDER ... --backend=npu --model_path=...`を明示している。
- ガイドは`SOC_MODEL=$(adb shell getprop ro.soc.model | tr '[:upper:]' '[:lower:]')`でSoC名をモデルURLに反映し、support tableにあるSoCか確認するよう求めている。

推測:

- LiteRT-LMのNPU実行は「Backend.NPUを選べば任意の`.litertlm`がNPU化される」設計ではなく、SoC別に用意/変換された`.litertlm`と対応するQNN/dispatch runtimeが必要。
- `Engine.initialize` SIGABRTが`qnn_partition_0`近辺で起きる場合、モデル内QNN partitionとランタイム世代の組み合わせを疑うべき。

### Google AI Edge Gallery

出典:

- https://github.com/google-ai-edge/gallery
- https://huggingface.co/litert-community/Gemma3-1B-IT
- https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm
- https://huggingface.co/google/gemma-3n-E2B-it-litert-lm
- 確認日: 2026-06-02

事実:

- Google AI Edge GalleryはGoogleのon-device ML/GenAI showcase appで、LiteRT-LMを使う実例としてLiteRT-LM READMEから案内されている。
- `litert-community/Gemma3-1B-IT`のModel Cardは、AndroidでGoogle AI Edge Gallery / MediaPipe経由、またはAndroid/DesktopでLiteRT-LM経由の利用を案内している。
- 同Model Cardの「Android via LiteRT LM with NPU」はSamsung S25 UltraでのNPU値を掲載している。Gemma3-1B、a16w4 QAT、context 1280、prefill 5836 tokens/sec、decode 85 tokens/sec、model size 689 MB。
- `gemma-4-E4B-it-litert-lm`のModel Cardは`.litertlm`形式、Android/iOS/Desktop/IoT/Web対応、Google AI Edge Gallery showcase appと同じ統合スタックであることを説明している。ただしAndroidの表はCPU/GPU、speculative decodingもCPU/GPUで、NPU値は掲載されていない。
- `google/gemma-3n-E2B-it-litert-lm`はLiteRT-LM benchmarkにVivo X300 Pro NPUの値を掲載しているが、Qualcomm SM8750ではない。

推測:

- Galleryで動くことは「同一モデルファイルだけで動く」ことを意味しない。Galleryが同梱/配布するnative stack、model allowlist、モデル取得先、ランタイム世代が一致している可能性がある。
- Gemma 4については、2026-06-02時点で公式HuggingFace上のAndroid NPU実測値はGemma3-1Bほど明確ではない。Gemma 4 Android実用はCPU/GPU/MTP経路の根拠が強く、Qualcomm NPU経路はSoC別ファイルとランタイム整合性の追加確認が必要。

### Qualcomm / QAIRT / QNN / FastRPC

出典:

- https://www.qualcomm.com/smartphones/products/8-series/snapdragon-8-elite-mobile-platform
- https://docs.qualcomm.com/doc/KBA-250421151446/KBA-250421151446_REV_1_QAIRT_2_33_0_Partner_Release_Notes.pdf
- https://github.com/qualcomm/fastrpc
- https://app.aihub.qualcomm.com/docs/hub/faq.html
- 確認日: 2026-06-02

事実:

- QualcommのSnapdragon 8 Elite公式ページは、Qualcomm Hexagon NPU、INT4/INT8/INT16/FP16対応、マルチモーダルGen AIサポートを説明している。
- QAIRT 2.33.0 Partner Release NotesにはGen AI feature compatibility表があり、SM8750がSM8650/SM8550等と並んで記載されている。既知問題としてHTPのION memory deregistration failureやLoRAv2 memRegister errorなど、HTP初期化/メモリ登録に近い領域の注意がある。
- Qualcomm FastRPCのREADMEは、FastRPCをCPUとDSP間のremote procedure call機構と説明している。DSPへ処理をoffloadするためのhost stub / DSP skelの仕組みがある。
- Qualcomm AI Hub FAQは、QNN `1008` / `QNN_COMMON_ERROR_INCOMPATIBLE_BINARIES`がHTP初期化失敗を示し、HTPで動くコードを含むライブラリが見つからないことが多いと説明している。多くのplatformで`ADSP_LIBRARY_PATH`がdevice DSP architectureに対応するskelファイルの場所を指す必要がある。

推測:

- LiteRT-LM Qualcomm NPU初期化では、`libQnnHtpV79Stub.so`などhost側stubだけでなく、対応するDSP skel payloadの探索が成功する必要がある。`ADSP_LIBRARY_PATH`、APK native lib extraction、`nativeLibraryDir`、`useLegacyPackaging`の差はSIGABRTやQNN初期化エラーの候補になる。
- QNN/HTPの初期化失敗は、例外としてJavaへ返らずnative abortになる実装経路があっても不自然ではない。

## GitHub Issues / external reports

出典:

- https://github.com/google-ai-edge/LiteRT/issues/5499
- https://github.com/google-ai-edge/LiteRT/issues/2791
- https://github.com/google-ai-edge/gallery/issues/557
- https://www.reddit.com/r/LocalLLaMA/comments/1sdeok0/how_to_run_ai_on_an_android_npu/
- https://www.reddit.com/r/LocalLLaMA/comments/1tc3czb/llms_on_flagships_smartphones/
- https://www.reddit.com/r/androiddev/comments/1t7zgtv/the_mess_of_using_a_local_llm_on_android_appkotlin/
- 確認日: 2026-06-02

事実:

- LiteRT issue #5499では、FastVLMをLiteRT/LiteRT-LMでQualcomm NPUへ載せる方法について、`CompiledModel`入力準備とLiteRT-LM `EngineConfig`でNPUがうまく動かないという相談がある。2026-01-28時点の外部利用者にとってNPU導線はまだ分かりにくい。
- LiteRT issue #2791では、LiteRT Next Androidで`Dispatch library directory is not set`、`NPU accelerator could not be loaded and registered`、`Compiler plugin is not configured`などのログの後、native crashした報告がある。これはLLMではないが、dispatch設定不備がnative crash周辺ログに現れる例として参考になる。
- Gallery issue #557では、Gemma-4-E4B-itで`Failed to create engine`、LiteRT-LM executor / LiteRT compiled modelの内部エラーが報告されている。端末はMediaTek Dimensity 9200+で、SM8750ではない。
- Redditでは、Galaxy S24 / SM8650で公式LiteRT-LM NPU手順に従い、QNN/LiteRT-LM libsを`/data/local/tmp`へ置き、`LD_LIBRARY_PATH`と`ADSP_LIBRARY_PATH`を設定してGemma3-1BをHexagon NPUで動かしたというユーザー報告がある。
- RedditのSnapdragon 8 Elite端末相談では、LiteRT-LM / Google AI Edge Galleryの利用が推奨され、Gemma 4 E2B/E4BのGPU実測報告はあるが、NPU実測とは区別されている。
- Android app開発者のReddit投稿では、LiteRT-LM Kotlin/AARでCPUは単純、GPU/NPU系は公開artifactやdriver/accelerator探索で詰まりやすいという報告がある。これは未検証の外部体験談であり、公式根拠ではない。

推測:

- 外部コミュニティでも、Qualcomm NPU成功例は「公式CLI手順 + `/data/local/tmp` + 明示的なlibrary path + SoC別Gemma3-1B」の形に寄っている。通常AndroidアプリへのAAR組み込みで同じ条件を再現するには、library extraction/path/model/runtime世代の管理が必要。
- `Failed to create engine`系の報告は複数あるが、SM8750 `Backend.NPU` `Engine.initialize` SIGABRTと同一原因だと断定できる公開Issueは見つからなかった。

## SM8750 / Snapdragon 8 EliteでのBackend.NPU実用可否

事実:

- SM8750はGoogle LiteRT Qualcomm NPUの対応SoCに含まれる。
- LiteRT-LM NPUガイドはSM8750向けGemma3-1B `.litertlm`を掲載している。
- HuggingFaceのGemma3-1B model cardは、Samsung S25 UltraでLiteRT-LM NPUベンチマークを掲載している。
- Qualcomm公式はSM8750世代のNPU性能とINT4等の対応を説明している。

判定:

- Gemma3-1B系: 実用可能と判断してよい。ただし、公式手順相当の完全なnative/model/runtime整合が前提。
- Gemma 4系: CPU/GPU/MTPの公式根拠は強いが、2026-06-02時点でQualcomm SM8750 NPUの公式model card実測はGemma3-1Bほど明確ではない。SM8750向け`.litertlm`がQNN partitionを含む場合でも、動作可否はモデル作成元とQNN/LiteRT-LM runtime世代の一致確認が必要。
- Lami現状: 既存ローカル調査のSIGABRT形状と外部根拠から、アプリコードの小変更で実用化できる段階ではなく、Galleryと同一世代のLiteRT-LM/LiteRT/QNN/V79 stackを一単位で検証する段階。

## Engine.initialize SIGABRTに関係しそうな既知情報

事実:

- LiteRT-LM Qualcomm NPU公式手順は`LD_LIBRARY_PATH`と`ADSP_LIBRARY_PATH`を同じdevice folderへ向ける。
- LiteRT NPU Androidデプロイ手順は、Qualcomm runtime librariesを配布し、`useLegacyPackaging = true`が必要と示している。
- Qualcomm AI Hub FAQは、HTP初期化失敗の典型原因としてDSP architecture対応のskelファイル探索失敗を挙げている。
- LiteRT issue #2791には、dispatch library directory未設定、NPU accelerator登録失敗、compiler plugin未設定のログがある。
- QAIRT release notesにはHTP memory registration / ION deregistration系の既知問題がある。

推測される原因候補:

1. `libLiteRtDispatch_Qualcomm.so`と`libLiteRt.so` / `liblitertlm_jni.so`のdispatch API layout/version不一致。
2. `libQnnSystem.so`、`libQnnHtp.so`、`libQnnHtpPrepare.so`、`libQnnHtpV79Stub.so`、`libQnnHtpV79Skel.so`の世代不一致。
3. SM8750向け`.litertlm`内のQNN partitionが、現在ロードされているQNN/HTP runtimeより新しい/古い。
4. APK内native library extractionや`nativeLibraryDir`の差により、DSP skel探索先が`ADSP_LIBRARY_PATH`上で見つからない。
5. QNN HTP初期化時のmemory registration / ION関連失敗。
6. Galleryが使うモデル/allowlist/runtimeと、Lamiのモデル/runtime取得元が一致していない。

優先して見るログ/証跡:

- `Engine.initialize`直前の`nativeLibraryDir`、`LD_LIBRARY_PATH`、`ADSP_LIBRARY_PATH`相当の値。
- tombstoneのabort message、register fragments、`liblitertlm_jni.so`内のdispatch/QNN関連文字列。
- `logcat`の`Qnn`, `HTP`, `FastRPC`, `cdsprpc`, `adsprpc`, `LiteRtDispatch`, `No usable Dispatch runtime`, `Unsupported dispatch runtime version`, `incompatible binaries`, `memRegister`。
- 実ロードされたlibrary build-idと、Gallery/公式手順で使うlibrary build-idの一致。
- `.litertlm`内の`qnn_partition_*`有無と、対象SoC文字列/HTP version文字列。

## Sources

| 種別 | URL | 確認日 | 使った事実 |
| --- | --- | --- | --- |
| Google LiteRT NPU | https://ai.google.dev/edge/litert/next/npu | 2026-06-02 | NPU概要、AOT/JIT、runtime libraries、fallback、Android要件 |
| Google LiteRT Qualcomm | https://ai.google.dev/edge/litert/next/qualcomm | 2026-06-02 | Qualcomm QNN対応、対応SoCにSM8750、開発要件 |
| Google LiteRT Android | https://ai.google.dev/edge/litert/android | 2026-06-02 | Android LiteRT APIと最新version表 |
| Google LiteRT-LM README | https://github.com/google-ai-edge/LiteRT-LM | 2026-06-02 | v0.12.0、NPU acceleration release、Gallery案内 |
| Google LiteRT-LM NPU guide | https://ai.google.dev/edge/litert/next/litert_lm_npu | 2026-06-02 | SM8750 model表、QAIRT、dispatch build、実行コマンド |
| Google AI Edge Gallery | https://github.com/google-ai-edge/gallery | 2026-06-02 | LiteRT-LM showcase app |
| HuggingFace Gemma3-1B | https://huggingface.co/litert-community/Gemma3-1B-IT | 2026-06-02 | Android/LiteRT-LM/NPU benchmark |
| HuggingFace Gemma 4 E4B | https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm | 2026-06-02 | `.litertlm`、Gallery stack、Android CPU/GPU benchmark |
| HuggingFace Gemma 3n E2B | https://huggingface.co/google/gemma-3n-E2B-it-litert-lm | 2026-06-02 | LiteRT-LM benchmark、NPU例 |
| Qualcomm Snapdragon 8 Elite | https://www.qualcomm.com/smartphones/products/8-series/snapdragon-8-elite-mobile-platform | 2026-06-02 | Hexagon NPU、INT4/INT8/INT16/FP16 |
| QAIRT 2.33.0 release notes | https://docs.qualcomm.com/doc/KBA-250421151446/KBA-250421151446_REV_1_QAIRT_2_33_0_Partner_Release_Notes.pdf | 2026-06-02 | SM8750 Gen AI compatibility、HTP known issues |
| Qualcomm FastRPC | https://github.com/qualcomm/fastrpc | 2026-06-02 | CPU-DSP RPC、stub/skel概念 |
| Qualcomm AI Hub FAQ | https://app.aihub.qualcomm.com/docs/hub/faq.html | 2026-06-02 | QNN incompatible binaries、HTP init、ADSP_LIBRARY_PATH |
| LiteRT issue #5499 | https://github.com/google-ai-edge/LiteRT/issues/5499 | 2026-06-02 | LiteRT/LiteRT-LM NPU利用相談 |
| LiteRT issue #2791 | https://github.com/google-ai-edge/LiteRT/issues/2791 | 2026-06-02 | dispatch directory未設定とnative crash例 |
| Gallery issue #557 | https://github.com/google-ai-edge/gallery/issues/557 | 2026-06-02 | Gemma 4 `Failed to create engine`外部報告 |
| Reddit Android NPU | https://www.reddit.com/r/LocalLLaMA/comments/1sdeok0/how_to_run_ai_on_an_android_npu/ | 2026-06-02 | SM8650で公式LiteRT-LM NPU手順成功の体験談 |
| Reddit flagship phones | https://www.reddit.com/r/LocalLLaMA/comments/1tc3czb/llms_on_flagships_smartphones/ | 2026-06-02 | Snapdragon 8 Elite端末でLiteRT-LM/GPU利用の体験談 |
| Reddit Android app Kotlin | https://www.reddit.com/r/androiddev/comments/1t7zgtv/the_mess_of_using_a_local_llm_on_android_appkotlin/ | 2026-06-02 | LiteRT-LM AAR利用体験談、CPU/GPU課題 |
