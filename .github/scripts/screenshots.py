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
import urllib.error
import urllib.request
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
    try:
        return list(ET.fromstring(xml).iter("node"))
    except ET.ParseError:
        return []  # 檔案還沒寫完或 dump 失敗，交給呼叫端重試


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


def type_text(value, chunk=10):
    # 一次送太長，模擬器偶爾會掉字，所以分段送。
    # 裝置端 sh 用單引號包起來；input text 用 %s 代表空白
    for i in range(0, len(value), chunk):
        escaped = value[i:i + chunk].replace("'", "'\\''").replace(" ", "%s")
        shell(f"input text '{escaped}'")


def check_key(key):
    """從 runner 直接打一次富果 API，分辨是金鑰本身的問題還是輸入過程出錯。不印出金鑰。"""
    req = urllib.request.Request(
        "https://api.fugle.tw/marketdata/v1.0/stock/intraday/quote/2330",
        headers={"X-API-KEY": key, "Accept": "application/json"},
    )
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            print(f"金鑰直接測試：HTTP {r.status}（長度 {len(key)}）", flush=True)
            return True
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", "replace")[:200]
        print(f"金鑰直接測試：HTTP {e.code} {body}（長度 {len(key)}）", flush=True)
    except OSError as e:
        print(f"金鑰直接測試：連線失敗 {e}", flush=True)
    return False


def enter_key(key, attempts=3):
    """輸入金鑰後切成「顯示」核對一次，不一致就清掉重打。"""
    for _ in range(attempts):
        tap(cls="android.widget.EditText")  # 設定頁第一個輸入框就是金鑰欄
        shell("input keyevent KEYCODE_MOVE_END")
        shell("input keyevent " + " ".join(["KEYCODE_DEL"] * 200))
        type_text(key)
        tap(desc="顯示金鑰")
        node, _ = find(cls="android.widget.EditText")
        typed = node.get("text", "")
        tap(desc="隱藏金鑰")
        if typed == key:
            print("輸入框內容與金鑰一致", flush=True)
            return
        print(f"輸入框內容與金鑰不一致（輸入 {len(typed)} 字，應為 {len(key)} 字），重打", flush=True)
    raise SystemExit("金鑰一直輸入不正確")


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
if key and not check_key(key):
    raise SystemExit("富果 API 不接受這把金鑰：請確認 FUGLE_API_KEY 是完整、有效的金鑰")

# ---------- 未設定金鑰 ----------

find(text="先設定行情金鑰")
shot("01-首頁-未設定金鑰")

tap(text="前往設定")
find(text="行情資料")

if not key:
    shot("02-設定")
    print("沒有設定 FUGLE_API_KEY，只拍未設定金鑰的畫面。")
    sys.exit(0)

# ---------- 輸入金鑰 ----------

enter_key(key)
tap(text="儲存並測試")
node, _ = find(text=r"金鑰可用.*|.*(無效|無法使用|失敗|錯誤|上限|HTTP).*", timeout=40)
if not node.get("text").startswith("金鑰可用"):
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
