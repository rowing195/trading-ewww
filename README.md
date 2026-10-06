# 漲跌速覽 StockPeek

自己用的台股看盤 App：首頁一眼看自選股漲跌幅，點進去看 K 線，底部一鍵切到券商 App 下單。
不登入、不連券商、不記股數。

## 第一次使用

1. 用 Android Studio 開啟這個資料夾，等 Gradle 同步完成（第一次會下載依賴）。
2. 手機開啟「開發人員選項 → USB 偵錯」，接上電腦按 ▶ Run。
   想要圖表更順：Build → Generate App Bundles or APKs → Generate APKs，選 release（已設定用 debug 金鑰簽章，自用可直接安裝）。
3. 到 [富果開發者網站](https://developer.fugle.tw/docs/key/) 登入會員申請免費 API 金鑰，貼到 App 的「設定」。
4. 首頁按 + 新增股票，代號或名稱都可以，一次可以貼好幾檔：`2330 0050 00878 鴻海`。
5. 設定 → 券商 App 捷徑，從手機已安裝的 App 挑你的券商（可多選）。

## 功能

- 首頁：現價、漲跌／漲跌幅（點色塊切換）、上漲／平盤／下跌家數；排序可選自訂、漲幅、跌幅；長按調整順序或刪除
- 盤中（08:30–13:35）自動更新，App 在前景才會跑；從券商 App 切回來會立刻更新
- K 線：5 分／日／週／月，可設 4 條均線、成交量開關、漲紅跌綠或漲綠跌紅
  - 單指左右拖曳平移、雙指縮放
  - 點一下或長按出現十字線，拖著看每根 K 棒；再點一下取消
- K 線頁點券商捷徑會順便複製股票代號，切過去貼上搜尋即可

## 資料與限制

- 行情來自富果行情 API，免費方案日內與歷史行情各每分鐘 60 次。自動更新間隔會依清單長度自己拉長，30 檔以內約 30–40 秒一次。
- 歷史 K 線盤後 16:30 才更新；盤中的日 K 最後一根用即時報價補上，週／月 K 的當週、當月要等盤後。
- 國定假日沒有特別處理，報價會停在前一個交易日。
- 金鑰用 Android Keystore 加密存在本機；清單與設定存在 DataStore，都不會備份到雲端。

## 程式結構

```
app/src/main/java/tw/stockpeek/
├── MainActivity.kt        畫面切換、前景時自動更新
├── AppViewModel.kt        報價、清單、K 線快取
├── data/
│   ├── FugleClient.kt     富果 REST API
│   ├── SettingsStore.kt   DataStore 設定
│   ├── KeyCipher.kt       Keystore 加密 API 金鑰
│   ├── MarketClock.kt     交易時段
│   └── Indicators.kt      均線
└── ui/
    ├── HomeScreen.kt      首頁清單、新增股票
    ├── DetailScreen.kt    報價與 K 線頁
    ├── CandleChart.kt     自繪 K 線圖（Canvas）
    ├── SettingsScreen.kt  設定、券商 App 挑選
    └── BrokerBar.kt       底部券商捷徑
```

K 線圖是用 Compose Canvas 自己畫的，顏色、比例、要畫什麼指標都在 `CandleChart.kt` 裡直接改。
