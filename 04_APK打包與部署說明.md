# SmartHome Guardian — APK 打包與部署完整說明

---

## 1. 開發環境需求

| 工具 | 最低版本 | 推薦版本 |
|-----|---------|---------|
| Android Studio | Hedgehog (2023.1.1) | Ladybug (2024.2.1) |
| JDK | 17 | 17 (Temurin/Azul) |
| Gradle | 8.5 | 8.7+ |
| Android Gradle Plugin | 8.3 | 8.5 |
| minSdk | API 26 (Android 8.0) | — |
| targetSdk | API 35 (Android 15) | — |

---

## 2. 前置設定

### 2.1 克隆專案
```bash
git clone https://github.com/yourorg/smarthome-guardian.git
cd smarthome-guardian
```

### 2.2 設定 google-services.json
1. 進入 Firebase Console → 建立專案 → 新增 Android App
2. 套件名稱：`com.smarthome.guardian`
3. 下載 `google-services.json`
4. 放置於 `app/google-services.json`

### 2.3 網路安全設定（Certificate Pinning）
建立 `app/src/main/res/xml/network_security_config.xml`：
```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <domain-config cleartextTrafficPermitted="false">
        <domain includeSubdomains="true">api.smarthome-guardian.local</domain>
        <pin-set expiration="2026-01-01">
            <!-- 取得方式見步驟 2.4 -->
            <pin digest="SHA-256">你的憑證 PIN</pin>
            <pin digest="SHA-256">備用憑證 PIN</pin>
        </pin-set>
    </domain-config>
    <!-- 開發環境允許本機 HTTP -->
    <debug-overrides>
        <trust-anchors>
            <certificates src="user" />
        </trust-anchors>
    </debug-overrides>
</network-security-config>
```

### 2.4 取得憑證 SHA-256 PIN
```bash
# 方法一：從憑證檔案取得
openssl x509 -in server.crt -pubkey -noout | \
  openssl rsa -pubin -outform der 2>/dev/null | \
  openssl dgst -sha256 -binary | \
  base64

# 方法二：從 HTTPS 連線取得
openssl s_client -connect api.smarthome-guardian.local:443 -servername api.smarthome-guardian.local \
  </dev/null 2>/dev/null | \
  openssl x509 -pubkey -noout | \
  openssl rsa -pubin -outform der 2>/dev/null | \
  openssl dgst -sha256 -binary | base64
```

---

## 3. 簽署金鑰設定

### 3.1 產生 Release Keystore
```bash
# 產生金鑰庫（保存好！遺失即無法更新 APP）
keytool -genkey -v \
  -keystore app/keystore/release.jks \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias smarthome_guardian_key \
  -storepass 你的金鑰庫密碼 \
  -keypass 你的金鑰密碼 \
  -dname "CN=SmartHome Guardian, OU=Security, O=YourOrg, L=Taipei, ST=Taiwan, C=TW"
```

### 3.2 設定環境變數（不要把密碼放在程式碼！）

**macOS/Linux：**
```bash
export KEYSTORE_PATH=app/keystore/release.jks
export KEYSTORE_PASS=你的金鑰庫密碼
export KEY_ALIAS=smarthome_guardian_key
export KEY_PASS=你的金鑰密碼
```

**Windows（PowerShell）：**
```powershell
$env:KEYSTORE_PATH = "app\keystore\release.jks"
$env:KEYSTORE_PASS = "你的金鑰庫密碼"
$env:KEY_ALIAS = "smarthome_guardian_key"
$env:KEY_PASS = "你的金鑰密碼"
```

### 3.3 本地 keystore.properties（替代環境變數，.gitignore 忽略）
在專案根目錄建立 `keystore.properties`（加入 .gitignore）：
```properties
storeFile=app/keystore/release.jks
storePassword=你的金鑰庫密碼
keyAlias=smarthome_guardian_key
keyPassword=你的金鑰密碼
```

在 `app/build.gradle.kts` 讀取（修改 signingConfigs）：
```kotlin
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) load(file.inputStream())
}

signingConfigs {
    create("release") {
        storeFile = file(keystoreProps["storeFile"] ?: System.getenv("KEYSTORE_PATH") ?: "")
        storePassword = (keystoreProps["storePassword"] ?: System.getenv("KEYSTORE_PASS")) as String
        keyAlias = (keystoreProps["keyAlias"] ?: System.getenv("KEY_ALIAS")) as String
        keyPassword = (keystoreProps["keyPassword"] ?: System.getenv("KEY_PASS")) as String
    }
}
```

---

## 4. 打包 APK / AAB

### 4.1 使用 Android Studio（推薦 GUI 方式）

1. **Build** → **Generate Signed Bundle / APK**
2. 選擇 **APK** 或 **Android App Bundle（AAB，上架用）**
3. 選擇 Keystore 檔案 → 輸入密碼
4. 選擇 **release** build variant
5. 勾選 **V1（JAR Signature）** 和 **V2（Full APK Signature）** 和 **V3**
6. 點擊 **Finish**

輸出位置：`app/release/app-release.apk`

### 4.2 使用命令列（CI/CD 推薦）

```bash
# 打包 Release APK
./gradlew assembleRelease

# 打包 AAB（Google Play 上架）
./gradlew bundleRelease

# 輸出位置
# APK: app/build/outputs/apk/release/app-release.apk
# AAB: app/build/outputs/bundle/release/app-release.aab
```

### 4.3 驗證 APK 簽署
```bash
# 驗證 APK 簽章
apksigner verify --verbose app-release.apk

# 查看 APK 資訊
aapt dump badging app-release.apk | grep -E "versionName|versionCode|package"

# 查看簽署憑證
apksigner verify --print-certs app-release.apk
```

---

## 5. ProGuard / R8 混淆驗證

### 5.1 混淆後的 Mapping 檔案
每次 Release 打包都會在以下位置產生 mapping 檔：
```
app/build/outputs/mapping/release/mapping.txt
```

**務必保存此檔案！** 用於解析崩潰日誌的 Stack Trace。

### 5.2 解析崩潰日誌
```bash
# 使用 retrace 工具
retrace mapping.txt crash_stacktrace.txt

# 或使用 Gradle 任務
./gradlew retrace --mapping app/build/outputs/mapping/release/mapping.txt \
                  --stacktrace stacktrace.txt
```

### 5.3 驗證混淆效果
```bash
# 解壓 APK 後查看 classes.dex
apktool d app-release.apk -o decompiled
# 確認類別名稱已混淆（例如 a.b.c 而非原始名稱）
```

---

## 6. 安全性驗證清單（打包前必做）

### 6.1 靜態分析
```bash
# 執行 Android Lint 安全規則
./gradlew lintRelease

# 使用 MobSF（行動安全框架）分析
docker run -it --rm \
  -p 8000:8000 \
  -v $(pwd)/app-release.apk:/home/mobsf/Mobile-Security-Framework-MobSF/uploads/app-release.apk \
  opensecurity/mobile-security-framework-mobsf:latest

# 開啟瀏覽器 → http://localhost:8000
# 上傳 APK 進行自動化安全分析
```

### 6.2 必須通過的安全檢查
```
✅ allowBackup="false"（避免備份洩露資料）
✅ debuggable="false"（Release 版）
✅ FLAG_SECURE 設定（防截圖）
✅ Certificate Pinning 啟用
✅ Root 偵測正常運作
✅ 所有 HTTP 流量加密（無明文）
✅ Keystore 金鑰不硬編碼
✅ 敏感資訊不寫入日誌
✅ 網路安全設定 cleartextTrafficPermitted="false"
✅ 匯出 Activity/Service/Receiver 最小化（android:exported）
```

### 6.3 手動測試要點
```
✅ 安裝於 Root 設備 → 顯示警告並拒絕執行
✅ 安裝於模擬器 → 顯示警告
✅ 使用 Frida hook 嘗試繞過認證 → 應失敗
✅ 使用網路代理（Charles/Burp Suite）攔截流量 → Certificate Pinning 拒絕
✅ 強制終止 APP 後再開 → Token 正確保留
✅ 超過 15 分鐘不操作 → Token 自動更新或要求重新登入
```

---

## 7. CI/CD 流水線設定

### 7.1 GitHub Actions 範例
建立 `.github/workflows/release.yml`：
```yaml
name: Release APK

on:
  push:
    tags:
      - 'v*'

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Set up Android SDK
        uses: android-actions/setup-android@v3

      - name: Cache Gradle
        uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: gradle-${{ hashFiles('**/*.gradle*', '**/libs.versions.toml') }}

      - name: Run Tests
        run: ./gradlew test

      - name: Run Lint
        run: ./gradlew lintRelease

      - name: Build Release APK
        env:
          KEYSTORE_PASS: ${{ secrets.KEYSTORE_PASS }}
          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          KEY_PASS: ${{ secrets.KEY_PASS }}
        run: |
          echo "${{ secrets.KEYSTORE_BASE64 }}" | base64 --decode > app/keystore/release.jks
          export KEYSTORE_PATH=app/keystore/release.jks
          ./gradlew assembleRelease

      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: app-release
          path: app/build/outputs/apk/release/app-release.apk

      - name: Create GitHub Release
        uses: softprops/action-gh-release@v1
        with:
          files: app/build/outputs/apk/release/app-release.apk
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

### 7.2 GitHub Secrets 設定
在 GitHub Repo → Settings → Secrets and variables → Actions 新增：
```
KEYSTORE_BASE64   # base64 編碼的 release.jks（cat release.jks | base64）
KEYSTORE_PASS     # Keystore 密碼
KEY_ALIAS         # Key Alias
KEY_PASS          # Key 密碼
```

---

## 8. 版本管理

### 8.1 版本號規則
```
versionCode: 整數，每次發布 +1（例如 1, 2, 3...）
versionName: 語意版本號（例如 "1.0.0", "1.1.0"）
```

### 8.2 自動版本號（build.gradle.kts）
```kotlin
android {
    defaultConfig {
        // Git commit 數作為 versionCode
        val gitCommitCount = "git rev-list --count HEAD"
            .runCommand()?.trim()?.toIntOrNull() ?: 1
        versionCode = gitCommitCount

        // Git tag 作為 versionName
        val gitTag = "git describe --tags --abbrev=0"
            .runCommand()?.trim() ?: "1.0.0"
        versionName = gitTag.trimStart('v')
    }
}

fun String.runCommand(): String? = try {
    Runtime.getRuntime().exec(this).inputStream
        .bufferedReader().readText()
} catch (e: Exception) { null }
```

---

## 9. 安裝與測試

### 9.1 直接安裝（測試設備）
```bash
# 通過 ADB 安裝（Debug 版）
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 通過 ADB 安裝（Release 版，需啟用「未知來源」）
adb install app/build/outputs/apk/release/app-release.apk

# 無線安裝（ADB over WiFi）
adb tcpip 5555
adb connect 設備IP:5555
adb install app-release.apk
```

### 9.2 內部測試發布（Firebase App Distribution）
```bash
# 安裝 Firebase CLI
npm install -g firebase-tools
firebase login

# 上傳 APK 到 App Distribution
firebase appdistribution:distribute app-release.apk \
  --app YOUR_FIREBASE_APP_ID \
  --groups "beta-testers" \
  --release-notes "版本 1.0.0 發布說明"
```

### 9.3 Google Play 上架
1. 打包 **AAB**（`./gradlew bundleRelease`）
2. 進入 Google Play Console
3. 建立新 App → 設定套件名稱
4. 填寫 Store Listing（App 說明、截圖）
5. 上傳 AAB 至 **Production** 或 **Internal Testing**
6. 填寫資安問卷（Data Safety）
7. 提交審核

---

## 10. 疑難排解

### 問題：Build 失敗 - Keystore not found
```
解決：確認環境變數 KEYSTORE_PATH 指向正確路徑
     確認 .jks 檔案存在
```

### 問題：Certificate Pinning 導致 API 連不上
```
解決：
1. 確認 CertificatePinner 中的 SHA-256 PIN 正確
2. 暫時使用 Debug 版本（無 Pinning）測試
3. 確認伺服器憑證未更換
```

### 問題：APK 安裝失敗 - INSTALL_FAILED_UPDATE_INCOMPATIBLE
```
解決：
1. 舊版未使用相同簽署金鑰
2. 先移除舊版：adb uninstall com.smarthome.guardian
3. 重新安裝
```

### 問題：Root 偵測誤判（開發機）
```
解決：
在 BuildConfig.ENABLE_SECURITY_CHECKS = false（Debug 模式）
或在 SecurityChecker 中加入開發機白名單
```

### 問題：SQLCipher 資料庫無法開啟
```
解決：
1. 確認 SQLCipher 金鑰正確
2. 確認 CryptoManager 金鑰一致（Keystore Key Alias 未變）
3. 資料庫損毀時啟用 fallbackToDestructiveMigration()
```

---

## 11. 版本號清單

| 版本 | 日期 | 說明 |
|-----|------|------|
| 1.0.0 | 初版 | 基礎功能：認證、儀表板、設備管理 |
| 1.1.0 | — | 稽核日誌、存取控制 |
| 1.2.0 | — | MQTT 整合、即時監控 |
| 2.0.0 | — | Matter 協定支援、AI 異常偵測 |
