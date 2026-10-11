#!/usr/bin/env python3
"""v2.1 新增能力的端到端截图验证。

覆盖：
  29 知识库浏览页（管理员）
  30 知识库检索验证（命中篇目 / 得分 / 片段）
  31 对话页会话侧栏（历史持久化）
  32 知识问答回答 + 来源卡（RAG）

用法：
  /Users/a1/.workbuddy/binaries/python/envs/default/bin/python tools/screenshots_v21.py

为什么用 Playwright + Edge 而不是 agent-browser：
  本机已缓存 Playwright(Python) 且装有 Edge(Chromium 内核)，
  复用现成环境可以免去再下载约 500MB 的 Chromium。
"""
import sys
from pathlib import Path

from playwright.sync_api import sync_playwright

BASE = "http://localhost:8088"
OUT = Path(__file__).resolve().parent.parent / "docs" / "screenshots"

ADMIN = ("admin", "admin")
MEMBER = ("18470558967", "admin")


def shot(page, name, full=False):
    OUT.mkdir(parents=True, exist_ok=True)
    path = OUT / name
    page.screenshot(path=str(path), full_page=full)
    print(f"  ✅ {name}")
    return path


def login(page, account, password, is_admin):
    page.goto(f"{BASE}/login", wait_until="domcontentloaded")
    page.wait_for_timeout(700)
    # 身份切换：登录表单上方的一对切换按钮（管理员 / 会员）
    want = "管理员" if is_admin else "会员"
    btn = page.locator("button").filter(has_text=want).first
    if btn.count():
        btn.click()
        page.wait_for_timeout(250)
    inputs = page.locator(".login-form input, input")
    inputs.nth(0).fill(account)
    inputs.nth(1).fill(password)
    # 主按钮文案是「登 录」（中间有全角空格），用类选择器更稳
    page.locator("button.el-button--primary").first.click()
    page.wait_for_timeout(1600)


def main():
    errors = []
    with sync_playwright() as p:
        browser = p.chromium.launch(channel="msedge", headless=True)
        ctx = browser.new_context(viewport={"width": 1500, "height": 950},
                                  device_scale_factor=2, locale="zh-CN")
        page = ctx.new_page()
        page.on("console", lambda m: errors.append(f"console.{m.type}: {m.text}")
                if m.type == "error" else None)
        page.on("pageerror", lambda e: errors.append(f"pageerror: {e}"))

        # ---------- 管理员：知识库浏览页 ----------
        print("管理员 · 知识库浏览页")
        login(page, *ADMIN, is_admin=True)
        page.goto(f"{BASE}/knowledge", wait_until="domcontentloaded")
        page.wait_for_timeout(1800)
        shot(page, "29-knowledge-browse.png")

        # ---------- 知识库检索验证 ----------
        print("管理员 · 知识库检索验证")
        probe = page.locator(".card", has_text="检索验证").locator("input").first
        probe.fill("深蹲膝盖疼怎么办")
        page.keyboard.press("Enter")
        page.wait_for_timeout(1800)
        shot(page, "30-knowledge-search.png")

        # ---------- 会员：会话侧栏 + 知识问答 ----------
        print("会员 · 会话侧栏与知识问答")
        page.evaluate("localStorage.clear()")
        login(page, *MEMBER, is_admin=False)
        page.goto(f"{BASE}/me/agent", wait_until="domcontentloaded")
        page.wait_for_timeout(1800)

        ta = page.locator("textarea").first
        ta.click()
        ta.fill("深蹲膝盖疼怎么办")
        page.keyboard.press("Enter")
        page.wait_for_timeout(3000)
        shot(page, "31-agent-knowledge-answer.png")

        ta.click()
        ta.fill("蛋白质吃多少合适")
        page.keyboard.press("Enter")
        page.wait_for_timeout(3000)
        shot(page, "32-agent-session-sidebar.png")

        browser.close()

    if errors:
        print("\n⚠️  页面错误：")
        for e in errors:
            print("   -", e)
        sys.exit(1)
    print("\n✅ 4 张截图完成，零 console/pageerror")


if __name__ == "__main__":
    main()
