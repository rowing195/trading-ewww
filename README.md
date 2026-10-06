<div align="center" id="top">

# trading-ewww

<em>一眼看漲跌，一鍵切下單</em>

<!-- BADGES -->
<em>使用的工具與技術：</em>

<img src="https://img.shields.io/badge/Kotlin-7F52FF.svg?style=flat&logo=kotlin&logoColor=white" alt="Kotlin">
<img src="https://img.shields.io/badge/Jetpack%20Compose-4285F4.svg?style=flat&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose">
<img src="https://img.shields.io/badge/Android-34A853.svg?style=flat&logo=android&logoColor=white" alt="Android">
<img src="https://img.shields.io/badge/Gradle-02303A.svg?style=flat&logo=gradle&logoColor=white" alt="Gradle">

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

trading-ewww 是自用的台股看盤 Android App：首頁一眼看自選股漲跌幅，點進去看 K 線，底部一鍵切到券商 App 下單。行情來自富果行情 API，只要免費註冊富果會員就能使用。

**為什麼選 trading-ewww？**

這個專案把「看盤」和「下單」拆開：看盤用輕量、無廣告的 App，下單交給你原本的券商 App。核心功能：

- 🟥 **一眼看漲跌：** 自選股現價、漲跌／漲跌幅與上漲、平盤、下跌家數，可依自訂、漲幅或跌幅排序。
- 🟧 **完整 K 線：** 5 分／日／週／月 K，最多 5 條均線（預設含 MA200）、成交量、十字線、拖曳平移與雙指縮放。
- 🟨 **盤中自動更新：** 只在 App 位於前景且盤中才更新，頻率依清單長度自動調整，不超過免費額度。
- 🟩 **一鍵切券商：** 底部捷徑直接開啟券商 App，並順便複製股票代號。
- 🟦 **不登入、不記帳：** 不連券商、不存股數；API 金鑰以 Android Keystore 加密存在本機。

---

## 功能特色

|      | 元件 | 說明 |
| :--- | :--- | :--- |
| ⚙️ | **架構** | <ul><li>單一 Activity + Jetpack Compose，以字串路由切換首頁、K 線、設定三個畫面</li><li>`AppViewModel` 集中管理報價、清單與 K 線快取，以 `StateFlow` 推送給畫面</li><li>`data/`（API、儲存、加密）與 `ui/`（畫面、圖表）分層</li></ul> |
| 🔩 | **程式品質** | <ul><li>Kotlin 2.2、JVM 17</li><li>網路與 API 錯誤統一轉成中文訊息（`friendly()`）</li><li>協程取消例外一律往外拋，不被吞掉</li><li>release 開啟 R8 程式壓縮與資源縮減</li></ul> |
| 🔌 | **整合** | <ul><li>富果行情 REST API v1.0：即時報價、日內／歷史 K 線、上市櫃代號清單</li><li>`PackageManager` 列出手機已安裝 App 作為券商捷徑</li><li>剪貼簿：切到券商 App 前自動複製股票代號</li></ul> |
| 🧩 | **模組化** | <ul><li>均線計算與均線設定解析為純函式（`Indicators.kt`）</li><li>交易時段判斷獨立於 `MarketClock`</li><li>`CandleChart` 只吃 K 棒與均線資料，不依賴 ViewModel</li></ul> |
| ⚡️ | **效能** | <ul><li>同時最多 4 個 API 請求（`Semaphore(4)`）</li><li>日／週／月 K 快取 5 分鐘</li><li>自動更新間隔依清單長度拉長，維持在每分鐘 60 次免費額度內</li><li>只在前景且盤中更新（`repeatOnLifecycle`）</li><li>K 線以 Compose `Canvas` 自繪，價格範圍只依可視區間計算</li></ul> |
| 🛡️ | **安全性** | <ul><li>API 金鑰以 Android Keystore 的 AES-256-GCM 金鑰加密後才寫入 DataStore</li><li>`allowBackup="false"`，設定與金鑰不進雲端備份</li><li>只要求 `INTERNET` 權限；不登入、不連券商帳戶</li></ul> |
| 📦 | **相依套件** | <ul><li>Compose BOM 2025.06.01、Material 3</li><li>AndroidX Core、Activity、Lifecycle、DataStore Preferences</li><li>kotlinx-coroutines 1.10.2</li><li>網路用 `HttpURLConnection`、JSON 用內建 `org.json`，沒有第三方網路或 JSON 函式庫</li></ul> |

---

## 專案結構

```sh
└── trading-ewww/
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
					<td style='padding: 8px;'>- 定義 App 的建置設定：版本 0.0.2、compileSdk 與 targetSdk 36、minSdk 26。<br>- release 開啟 R8 壓縮與資源縮減，並以 debug 金鑰簽章方便自用直接安裝。<br>- 列出 Compose、DataStore、Lifecycle、協程等依賴。</td>
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
									<td style='padding: 8px;'>- App 進入點，負責首頁、K 線、設定三個畫面的切換與返回鍵處理。<br>- App 在前景時立即更新一次報價，盤中再依清單長度定時更新；離開前景即停止，不在背景消耗電量或 API 額度。</td>
								</tr>
								<tr style='border-bottom: 1px solid #eee;'>
									<td style='padding: 8px;'><b><a href='https://github.com/rowing195/trading-ewww/blob/main/app/src/main/java/tw/stockpeek/AppViewModel.kt'>AppViewModel.kt</a></b></td>
									<td style='padding: 8px;'>- 集中管理整個 App 的狀態：自選清單、即時報價、K 線快取與提示訊息。<br>- 批次抓報價並限制同時請求數，支援用代號或名稱新增股票，依週期分段抓取歷史 K 線，並把例外轉成易懂的中文訊息。</td>
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
											<td style='padding: 8px;'>- 用 DataStore 保存自選清單、券商捷徑、均線週期、成交量顯示、漲跌配色與排序方式，並以 Flow 提供給 ViewModel。<br>- API 金鑰交給 KeyCipher 加密後才寫入。</td>
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
											<td style='padding: 8px;'>- 定義全 App 共用的資料型別：自選股、券商捷徑、即時報價、K 棒、K 線週期、排序方式與設定。<br>- 包含預設均線週期（5、10、20、60、200）與台股代號格式規則。</td>
										</tr>
										<tr style='border-bottom: 1px solid #eee;'>
											<td style='padding: 8px;'><b><a href='https://github.com/rowing195/trading-ewww/blob/main/app/src/main/java/tw/stockpeek/data/Indicators.kt'>Indicators.kt</a></b></td>
											<td style='padding: 8px;'>- 計算簡單移動平均，資料不足的位置填 NaN 讓圖表略過。<br>- 解析使用者輸入的均線設定，最多 5 條、每條 2–240 期。</td>
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
											<td style='padding: 8px;'>- 首頁自選股清單，顯示現價、漲跌或漲跌幅，以及上漲、平盤、下跌家數。<br>- 支援自訂、漲幅、跌幅排序，長按調整順序或刪除，並提供可一次貼上多檔代號或名稱的新增對話框。</td>
										</tr>
										<tr style='border-bottom: 1px solid #eee;'>
											<td style='padding: 8px;'><b><a href='https://github.com/rowing195/trading-ewww/blob/main/app/src/main/java/tw/stockpeek/ui/DetailScreen.kt'>DetailScreen.kt</a></b></td>
											<td style='padding: 8px;'>- 個股頁，上方顯示即時報價與開高低量，下方切換 5 分／日／週／月 K。<br>- 日 K 最後一根以即時報價補上，資訊列跟著十字線顯示該根 K 棒的價量與各均線數值。</td>
										</tr>
										<tr style='border-bottom: 1px solid #eee;'>
											<td style='padding: 8px;'><b><a href='https://github.com/rowing195/trading-ewww/blob/main/app/src/main/java/tw/stockpeek/ui/CandleChart.kt'>CandleChart.kt</a></b></td>
											<td style='padding: 8px;'>- 用 Compose Canvas 自繪的 K 線圖，包含 K 棒、成交量、均線、可視範圍最高最低價、最新價虛線與十字線。<br>- 價格範圍只依畫面內的 K 棒計算，均線超出範圍的部分會被裁掉。<br>- 支援單指平移、雙指縮放與點擊或長按叫出十字線；顏色、比例與指標都在這裡直接調整。</td>
										</tr>
										<tr style='border-bottom: 1px solid #eee;'>
											<td style='padding: 8px;'><b><a href='https://github.com/rowing195/trading-ewww/blob/main/app/src/main/java/tw/stockpeek/ui/SettingsScreen.kt'>SettingsScreen.kt</a></b></td>
											<td style='padding: 8px;'>- 設定頁，負責輸入並測試富果 API 金鑰、從手機已安裝的 App 挑選券商捷徑。<br>- 調整均線週期、成交量顯示，以及漲紅跌綠或漲綠跌紅配色。</td>
										</tr>
										<tr style='border-bottom: 1px solid #eee;'>
											<td style='padding: 8px;'><b><a href='https://github.com/rowing195/trading-ewww/blob/main/app/src/main/java/tw/stockpeek/ui/BrokerBar.kt'>BrokerBar.kt</a></b></td>
											<td style='padding: 8px;'>- 底部券商捷徑列，點一下直接開啟券商 App，在 K 線頁會順便把股票代號複製到剪貼簿。<br>- 列出手機上可啟動的 App 供挑選，名稱像券商的排在前面。</td>
										</tr>
										<tr style='border-bottom: 1px solid #eee;'>
											<td style='padding: 8px;'><b><a href='https://github.com/rowing195/trading-ewww/blob/main/app/src/main/java/tw/stockpeek/ui/Format.kt'>Format.kt</a></b></td>
											<td style='padding: 8px;'>- 統一價格、漲跌、百分比、張數與 K 棒時間的顯示格式，數字使用等寬字型以便對齊。<br>- 張數達 10 萬以上改用「萬」為單位。</td>
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
													<td style='padding: 8px;'>- 定義 App 的淺色與深色配色、漲跌顏色（可切換漲紅跌綠或漲綠跌紅），以及五條均線各自的顏色。</td>
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

**直接安裝 APK：** 到 [Releases](https://github.com/rowing195/trading-ewww/releases) 下載最新的 APK，用手機開啟安裝（第一次需允許「安裝不明來源的應用程式」）。

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
2. 回首頁按 **+** 新增股票，代號或名稱都可以，一次可以貼好幾檔：`2330 0050 00878 鴻海`。
3. 設定 →「券商 App 捷徑」，從手機已安裝的 App 挑你的券商（可多選）。
4. 點股票進入 K 線頁：
    - 單指左右拖曳平移、雙指縮放
    - 點一下或長按出現十字線，拖著看每根 K 棒；再點一下取消
    - 點底部券商捷徑會順便複製股票代號，切過去貼上搜尋即可

### 測試

目前沒有自動化測試。

---

## 資料來源與限制

- 行情來自富果行情 API，免費方案日內與歷史行情各每分鐘 60 次。
- 盤中（平日 08:30–13:35）且 App 位於前景時才自動更新；從券商 App 切回來會立刻更新一次。
- 自動更新間隔最短 20 秒，清單超過 15 檔後依長度拉長（30 檔約 40 秒）。
- 歷史 K 線盤後 16:30 才更新；盤中的日 K 最後一根用即時報價補上，週 K、月 K 的當週、當月要等盤後。
- K 線資料範圍：5 分 K 約近 5 個交易日、日 K 約 2 年、週 K 約 5 年、月 K 約 10 年。月 K 的資料筆數不足 200 根，所以畫不出 MA200。
- 國定假日沒有特別處理，報價會停在前一個交易日。
- 金鑰以 Android Keystore 加密存在本機；清單與設定存在 DataStore，都不會備份到雲端。

---

## 授權

目前沒有附上授權條款（LICENSE）。

---

## 致謝

- 行情資料由 [富果 Fugle 行情 API](https://developer.fugle.tw/) 提供。

<div align="left"><a href="#top">回到頂端</a></div>

---
