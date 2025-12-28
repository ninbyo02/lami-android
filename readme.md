# Ollama Mobile (Android) Client

A minimal and efficient Android client for running Ollama AI models on your device. Built using **Jetpack Compose**, this application provides a smooth and intuitive experience for interacting with AI models on mobile.

## Features

- **Lightweight & Fast**: Optimized for mobile devices with a minimal UI.
- **Runs Locally**: No cloud dependency, runs directly on your Android device.
- **User-Friendly Interface**: Simple yet powerful design for easy AI interaction.
- **Customizable Models**: Load and switch between different AI models.
- **Offline Support**: Works even without an internet connection.

## Screenshots

### Home Screen
<img src="Screenshots/01.png" width="250" />  <img src="Screenshots/02.png" width="250" />

### New Chat
<img src="Screenshots/03.png" width="250" />  <img src="Screenshots/04.png" width="250" />

### Chat Interface
<img src="Screenshots/05.png" width="250" />  <img src="Screenshots/06.png" width="250" />

<img src="Screenshots/07.png" width="250" />  <img src="Screenshots/08.png" width="250" />

### Settings

<img src="Screenshots/10.png" width="250" />  
<img src="Screenshots/11.png" width="250" />  

## Installation

1. **Download** the latest APK from [GitHub Releases](#).
2. **Install** the APK on your Android device.
3. **Launch the application** and start interacting with AI models.

## Requirements

- Android 13 or higher
- Minimum 4GB RAM (Recommended: 6GB+ for better performance)
- Ollama AI models installed on your device

## Usage

1. Open the application.
2. Load or switch between AI models.
3. Start a new conversation or continue an existing chat.
4. Adjust settings as needed for a personalized experience.

## Contributing

We welcome contributions! Feel free to **fork the repository** and submit **pull requests**.

### Contribution Guidelines
- Follow standard Android development best practices.
- Ensure UI/UX consistency with Jetpack Compose.
- Keep performance optimizations in mind.

## 開発環境セットアップ

ローカルでの確認と自動化フローに参加するための最小手順です。

1. 必要ツールのインストール
   ```bash
   pip install --upgrade pre-commit commitizen
   pre-commit install --hook-type pre-commit --hook-type commit-msg
   ```
2. Android SDK の準備（未設定の場合）
   ```bash
   sdkmanager --install "platform-tools" "platforms;android-34" "build-tools;34.0.0"
   ```
3. テスト実行
   ```bash
   ./gradlew test
   ```
4. 変更前の自動フォーマット
   ```bash
   pre-commit run --all-files
   ```

GitHub Actions でも `./gradlew test` を実行する CI を用意しているため、プルリクエスト作成時に自動でユニットテストが走ります。

## License

This project is licensed under the **MIT License**.

---

Developed with ❤️ using Jetpack Compose for Android.
