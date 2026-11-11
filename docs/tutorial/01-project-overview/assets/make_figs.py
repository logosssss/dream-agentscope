# -*- coding: utf-8 -*-
"""生成第 1 篇正文配图。

三张图输出到同目录：
  fig-01-layers.png       模块分层与依赖方向
  fig-02-request-path.png 一次流式请求的检查顺序
  fig-03-topology.png     部署拓扑（两个进程 + 外部依赖）

白底浅色，和文章正文同一视觉；公众号版不用这三张（那边是 CSS 色块）。
"""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
FONT_BOLD = "C:/Windows/Fonts/msyhbd.ttc"
FONT_REG = "C:/Windows/Fonts/msyh.ttc"

BG = (255, 255, 255)
BOX = (247, 249, 252)
BOX_LINE = (214, 222, 232)
TITLE = (31, 41, 55)
SUB = (107, 114, 128)
ACCENT = (24, 95, 165)
ACCENT_BG = (232, 241, 250)
WARN = (185, 28, 28)
WARN_BG = (253, 236, 236)
LINE = (148, 163, 184)


def fb(size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(FONT_BOLD, size)


def fr(size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(FONT_REG, size)


def head(d: ImageDraw.ImageDraw, title: str, sub: str) -> None:
    d.text((72, 56), title, font=fb(32), fill=TITLE, anchor="la")
    d.text((72, 104), sub, font=fr(18), fill=SUB, anchor="la")


def box(d, xy, title, sub=None, fill=BOX, line=BOX_LINE, tag=None):
    x0, y0, x1, y1 = xy
    d.rounded_rectangle(xy, radius=12, fill=fill, outline=line, width=2)
    cx = (x0 + x1) / 2
    if sub:
        d.text((cx, (y0 + y1) / 2 - 14), title, font=fb(21), fill=TITLE, anchor="mm")
        d.text((cx, (y0 + y1) / 2 + 18), sub, font=fr(15), fill=SUB, anchor="mm")
    else:
        d.text((cx, (y0 + y1) / 2), title, font=fb(21), fill=TITLE, anchor="mm")
    if tag:
        d.rounded_rectangle([x1 - 78, y0 + 8, x1 - 12, y0 + 34], radius=13,
                            fill=ACCENT_BG, outline=ACCENT, width=1)
        d.text((x1 - 45, y0 + 21), tag, font=fr(14), fill=ACCENT, anchor="mm")


def arrow(d, p0, p1, color=LINE, width=2, dash=False):
    import math
    x0, y0 = p0
    x1, y1 = p1
    if dash:
        seg = math.hypot(x1 - x0, y1 - y0)
        n = max(1, int(seg // 14))
        for i in range(n):
            t0 = i / n
            t1 = t0 + 0.5 / n
            d.line([(x0 + (x1 - x0) * t0, y0 + (y1 - y0) * t0),
                    (x0 + (x1 - x0) * t1, y0 + (y1 - y0) * t1)], fill=color, width=width)
    else:
        d.line([p0, p1], fill=color, width=width)
    ang = math.atan2(y1 - y0, x1 - x0)
    L, W = 12, 6
    d.polygon([(x1, y1),
               (x1 - L * math.cos(ang) + W * math.sin(ang), y1 - L * math.sin(ang) - W * math.cos(ang)),
               (x1 - L * math.cos(ang) - W * math.sin(ang), y1 - L * math.sin(ang) + W * math.cos(ang))],
              fill=color)


def topology() -> Image.Image:
    img = Image.new("RGB", (1600, 760), BG)
    d = ImageDraw.Draw(img)
    head(d, "Dream-SaaS 部署拓扑",
         "两个进程、两个前端；MySQL、Redis、Milvus 启动即探测，Nacos 与模型服务按需连接")

    for x, label in ((80, "客户端"), (380, "前端"), (780, "后端进程"), (1200, "外部依赖")):
        d.text((x, 176), label, font=fb(16), fill=ACCENT, anchor="la")

    box(d, (80, 356, 300, 470), "浏览器", "用户与管理端共用")
    box(d, (380, 236, 680, 350), "web", "5173 · 对话 / 知识库", tag="5173")
    box(d, (380, 476, 680, 590), "web-admin", "运维界面", tag="5174")
    box(d, (780, 216, 1100, 350), "agent-bootstrap", "业务 API · 执行 Flyway", tag="8080")
    box(d, (780, 476, 1100, 610), "agent-admin", "运维 API · 不跑对话", tag="8100")

    d.rounded_rectangle([1200, 206, 1520, 620], radius=12, fill=(250, 251, 253),
                        outline=BOX_LINE, width=2)
    rows = [("MySQL", "会话、运行、审计"),
            ("Redis", "登录会话、额度计数"),
            ("Milvus", "向量集合与检索"),
            ("Nacos", "配置，有地址才连"),
            ("模型服务", "请求期解析")]
    for i, (name, note) in enumerate(rows):
        y = 232 + i * 74
        d.text((1232, y), name, font=fb(18), fill=TITLE, anchor="la")
        d.text((1232, y + 26), note, font=fr(14), fill=SUB, anchor="la")

    arrow(d, (300, 400), (376, 300))
    arrow(d, (300, 428), (376, 528))
    arrow(d, (680, 292), (776, 284))
    arrow(d, (680, 528), (776, 540))
    arrow(d, (1100, 266), (1196, 300))
    arrow(d, (1100, 552), (1196, 500))

    d.text((80, 664), "Nginx（可选）：代填 X-Caller-Token 后转发到 8080；两个进程互不调用，各自连中间件",
           font=fr(16), fill=SUB, anchor="la")
    return img


def request_path() -> Image.Image:
    rows = [
        ("POST /api/chat/stream", "请求进入用户进程；只走业务路径，被 CallerTokenFilter 最先拦下", None),
        ("CallerTokenFilter", "校验 X-Caller-Token；/health/* 与 /wx/confirm 豁免；不通过直接 401", "401"),
        ("AccessIdentityFilter", "解析 Authorization: Bearer，把租户与用户写入线程身份", "401"),
        ("租户与归属校验", "requireUser → 租户可用 → 租户内用户可用 → 会话归属独占", "200 + code"),
        ("QuotaGuard.check", "requestId 去重 → 日请求额度（租户可覆盖）→ 分钟窗口限流", "200 + code"),
        ("SessionTurnGuard + ConcurrentRunGuard", "按 turn:* 占会话锁，再占租户并发名额；失败回滚已占的锁", "200 + code"),
        ("AgentExecutor.stream", "runtime 唯一入口；HarnessAgentFactory 按请求构建，结束即关闭", None),
        ("doFinally：先落库再放锁", "RunLog 与 RunStep 写完后释放并发名额与会话锁，避免下一轮 seq 交错", None),
    ]
    h = 150 + len(rows) * 96 + 40
    img = Image.new("RGB", (1440, h), BG)
    d = ImageDraw.Draw(img)
    head(d, "一次流式对话的检查顺序",
         "任何一步失败都不会进入模型调用；401 走状态码，其余业务失败是 HTTP 200 + code")

    x_text = 150
    for i, (title, detail, tag) in enumerate(rows):
        y = 170 + i * 96
        d.rounded_rectangle([72, y, 108, y + 36], radius=10, fill=ACCENT_BG, outline=ACCENT, width=1)
        d.text((90, y + 18), str(i + 1), font=fb(17), fill=ACCENT, anchor="mm")
        d.text((x_text, y + 2), title, font=fb(20), fill=TITLE, anchor="la")
        d.text((x_text, y + 34), detail, font=fr(16), fill=SUB, anchor="la")
        if tag:
            status = tag.startswith("200")
            fill = (241, 245, 249) if status else WARN_BG
            edge = (148, 163, 184) if status else WARN
            ink = (71, 85, 105) if status else WARN
            w = 132 if status else 74
            d.rounded_rectangle([1252, y + 4, 1252 + w, y + 34], radius=15,
                               fill=fill, outline=edge, width=1)
            d.text((1252 + w / 2, y + 19), tag, font=fb(14), fill=ink, anchor="mm")
        if i < len(rows) - 1:
            d.line([(90, y + 36), (90, y + 96)], fill=(203, 213, 225), width=2)
    return img


def layers() -> Image.Image:
    """五层依赖栈。层内模块互不依赖，依赖只允许向下。"""
    tiers = [
        ("进程入口", ["agent-bootstrap", "agent-admin"], "可独立启动，互不调用"),
        ("接入层", ["agent-access"], "HTTP 入口、业务编排、额度与占位"),
        ("执行层", ["agent-runtime"], "每次请求构建 HarnessAgent，结束即关闭"),
        ("领域服务", ["agent-semantic", "agent-state", "agent-vector",
                   "agent-audit", "agent-object-store"], "提示词与工具目录、会话状态、向量、审计、对象存储"),
        ("基础", ["agent-common"], "异常、通用结构、请求级上下文；不依赖任何业务模块"),
    ]
    img = Image.new("RGB", (1560, 720), BG)
    d = ImageDraw.Draw(img)
    head(d, "十个模块的五层依赖栈",
         "依赖只允许向下；层内互不依赖，反向依赖由 ArchUnit 在 bootstrap 的测试里拦住")

    for i, (name, chips, note) in enumerate(tiers):
        y = 156 + i * 104
        fill = (240, 246, 252) if i in (0, 4) else (247, 249, 252)
        d.rounded_rectangle([150, y, 1480, y + 84], radius=10, fill=fill,
                            outline=BOX_LINE, width=2)
        d.text((178, y + 26), name, font=fb(19), fill=TITLE, anchor="la")
        d.text((178, y + 54), note, font=fr(14), fill=SUB, anchor="la")

        x = 560
        for chip in chips:
            w = d.textlength(chip, font=fr(16)) + 34
            if x + w > 1460:
                x = 560
            d.rounded_rectangle([x, y + 24, x + w, y + 60], radius=8,
                                fill=(255, 255, 255), outline=(203, 213, 225), width=1)
            d.text((x + w / 2, y + 42), chip, font=fr(16), fill=(30, 41, 59), anchor="mm")
            x += w + 12

    arrow(d, (110, 168), (110, 648), width=3)
    d.text((150, 676), "横切模块不反向依赖业务层：agent-common 不依赖任何模块，"
                       "agent-state / agent-vector / agent-object-store / agent-audit 互不依赖。",
           font=fr(15), fill=SUB, anchor="la")
    return img


def main() -> None:
    topology().save(HERE / "fig-03-topology.png", optimize=True)
    request_path().save(HERE / "fig-02-request-path.png", optimize=True)
    layers().save(HERE / "fig-01-layers.png", optimize=True)
    print("done")


if __name__ == "__main__":
    main()
