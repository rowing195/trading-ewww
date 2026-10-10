<div align="center" id="top">

# trading-ewww

<em>一眼看漲跌，看懂技術面，一鍵切下單</em>

<!-- BADGES -->
<img src="https://img.shields.io/github/v/release/rowing195/trading-ewww?style=flat&color=0080ff" alt="最新版本">
<img src="https://img.shields.io/github/last-commit/rowing195/trading-ewww?style=flat&color=0080ff" alt="最後更新">

<em>使用的工具與技術：</em>

<img src="https://img.shields.io/badge/Kotlin-7F52FF.svg?style=flat&logo=kotlin&logoColor=white" alt="Kotlin">
<img src="https://img.shields.io/badge/Jetpack%20Compose-4285F4.svg?style=flat&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose">
<img src="https://img.shields.io/badge/Android-34A853.svg?style=flat&logo=android&logoColor=white" alt="Android">
<img src="https://img.shields.io/badge/Gradle-02303A.svg?style=flat&logo=gradle&logoColor=white" alt="Gradle">
<img src="https://img.shields.io/badge/JUnit-25A162.svg?style=flat&logo=junit5&logoColor=white" alt="JUnit">
<img src="https://img.shields.io/badge/GitHub%20Actions-2088FF.svg?style=flat&logo=githubactions&logoColor=white" alt="GitHub Actions">

</div>
<br>

---

### 目錄

- [總覽](#總覽)
- [功能特色](#功能特色)
- [專案結構](#專案結構)
    - [專案索引](#專案索引)
- [快速開始](#快速開始)
    - [環境需求](#環境需求)
    - [安裝](#安裝)
    - [使用方式](#使用方式)
    - [測試](#測試)
- [資料來源與限制](#資料來源與限制)
- [授權](#授權)
- [致謝](#致謝)

---

## 總覽

trading-ewww 是自用的台股看盤 Android App：首頁一眼看自選股漲跌，點進去看 K 線與 KD、MACD、RSI 等技術指標，再用技術面摘要快速判讀偏多或偏空，底部一鍵切到券商 App 下單。行情來自富果行情 API，只要免費註冊富果會員就能使用。

**為什麼選 trading-ewww？**

這個專案把「看盤」和「下單」拆開：看盤用輕量、無廣告的 App，下單交給你原本的券商 App。核心功能：

- 🟥 **一眼看漲跌：** 自選股現價、漲跌點數與漲跌幅、漲跌家數比例條，每檔附一條以參考價為中心、左右各 10% 的今日走勢小條，漲停、跌停另外標示；可依自訂、漲幅或跌幅排序。
- 🟧 **完整 K 線：** 5 分／日／週／月 K，最多 5 條均線（預設含 MA200）、布林通道、成交量，副圖可切 KD／MACD／RSI；十字線、拖曳平移與雙指縮放，主圖與副圖同步。
- 🟪 **技術面摘要：** 依日 K 的均線排列、月線／季線／200 日線、KD、MACD、RSI、量能共 8 項訊號判讀偏多或偏空，並列出近期壓力與支撐。
- 🟨 **盤中自動更新：** 只在 App 位於前景且盤中才更新，頻率依清單長度自動調整，不超過免費額度。
- 🟩 **一鍵切券商：** 底部捷徑直接開啟券商 App，並順便複製股票代號。
- ⬛ **夜盤介面風格：** 深色優先、數字一律等寬對齊；紅綠只表示漲跌，錯誤用琥珀色，刪除前先確認。
- 🟦 **不登入、不記帳：** 不連券商、不存股數；API 金鑰以 Android Keystore 加密存在本機。

---

## 功能特色

|      | 元件 | 說明 |
| :--- | :--- | :--- |
| ⚙️ | **架構** | <ul><li>單一 Activity + Jetpack Compose，以字串路由切換首頁、K 線、技術面摘要、設定四個畫面</li><li>從技術面摘要返回 K 線頁時，以 `SaveableStateHolder` 保留原本的週期與副圖分頁</li><li>`AppViewModel` 集中管理報價、清單與 K 線快取，以 `StateFlow` 推送給畫面</li><li>`data/`（API、儲存、加密、指標計算）與 `ui/`（畫面、圖表）分層</li></ul> |
| 🔩 | **程式品質** | <ul><li>Kotlin 2.2、JVM 17</li><li>網路與 API 錯誤統一轉成中文訊息（`friendly()`）</li><li>協程取消例外一律往外拋，不被吞掉</li><li>release 開啟 R8 程式壓縮與資源縮減</li></ul> |
| 🔌 | **整合** | <ul><li>富果行情 REST API v1.0：即時報價、日內／歷史 K 線、上市櫃代號清單</li><li>`PackageManager` 列出手機已安裝 App 作為券商捷徑</li><li>剪貼簿：切到券商 App 前自動複製股票代號</li><li>GitHub Actions：在模擬器上點過各畫面並截圖</li></ul> |
| 🧩 | **模組化** | <ul><li>EMA、KD、MACD、RSI、布林通道為純函式（`Indicators.kt`）</li><li>技術面判讀獨立於 `TechSummary.kt`，不依賴 Android</li><li>主圖 `CandleChart` 與副圖 `IndicatorChart` 共用可視範圍與手勢，兩張圖自動對齊</li><li>交易時段判斷獨立於 `MarketClock`</li></ul> |
| 🧪 | **測試** | <ul><li>JUnit 4 單元測試涵蓋指標計算與技術面判讀</li><li>GitHub Actions 模擬器截圖 workflow，可用 `FUGLE_API_KEY` secret 拍有行情的畫面</li></ul> |
| ⚡️ | **效能** | <ul><li>同時最多 4 個 API 請求（`Semaphore(4)`）</li><li>日／週／月 K 快取 5 分鐘，技術面摘要沿用同一份日 K 快取</li><li>自動更新間隔依清單長度拉長，維持在每分鐘 60 次免費額度內</li><li>只在前景且盤中更新（`repeatOnLifecycle`）</li><li>K 線以 Compose `Canvas` 自繪，價格範圍依可視區間的 K 棒與鄰近均線計算</li></ul> |
| 🎨 | **介面風格** | <ul><li>「夜盤」風格：深色優先，底色、卡片、浮起元件三層表面，不用陰影</li><li>紅綠只表示漲跌；成功用重點藍、錯誤用琥珀，刪除類用中性外框並先確認</li><li>價格、漲跌、指標數值用內嵌的 IBM Plex Mono 等寬字，上下對齊</li><li>按鈕、開關、輸入框、設定列集中在 `Controls.kt`，各畫面共用</li></ul> |
| 🛡️ | **安全性** | <ul><li>API 金鑰以 Android Keystore 的 AES-256-GCM 金鑰加密後才寫入 DataStore</li><li>`allowBackup="false"`，設定與金鑰不進雲端備份</li><li>只要求 `INTERNET` 權限；不登入、不連券商帳戶</li></ul> |
| 📦 | **相依套件** | <ul><li>Compose BOM 2025.06.01、Material 3</li><li>AndroidX Core、Activity、Lifecycle、DataStore Preferences</li><li>kotlinx-coroutines 1.10.2</li><li>測試：JUnit 4.13.2</li><li>字型：IBM Plex Mono（SIL Open Font License 1.1，內嵌於 App）</li><li>網路用 `HttpURLConnection`、JSON 用內建 `org.json`，沒有第三方網路或 JSON 函式庫</li></ul> |

---

## 專案結構

```sh
└── trading-ewww/
    ├── .github
    │   ├── scripts
    │   └── workflows
    ├── README.md
    ├── app
    │   ├── build.gradle.kts
    │   ├── proguard-rules.pro
    │   └── src
    ├── build.gradle.kts
    ├── gradle
    │   └── wrapper
    ├── gradle.properties
    ├── gradlew
    ├── gradlew.bat
    ├── screenshots
    └── settings.gradle.kts
```

### 專案索引

<details open>
	<summary><b><code>TRADING-EWWW/</code></b></summary>
	<!-- __root__ Submodule -->
	<details>
		<summary><b>__root__</b></summary>
		<blockquote>
			<div class='directory-path' style='padding: 8px 0; color: #666;'>
				<code><b>⦿ __root__</b></code>
			<table style='width: 100%; border-collapse: collapse;'>
			<thead>
				<tr style='background-color: #f8f9fa;'>
					<th style='width: 30%; text-align: left; padding: 8px;'>檔案</th>
					<th style='text-align: left; padding: 8px;'>摘要</th>
				</tr>
			</thead>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/trading-ewww/blob/main/build.gradle.kts'>build.gradle.kts</a></b></td>
					<td style='padding: 8px;'>- 宣告整個專案使用的 Gradle 外掛版本：Android Gradle Plugin 8.11.1 與 Kotlin 2.2.0（含 Compose 編譯器外掛）。<br>- 外掛只在這裡定版，實際套用在 app 模組。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/trading-ewww/blob/main/settings.gradle.kts'>settings.gradle.kts</a></b></td>
					<td style='padding: 8px;'>- 設定外掛與依賴的下載來源（Google Maven、Maven Central），並納入唯一的 app 模組。<br>- 禁止在模組內另外宣告倉庫，所有依賴來源集中在這裡管理。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/trading-ewww/blob/main/gradle.properties'>gradle.properties</a></b></td>
					<td style='padding: 8px;'>- 設定 Gradle 執行參數：JVM 記憶體上限 2 GB、UTF-8 編碼。<br>- 啟用 AndroidX 與非遞移 R 類別，並採用 Kotlin 官方程式風格。</td>
				</tr>
			</table>
		</blockquote>
	</details>
	<!-- .github Submodule -->
	<details>
		<summary><b>.github</b></summary>
		<blockquote>
			<div class='directory-path' style='padding: 8px 0; color: #666;'>
				<code><b>⦿ .github</b></code>
			<table style='width: 100%; border-collapse: collapse;'>
			<thead>
				<tr style='background-color: #f8f9fa;'>
					<th style='width: 30%; text-align: left; padding: 8px;'>檔案</th>
					<th style='text-align: left; padding: 8px;'>摘要</th>
				</tr>
			</thead>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/trading-ewww/blob/main/.github/workflows/screenshots.yml'>workflows/screenshots.yml</a></b></td>
					<td style='padding: 8px;'>- 在 GitHub 的機器上編譯 debug APK、開 Android 模擬器並執行截圖腳本。<br>- 推到 `claude/` 開頭的分支或手動觸發時執行；非預設分支會把截圖 commit 回 `screenshots/`，main 只上傳成 artifact。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/trading-ewww/blob/main/.github/scripts/screenshots.py'>scripts/screenshots.py</a></b></td>
					<td style='padding: 8px;'>- 用 adb 與 uiautomator 安裝 App、依序點過各畫面並截圖。<br>- 有 `FUGLE_API_KEY` 時會先直接驗證金鑰、輸入後核對內容，再加入自選股拍報價、K 線與深色模式；沒有金鑰只拍未設定的畫面。</td>
				</tr>
			</table>
		</blockquote>
	</details>
	<!-- app Submodule -->
	<details>
		<summary><b>app</b></summary>
		<blockquote>
			<div class='directory-path' style='padding: 8px 0; color: #666;'>
				<code><b>⦿ app</b></code>
			<table style='width: 100%; border-collapse: collapse;'>
			<thead>
				<tr style='background-color: #f8f9fa;'>
					<th style='width: 30%; text-align: left; padding: 8px;'>檔案</th>
					<th style='text-align: left; padding: 8px;'>摘要</th>
				</tr>
			</thead>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/trading-ewww/blob/main/app/build.gradle.kts'>build.gradle.kts</a></b></td>
					<td style='padding: 8px;'>- 定義 App 的建置設定：版本 0.2.0（versionCode 4）、compileSdk 與 targetSdk 36、minSdk 26，並產生 BuildConfig 讓設定頁顯示版本。<br>- release 開啟 R8 壓縮與資源縮減，並以 debug 金鑰簽章方便自用直接安裝。<br>- 列出 Compose、DataStore、Lifecycle、協程等依賴，以及測試用的 JUnit。</td>
				</tr>
				<tr style='border-bottom: 1px solid #eee;'>
					<td style='padding: 8px;'><b><a href='https://github.com/rowing195/trading-ewww/blob/main/app/proguard-rules.pro'>proguard-rules.pro</a></b></td>
					<td style='padding: 8px;'>- 存放 R8 保留規則。<br>- 目前不需要額外規則：網路用 HttpURLConnection、JSON 用 Android 內建 org.json，沒有依賴反射的序列化。</td>
				</tr>
			</table>
			<!-- src Submodule -->
			<details>
				<summary><b>src/main</b></summary>
				<blockquote>
					<div class='directory-path' style='padding: 8px 0; color: #666;'>
						<code><b>⦿ app.src.main</b></code>
					<table style='width: 100%; border-collapse: collapse;'>
					<thead>
						<tr style='background-color: #f8f9fa;'>
							<th style='width: 30%; text-align: left; padding: 8px;'>檔案</th>
							<th style='text-align: left; padding: 8px;'>摘要</th>
						</tr>
					</thead>
						<tr style='border-bottom: 1px solid #eee;'>
							<td style='padding: 8px;'><b><a href='https://github.com/rowing195/trading-ewww/blob/main/app/src/main/AndroidManifest.xml'>AndroidManifest.xml</a></b></td>
							<td style='padding: 8px;'>- 宣告唯一的 MainActivity 與網路權限，以及 Android 11 以上查詢已安裝 App 所需的 queries，讓設定頁能列出券商 App。<br>- 關閉 allowBackup，避免設定與金鑰進入雲端備份。</td>
						</tr>
						<tr style='border-bottom: 1px solid #eee;'>
							<td style='padding: 8px;'><b><a href='https://github.com/rowing195/trading-ewww/blob/main/app/src/main/res/font'>res/font</a></b></td>
							<td style='padding: 8px;'>- 內嵌的 IBM Plex Mono（Regular、Medium、SemiBold），所有價格與指標數值都用它。</td>
						</tr>
						<tr style='border-bottom: 1px solid #eee;'>
							<td style='padding: 8px;'><b><a href='https://github.com/rowing195/trading-ewww/blob/main/app/src/main/assets/licenses/OFL-IBMPlexMono.txt'>assets/licenses/OFL-IBMPlexMono.txt</a></b></td>
							<td style='padding: 8px;'>- IBM Plex Mono 的 SIL Open Font License 1.1 條款，隨 App 一起發佈。</td>
						</tr>
					</table>
					<!-- tw.stockpeek Submodule -->
					<details>
						<summary><b>java/tw/stockpeek</b></summary>
						<blockquote>
							<div class='directory-path' style='padding: 8px 0; color: #666;'>
								<code><b>⦿ app.src.main.java.tw.stockpeek</b></code>
							<table style='width: 100%; border-collapse: collapse;'>
							<thead>
								<tr style='background-color: #f8f9fa;'>
									<th style='width: 30%; text-align: left; padding: 8px;'>檔案</th>
									<th style='text-align: left; padding: 8px;'>摘要</th>
								</tr>
							</thead>
								<tr style='border-bottom: 1px solid #eee;'>
									<td style='padding: 8px;'><b><a href='https://github.com/rowing195/trading-ewww/blob/main/app/src/main/java/tw/stockpeek/MainActivity.kt'>MainActivity.kt</a></b></td>
									<td style='padding: 8px;'>- App 進入點，負責首頁、K 線、技術面摘要、設定四個畫面的切換與返回鍵處理；從摘要返回 K 線頁時保留原本的週期與副圖。<br>- App 在前景時立即更新一次報價，盤中再依清單長度定時更新；離開前景即停止，不在背景消耗電量或 API 額度。</td>
								</tr>
								<tr style='border-bottom: 1px solid #eee;'>
									<td style='padding: 8px;'><b><a href='https://github.com/rowing195/trading-ewww/blob/main/app/src/main/java/tw/stockpeek/AppViewModel.kt'>AppViewModel.kt</a></b></td>
									<td style='padding: 8px;'>- 集中管理整個 App 的狀態：自選清單、即時報價、K 線快取、圖表設定與提示訊息。<br>- 批次抓報價並限制同時請求數，支援用代號或名稱新增股票，依週期分段抓取歷史 K 線，並把例外轉成易懂的中文訊息。</td>
								</tr>
							</table>
							<!-- data Submodule -->
							<details>
								<summary><b>data</b></summary>
								<blockquote>
									<div class='directory-path' style='padding: 8px 0; color: #666;'>
										<code><b>⦿ app.src.main.java.tw.stockpeek.data</b></code>
									<table style='width: 100%; border-collapse: collapse;'>
									<thead>
										<tr style='background-color: #f8f9fa;'>
											<th style='width: 30%; text-align: left; padding: 8px;'>檔案</th>
											<th style='text-align: left; padding: 8px;'>摘要</th>
										</tr>
									</thead>
										<tr style='border-bottom: 1px solid #eee;'>
											<td style='padding: 8px;'><b><a href='https://github.com/rowing195/trading-ewww/blob/main/app/src/main/java/tw/stockpeek/data/FugleClient.kt'>FugleClient.kt</a></b></td>
											<td style='padding: 8px;'>- 封裝富果行情 REST API，提供即時報價、日內與歷史 K 線，以及上市櫃代號清單。<br>- HTTP 錯誤轉成中文訊息，查無資料時回傳空清單，成交量統一換算成「張」。</td>
										</tr>
										<tr style='border-bottom: 1px solid #eee;'>
											<td style='padding: 8px;'><b><a href='https://github.com/rowing195/trading-ewww/blob/main/app/src/main/java/tw/stockpeek/data/SettingsStore.kt'>SettingsStore.kt</a></b></td>
											<td style='padding: 8px;'>- 用 DataStore 保存自選清單、券商捷徑、均線週期、成交量、最高最低價標記、布林通道、副圖分頁、漲跌配色與排序方式，並以 Flow 提供給 ViewModel。<br>- K 線相關設定可一鍵恢復預設；API 金鑰交給 KeyCipher 加密後才寫入。</td>
										</tr>
										<tr style='border-bottom: 1px solid #eee;'>
											<td style='padding: 8px;'><b><a href='https://github.com/rowing195/trading-ewww/blob/main/app/src/main/java/tw/stockpeek/data/KeyCipher.kt'>KeyCipher.kt</a></b></td>
											<td style='padding: 8px;'>- 以 Android Keystore 產生的 AES-256-GCM 金鑰加解密富果 API 金鑰，明文不落地。<br>- 加密用的金鑰由系統 Keystore 保管，App 資料裡只存密文。</td>
										</tr>
										<tr style='border-bottom: 1px solid #eee;'>
											<td style='padding: 8px;'><b><a href='https://github.com/rowing195/trading-ewww/blob/main/app/src/main/java/tw/stockpeek/data/MarketClock.kt'>MarketClock.kt</a></b></td>
											<td style='padding: 8px;'>- 以台北時區判斷台股交易時段，提供盤前、試撮、盤中、已收盤、休市等狀態文字。<br>- 平日 08:30–13:35 視為需要自動更新的時段；國定假日未特別處理。</td>
										</tr>
										<tr style='border-bottom: 1px solid #eee;'>
											<td style='padding: 8px;'><b><a href='https://github.com/rowing195/trading-ewww/blob/main/app/src/main/java/tw/stockpeek/data/Models.kt'>Models.kt</a></b></td>
											<td style='padding: 8px;'>- 定義全 App 共用的資料型別：自選股、券商捷徑、即時報價、K 棒、K 線週期、排序方式、副圖指標（KD、MACD、RSI）與設定。<br>- 包含預設均線週期（5、10、20、60、200）與台股代號格式規則。</td>
										</tr>
										<tr style='border-bottom: 1px solid #eee;'>
											<td style='padding: 8px;'><b><a href='https://github.com/rowing195/trading-ewww/blob/main/app/src/main/java/tw/stockpeek/data/Indicators.kt'>Indicators.kt</a></b></td>
											<td style='padding: 8px;'>- 計算技術指標：簡單與指數移動平均、KD（9, 3, 3）、MACD（12, 26, 9）、RSI（Wilder 平滑）、布林通道（20, 2）。<br>- 資料不足的位置填 NaN 讓圖表略過；另負責解析使用者輸入的均線設定，最多 5 條、每條 2–240 期。</td>
										</tr>
										<tr style='border-bottom: 1px solid #eee;'>
											<td style='padding: 8px;'><b><a href='https://github.com/rowing195/trading-ewww/blob/main/app/src/main/java/tw/stockpeek/data/TechSummary.kt'>TechSummary.kt</a></b></td>
											<td style='padding: 8px;'>- 依日 K 判讀 8 項技術訊號：均線排列、月線、季線、200 日線、KD、MACD、RSI 與量能，各自標記偏多、中性或偏空並附白話說明。<br>- 加總後分成強烈偏空到強烈偏多 5 級，同時算出 20／60 日高低點作為壓力與支撐。</td>
										</tr>
									</table>
								</blockquote>
							</details>
							<!-- ui Submodule -->
							<details>
								<summary><b>ui</b></summary>
								<blockquote>
									<div class='directory-path' style='padding: 8px 0; color: #666;'>
										<code><b>⦿ app.src.main.java.tw.stockpeek.ui</b></code>
									<table style='width: 100%; border-collapse: collapse;'>
									<thead>
										<tr style='background-color: #f8f9fa;'>
											<th style='width: 30%; text-align: left; padding: 8px;'>檔案</th>
											<th style='text-align: left; padding: 8px;'>摘要</th>
										</tr>
									</thead>
										<tr style='border-bottom: 1px solid #eee;'>
											<td style='padding: 8px;'><b><a href='https://github.com/rowing195/trading-ewww/blob/main/app/src/main/java/tw/stockpeek/ui/HomeScreen.kt'>HomeScreen.kt</a></b></td>
											<td style='padding: 8px;'>- 首頁自選股清單：漲跌家數卡與比例條，每檔顯示現價、漲跌點數、漲跌幅，以及以參考價為中心、左右各 10% 的今日走勢小條；漲停、跌停另外標示。<br>- 支援自訂、漲幅、跌幅排序與下拉更新，長按調整順序或刪除；右上角開啟可一次貼上多檔代號或名稱的新增對話框。</td>
										</tr>
										<tr style='border-bottom: 1px solid #eee;'>
											<td style='padding: 8px;'><b><a href='https://github.com/rowing195/trading-ewww/blob/main/app/src/main/java/tw/stockpeek/ui/DetailScreen.kt'>DetailScreen.kt</a></b></td>
											<td style='padding: 8px;'>- 個股 K 線頁：即時報價與開高低、參考價、量、振幅，下方以分段按鈕切換 5 分／日／週／月 K。<br>- 資訊列跟著十字線顯示該根 K 棒的價量與各均線數值，點均線標籤可暫時隱藏該條線；下方是 KD／MACD／RSI 副圖與進入技術面摘要的入口。<br>- 日 K 最後一根以即時報價補上。</td>
										</tr>
										<tr style='border-bottom: 1px solid #eee;'>
											<td style='padding: 8px;'><b><a href='https://github.com/rowing195/trading-ewww/blob/main/app/src/main/java/tw/stockpeek/ui/CandleChart.kt'>CandleChart.kt</a></b></td>
											<td style='padding: 8px;'>- 用 Compose Canvas 自繪的 K 線主圖：K 棒、成交量、均線、布林通道、可視範圍最高最低價、最新價虛線與十字線。<br>- 價格範圍依畫面內的 K 棒計算，離得不遠的均線一起納入；太遠的（例如 MA200）改在圖邊標出價位與方向。<br>- 定義主圖、副圖共用的可視範圍與手勢：單指平移、雙指縮放、點擊或長按叫出十字線。</td>
										</tr>
										<tr style='border-bottom: 1px solid #eee;'>
											<td style='padding: 8px;'><b><a href='https://github.com/rowing195/trading-ewww/blob/main/app/src/main/java/tw/stockpeek/ui/IndicatorChart.kt'>IndicatorChart.kt</a></b></td>
											<td style='padding: 8px;'>- K 線下方的副圖，畫 KD、MACD（含柱狀體）或 RSI 與 80／20、零軸等參考線。<br>- 跟主圖共用可視範圍，平移、縮放與十字線同步。</td>
										</tr>
										<tr style='border-bottom: 1px solid #eee;'>
											<td style='padding: 8px;'><b><a href='https://github.com/rowing195/trading-ewww/blob/main/app/src/main/java/tw/stockpeek/ui/IndicatorSheet.kt'>IndicatorSheet.kt</a></b></td>
											<td style='padding: 8px;'>- K 線頁右上角的指標設定面板：5 條均線週期、成交量、最高最低價標記、布林通道、副圖分頁與漲跌顏色。<br>- 開關立即生效，均線週期在按「完成」或關閉面板時才存，可一鍵恢復預設。</td>
										</tr>
										<tr style='border-bottom: 1px solid #eee;'>
											<td style='padding: 8px;'><b><a href='https://github.com/rowing195/trading-ewww/blob/main/app/src/main/java/tw/stockpeek/ui/SignalsScreen.kt'>SignalsScreen.kt</a></b></td>
											<td style='padding: 8px;'>- 技術面摘要頁，固定看日 K：綜合判讀等級與多空家數、各均線站上或跌破、KD／MACD／RSI 刻度與說明、量能比較，以及由高到低排列的壓力與支撐價位。<br>- 頁尾註明僅呈現指標狀態，不構成投資建議。</td>
										</tr>
										<tr style='border-bottom: 1px solid #eee;'>
											<td style='padding: 8px;'><b><a href='https://github.com/rowing195/trading-ewww/blob/main/app/src/main/java/tw/stockpeek/ui/SettingsScreen.kt'>SettingsScreen.kt</a></b></td>
											<td style='padding: 8px;'>- 設定頁：金鑰狀態、輸入與測試，尚未設定時有三步驟說明；清除金鑰前先確認。<br>- 券商捷徑可排序、移除，從底部面板搜尋手機上的 App 加入。<br>- K 線區顯示均線與副圖設定（點了打開指標設定面板）、各項開關與漲跌顏色；關於區顯示版本與資料來源。</td>
										</tr>
										<tr style='border-bottom: 1px solid #eee;'>
											<td style='padding: 8px;'><b><a href='https://github.com/rowing195/trading-ewww/blob/main/app/src/main/java/tw/stockpeek/ui/BrokerBar.kt'>BrokerBar.kt</a></b></td>
											<td style='padding: 8px;'>- 底部「下單」券商捷徑列，點一下直接開啟券商 App，在 K 線頁會順便把股票代號複製到剪貼簿；首頁另有管理捷徑的按鈕。<br>- 列出手機上可啟動的 App 供挑選，名稱像券商的排在前面。</td>
										</tr>
										<tr style='border-bottom: 1px solid #eee;'>
											<td style='padding: 8px;'><b><a href='https://github.com/rowing195/trading-ewww/blob/main/app/src/main/java/tw/stockpeek/ui/Components.kt'>Components.kt</a></b></td>
											<td style='padding: 8px;'>- 共用元件：分段切換按鈕、淡底狀態標籤、漲停跌停色塊標籤。<br>- 依訊號方向與判讀等級決定顏色，偏多、偏空跟著漲跌配色，過熱或超跌用提醒色。</td>
										</tr>
										<tr style='border-bottom: 1px solid #eee;'>
											<td style='padding: 8px;'><b><a href='https://github.com/rowing195/trading-ewww/blob/main/app/src/main/java/tw/stockpeek/ui/Controls.kt'>Controls.kt</a></b></td>
											<td style='padding: 8px;'>- 「夜盤」風格的共用元件：主要／次要／文字／刪除類按鈕、開關與開關列、漲跌顏色選項卡、提示列、設定區塊與設定列、標籤在框外的輸入框。<br>- 首頁、K 線頁、指標設定面板與設定頁都用這一套，外觀一致。</td>
										</tr>
										<tr style='border-bottom: 1px solid #eee;'>
											<td style='padding: 8px;'><b><a href='https://github.com/rowing195/trading-ewww/blob/main/app/src/main/java/tw/stockpeek/ui/AppIcons.kt'>AppIcons.kt</a></b></td>
											<td style='padding: 8px;'>- 補上 material-icons-core 沒有的線條圖示：排序、指標設定、開啟外部 App、金鑰、顯示／隱藏、垃圾桶、提醒、圖表等。</td>
										</tr>
										<tr style='border-bottom: 1px solid #eee;'>
											<td style='padding: 8px;'><b><a href='https://github.com/rowing195/trading-ewww/blob/main/app/src/main/java/tw/stockpeek/ui/Format.kt'>Format.kt</a></b></td>
											<td style='padding: 8px;'>- 統一價格、漲跌（含 ▲▼ 箭頭）、百分比、指標數值、張數與 K 棒時間的顯示格式，數字套用內嵌的 IBM Plex Mono 等寬字型以便對齊。<br>- 張數達 10 萬以上改用「萬」為單位。</td>
										</tr>
									</table>
									<!-- theme Submodule -->
									<details>
										<summary><b>theme</b></summary>
										<blockquote>
											<div class='directory-path' style='padding: 8px 0; color: #666;'>
												<code><b>⦿ app.src.main.java.tw.stockpeek.ui.theme</b></code>
											<table style='width: 100%; border-collapse: collapse;'>
											<thead>
												<tr style='background-color: #f8f9fa;'>
													<th style='width: 30%; text-align: left; padding: 8px;'>檔案</th>
													<th style='text-align: left; padding: 8px;'>摘要</th>
												</tr>
											</thead>
												<tr style='border-bottom: 1px solid #eee;'>
													<td style='padding: 8px;'><b><a href='https://github.com/rowing195/trading-ewww/blob/main/app/src/main/java/tw/stockpeek/ui/theme/Theme.kt'>Theme.kt</a></b></td>
													<td style='padding: 8px;'>- 定義「夜盤」風格的淺色與深色配色、漲跌顏色（可切換漲紅跌綠或漲綠跌紅）與提醒色；錯誤色改用琥珀，不跟漲跌搶紅綠。<br>- 定義圓角（8／12／16／22）與數字字型；五條均線的顏色分淺色、深色兩組，色塊上的文字自動挑對比較高的深色或白色。</td>
												</tr>
											</table>
										</blockquote>
									</details>
								</blockquote>
							</details>
						</blockquote>
					</details>
				</blockquote>
			</details>
			<!-- test Submodule -->
			<details>
				<summary><b>src/test</b></summary>
				<blockquote>
					<div class='directory-path' style='padding: 8px 0; color: #666;'>
						<code><b>⦿ app.src.test.java.tw.stockpeek.data</b></code>
					<table style='width: 100%; border-collapse: collapse;'>
					<thead>
						<tr style='background-color: #f8f9fa;'>
							<th style='width: 30%; text-align: left; padding: 8px;'>檔案</th>
							<th style='text-align: left; padding: 8px;'>摘要</th>
						</tr>
					</thead>
						<tr style='border-bottom: 1px solid #eee;'>
							<td style='padding: 8px;'><b><a href='https://github.com/rowing195/trading-ewww/blob/main/app/src/test/java/tw/stockpeek/data/IndicatorsTest.kt'>IndicatorsTest.kt</a></b></td>
							<td style='padding: 8px;'>- 驗證 EMA、RSI（Wilder 平滑）、KD 起始值與平滑、MACD、布林通道的計算結果。</td>
						</tr>
						<tr style='border-bottom: 1px solid #eee;'>
							<td style='padding: 8px;'><b><a href='https://github.com/rowing195/trading-ewww/blob/main/app/src/test/java/tw/stockpeek/data/TechSummaryTest.kt'>TechSummaryTest.kt</a></b></td>
							<td style='padding: 8px;'>- 驗證技術面判讀：資料不足時不判讀、穩定上漲判為強烈偏多、穩定下跌判為強烈偏空、不足 200 根時略過 200 日線，以及壓力支撐價位。</td>
						</tr>
					</table>
				</blockquote>
			</details>
		</blockquote>
	</details>
</details>

---

## 快速開始

### 環境需求

- **程式語言：** Kotlin
- **建置工具：** Gradle 8.14.3（使用專案內的 Gradle Wrapper）、Android Gradle Plugin 8.11.1
- **JDK：** 17
- **Android SDK：** compileSdk 36；手機需 Android 8.0（API 26）以上
- **行情金鑰：** 富果行情 API 金鑰。到 [富果開發者網站](https://developer.fugle.tw/docs/key/) 用富果會員登入申請，免費、不需要在任何券商開戶

### 安裝

**直接安裝 APK：** 到 [Releases](https://github.com/rowing195/trading-ewww/releases) 下載最新的 APK，用手機開啟安裝（第一次需允許「安裝不明來源的應用程式」）。已裝舊版可直接覆蓋更新，金鑰、清單與設定都會保留。

**從原始碼建置：**

1. **複製專案：**

    ```sh
    ❯ git clone https://github.com/rowing195/trading-ewww.git
    ```

2. **進入專案資料夾：**

    ```sh
    ❯ cd trading-ewww
    ```

3. **建置並安裝到已連線的手機或模擬器：**

    ```sh
    ❯ ./gradlew installDebug
    ```

    要產生 release APK（R8 壓縮，以 debug 金鑰簽章，自用可直接安裝）：

    ```sh
    ❯ ./gradlew assembleRelease
    ```

    產出位置：`app/build/outputs/apk/release/app-release.apk`。Windows 請改用 `gradlew.bat`；也可以直接用 Android Studio 開啟資料夾後按 ▶ Run。

### 使用方式

1. 開啟 App →「設定」，貼上富果 API 金鑰 →「儲存並測試」。
2. 回首頁按右上角 **+** 新增股票，代號或名稱都可以，一次可以貼好幾檔：`2330 0050 00878 鴻海`。長按股票可以調整順序或刪除，往下拉可以立即更新。
3. 設定 →「券商捷徑」→「從手機上的 App 加入」，搜尋並挑你的券商（可多選）。
4. 點股票進入 K 線頁：
    - 單指左右拖曳平移、雙指縮放，主圖與副圖一起動
    - 點一下或長按出現十字線，拖著看每根 K 棒；再點一下或按「最新」取消
    - 點均線標籤可以暫時隱藏該條均線；下方副圖可切換 KD／MACD／RSI
    - 右上角「指標設定」調整均線週期、成交量、最高最低價標記、布林通道、副圖分頁與漲跌顏色
    - 點「技術面」列進入技術面摘要，返回時會停在原本的週期與副圖
    - 點底部券商捷徑會順便複製股票代號，切過去貼上搜尋即可

### 測試

指標計算與技術面判讀有單元測試：

```sh
❯ ./gradlew testDebugUnitTest
```

推到 `claude/` 開頭的分支時，GitHub Actions 會在模擬器上點過各畫面並截圖（也可以在 Actions 頁手動執行）。在 repo 設定 `FUGLE_API_KEY` secret 才會拍到有報價與 K 線的畫面。

---

## 資料來源與限制

- 行情來自富果行情 API，免費方案日內與歷史行情各每分鐘 60 次。
- 盤中（平日 08:30–13:35）且 App 位於前景時才自動更新；從券商 App 切回來會立刻更新一次。
- 自動更新間隔最短 20 秒，清單超過 15 檔後依長度拉長（30 檔約 40 秒）。
- 歷史 K 線盤後 16:30 才更新；盤中的日 K 最後一根用即時報價補上，週 K、月 K 的當週、當月要等盤後。
- K 線資料範圍：5 分 K 約近 5 個交易日、日 K 約 2 年、週 K 約 5 年、月 K 約 10 年。月 K 的資料筆數不足 200 根，所以畫不出 MA200。
- 技術面摘要固定看日 K，至少要 60 根才判讀，不到 200 根時略過 200 日線；判讀用固定的 5／10／20／60／200 日均線，不跟著均線設定變動。
- 盤中的「今日量」是目前的累計量，早盤跟均量比較會偏低。
- 技術面摘要只呈現指標目前的狀態，不構成投資建議。
- 國定假日沒有特別處理，報價會停在前一個交易日。
- 金鑰以 Android Keystore 加密存在本機；清單與設定存在 DataStore，都不會備份到雲端。

---

## 授權

本專案目前沒有附上授權條款（LICENSE）。

內嵌的 IBM Plex Mono 字型以 SIL Open Font License 1.1 授權，條款見 [`app/src/main/assets/licenses/OFL-IBMPlexMono.txt`](app/src/main/assets/licenses/OFL-IBMPlexMono.txt)。

---

## 致謝

- 行情資料由 [富果 Fugle 行情 API](https://developer.fugle.tw/) 提供。
- 數字字型為 IBM 的 [IBM Plex Mono](https://github.com/IBM/plex)。

<div align="left"><a href="#top">回到頂端</a></div>

---
