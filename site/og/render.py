"""Рендер OG-картинки сайта: python site/og/render.py (из корня репозитория)."""

import json
from pathlib import Path

from playwright.sync_api import sync_playwright

HERE = Path(__file__).resolve().parent
lof = HERE / "lof.js"
lof.write_text("window.PC_DATA={lof:" + json.dumps(json.loads((HERE.parent / "data" / "lof.json").read_text())) + "};")
try:
    with sync_playwright() as p:
        browser = p.chromium.launch(channel="chrome")
        page = browser.new_page(viewport={"width": 1200, "height": 630})
        page.goto((HERE / "og.html").as_uri())
        page.evaluate("document.fonts.ready")
        page.screenshot(path=str(HERE.parent / "src" / "assets" / "og.png"))
        browser.close()
finally:
    lof.unlink()
