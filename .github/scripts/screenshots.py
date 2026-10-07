#!/usr/bin/env python3
"""在模擬器上安裝 App、依序點過各畫面並截圖。

用法：screenshots.py <apk> <輸出資料夾>
環境變數 FUGLE_API_KEY 有值時，會輸入金鑰、加入自選股，拍有報價和 K 線的畫面；
沒有值就只拍未設定金鑰時的首頁與設定頁。
"""
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PKG = "tw.stockpeek"
SYMBOLS = "2330 0050 2317 2454 00878 2603"
DUMP = "/data/local/tmp/ui.xml"

apk, out = sys.argv[1], sys.argv[2]
os.makedirs(out, exist_ok=True)


def adb(*args, check=True):
    return subprocess.run(["adb", *args], check=check, capture_output=True, text=True).stdout


def shell(cmd, check=True):
    return adb("shell", cmd, check=check)


def nodes():
    shell(f"rm -f {DUMP}", check=False)
    # 畫面還在動（例如載入中的轉圈）時 dump 會失敗，交給呼叫端重試
    shell(f"uiautomator dump {DUMP}", check=False)
    xml = adb("exec-out", f"cat {DUMP}", check=False)
    if not xml.startswith("<?xml"):
        return []
    return list(ET.fromstring(xml).iter("node"))


def find(text=None, desc=None, cls=None, timeout=30):
    """等到畫面上出現符合條件的元件，回傳中心座標。text 是正規表示式，要整串相符。"""
    deadline = time.time() + timeout
    while time.time() < deadline:
        for n in nodes():
            if text is not None and not re.fullmatch(text, n.get("text", "")):
                continue
            if desc is not None and n.get("content-desc") != desc:
                continue
            if cls is not None and n.get("class") != cls:
                continue
            x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
            return n, ((x1 + x2) // 2, (y1 + y2) // 2)
        time.sleep(1)
    raise SystemExit(f"等不到元件：text={text} desc={desc} cls={cls}")


def tap(**kw):
    _, (x, y) = find(**kw)
    shell(f"input tap {x} {y}")


def type_text(value):
    # 裝置端 sh 用單引號包起來；input text 用 %s 代表空白
    escaped = value.replace("'", "'\\''").replace(" ", "%s")
    shell(f"input text '{escaped}'")


def shot(name, wait=2.0):
    time.sleep(wait)
    png = subprocess.run(["adb", "exec-out", "screencap", "-p"], check=True, capture_output=True).stdout
    with open(os.path.join(out, f"{name}.png"), "wb") as f:
        f.write(png)
    print(f"截圖 {name}.png", flush=True)


# ---------- 準備 ----------

adb("install", "-r", apk)
# 狀態列用展示模式：藏起通知圖示，畫面比較乾淨
shell("settings put global sysui_demo_allowed 1", check=False)
shell("am broadcast -a com.android.systemui.demo -e command enter", check=False)
shell("am broadcast -a com.android.systemui.demo -e command notifications -e visible false", check=False)
shell("cmd uimode night no", check=False)
shell(f"am start -W -n {PKG}/.MainActivity")

key = os.environ.get("FUGLE_API_KEY", "").strip()

# ---------- 未設定金鑰 ----------

find(text="先設定行情金鑰")
shot("01-首頁-未設定金鑰")

tap(text="前往設定")
find(text="行情資料（富果 API）")

if not key:
    shot("02-設定")
    print("沒有設定 FUGLE_API_KEY，只拍未設定金鑰的畫面。")
    sys.exit(0)

# ---------- 輸入金鑰 ----------

tap(cls="android.widget.EditText")  # 設定頁第一個輸入框就是金鑰欄
type_text(key)
tap(text="儲存並測試")
node, _ = find(text=r"金鑰可用 ✓|.*(無效|失敗|錯誤|上限|HTTP).*", timeout=40)
if not node.get("text").endswith("✓"):
    raise SystemExit("金鑰測試失敗：" + node.get("text"))
shot("02-設定")

# ---------- 加自選股 ----------

tap(desc="返回")
find(text="還沒有股票")
tap(desc="新增股票")
tap(cls="android.widget.EditText")
type_text(SYMBOLS)
tap(text="新增")
find(text=r"共 \d+ 檔", timeout=60)
shot("03-首頁-自選股", wait=6)  # 等「已新增」提示消失

# ---------- K 線 ----------

tap(text="2330.*")
find(text="日K")
shot("04-K線-日K", wait=8)
tap(text="週K")
shot("05-K線-週K", wait=10)
tap(text="5分")
shot("06-K線-5分K", wait=8)

# ---------- 深色模式 ----------

tap(text="日K")
shell("cmd uimode night yes")
shot("07-K線-深色", wait=8)
tap(desc="返回")
find(text=r"共 \d+ 檔")
shot("08-首頁-深色")
