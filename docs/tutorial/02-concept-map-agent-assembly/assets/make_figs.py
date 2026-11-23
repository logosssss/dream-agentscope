# -*- coding: utf-8 -*-
"""生成第 2 篇正文配图。

三张图输出到同目录：
  fig-01-assembly-order.png 一次装配的六个步骤
  fig-02-runtime-context.png RuntimeContext 的三次写入
  fig-03-lifecycle.png       长驻实例的生命周期

白底浅色，与正文同一视觉；公众号版不用这三张（那边是 CSS 色块）。
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
LINE = (148, 163, 184)


def fb(size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(FONT_BOLD, size)


def fr(size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(FONT_REG, size)


def head(d: ImageDraw.ImageDraw, title: str, sub: str) -> None:
    d.text((72, 54), title, font=fb(32), fill=TITLE, anchor="la")
    d.text((72, 102), sub, font=fr(18), fill=SUB, anchor="la")


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


def pill(d, right: int, center_y: int, text: str, width: int = 96) -> None:
    x1 = right
    x0 = right - width
    d.rounded_rectangle([x0, center_y - 15, x1, center_y + 15], radius=15,
                        fill=ACCENT_BG, outline=ACCENT, width=1)
    d.text(((x0 + x1) / 2, center_y), text, font=fr(14), fill=ACCENT, anchor="mm")


def assembly_order() -> Image.Image:
    rows = [
        ("1", "GatewayBootstrap.builder()",
         "只收集装配函数，此刻没有任何 Agent 对象", "配置期"),
        ("2", "configureAllAgents(fn)",
         "公共项对每个 Agent 各跑一次，测试断言为 2", "配置期"),
        ("3", "agent(agentId, fn)",
         "consumer 返回后立即 build，Builder 不再复用", "装配期"),
        ("4", "start()",
         "建 Channel、注入 Gateway，返回 agents() 与 channelManager()", "装配期"),
        ("5", "attachCompanionTools(getToolkit())",
         "Toolkit 在 build 之后才存在，业务工具只能此时挂", "装配期"),
        ("6", "channel().sendStream(request)",
         "首次调用才懒启动；缺模型密钥也能先起进程", "运行期"),
    ]
    h = 172 + len(rows) * 100 + 56
    img = Image.new("RGB", (1440, h), BG)
    d = ImageDraw.Draw(img)
    head(d, "一次 Agent 装配的六个步骤",
         "公共项先跑、差异项后跑，build 发生在 consumer 返回之后")

    for i, (no, title, detail, phase) in enumerate(rows):
        y = 168 + i * 100
        d.rounded_rectangle([88, y, 1168, y + 82], radius=10, fill=BOX,
                            outline=BOX_LINE, width=2)
        d.ellipse([108, y + 24, 142, y + 58], fill=ACCENT_BG, outline=ACCENT, width=1)
        d.text((125, y + 41), no, font=fb(17), fill=ACCENT, anchor="mm")
        d.text((164, y + 14), title, font=fb(20), fill=TITLE, anchor="la")
        d.text((164, y + 46), detail, font=fr(15), fill=SUB, anchor="la")
        pill(d, 1376, y + 41, phase)

    d.text((88, h - 40),
           "顺序不是约定，是框架实现：configureAllAgents 的回调排在每个 agent() 的 consumer 之前。",
           font=fr(15), fill=SUB, anchor="la")
    return img


def runtime_context() -> Image.Image:
    layers = [
        ("1", "调用方暂存", "SendOptions.withRuntimeContext(staging(tenantId, agentId))",
         "只放业务键；userId 与 sessionId 走 SendOptions"),
        ("2", "resolver 补键", "AgentChannelRuntimeContextResolver.resolve(...)",
         "以 caller 为基座；agentId 可从 MsgContext.extra 兜底"),
        ("3", "Gateway 覆盖身份", "框架在进入 Agent 前写入 userId 与 sessionId",
         "身份以登录态为准，调用方无法自称"),
    ]
    img = Image.new("RGB", (1440, 600), BG)
    d = ImageDraw.Draw(img)
    head(d, "RuntimeContext 的三次写入",
         "同一份上下文被依次合并；后写的覆盖先写的")

    for i, (no, title, detail, note) in enumerate(layers):
        y = 152 + i * 118
        d.rounded_rectangle([88, y, 1352, y + 96], radius=10, fill=BOX,
                            outline=BOX_LINE, width=2)
        d.ellipse([110, y + 30, 146, y + 66], fill=ACCENT_BG, outline=ACCENT, width=1)
        d.text((128, y + 48), no, font=fb(17), fill=ACCENT, anchor="mm")
        d.text((168, y + 18), title, font=fb(20), fill=TITLE, anchor="la")
        d.text((168, y + 50), detail, font=fr(15), fill=SUB, anchor="la")
        d.text((168, y + 72), note, font=fr(14), fill=(140, 148, 160), anchor="la")
        if i < len(layers) - 1:
            arrow(d, (720, y + 96), (720, y + 116))

    d.rounded_rectangle([88, 508, 1352, 556], radius=8, fill=ACCENT_BG,
                        outline=ACCENT, width=1)
    d.text((108, 532),
           "结果：{ userId, sessionId, tenantId, agentId, RiskLevel }",
           font=fb(17), fill=(12, 68, 124), anchor="lm")
    d.text((940, 532), "业务键不要进 MsgContext.extra：会进会话闸键",
           font=fr(14), fill=ACCENT, anchor="lm")
    return img


def lifecycle() -> Image.Image:
    boxes = [
        ("未启动", "bootstrap == null", 80, 340),
        ("运行中", "companion + echo 长驻", 400, 660),
        ("排空中", "draining = true", 720, 980),
        ("运行中", "旧实例 close 后重建", 1040, 1360),
    ]
    img = Image.new("RGB", (1440, 540), BG)
    d = ImageDraw.Draw(img)
    head(d, "长驻实例的生命周期",
         "懒启动一次，配置热更时排空重建，进程退出走同一个闸")

    for title, sub, x0, x1 in boxes:
        d.rounded_rectangle([x0, 200, x1, 336], radius=12, fill=BOX,
                            outline=BOX_LINE, width=2)
        d.text(((x0 + x1) / 2, 246), title, font=fb(23), fill=TITLE, anchor="mm")
        d.text(((x0 + x1) / 2, 288), sub, font=fr(15), fill=SUB, anchor="mm")

    labels = [("首次对话", "channel()"), ("配置热更", "rebuildAll()"), ("在途归零", "或超时")]
    for i, (line1, line2) in enumerate(labels):
        x = 340 + i * 320
        arrow(d, (x, 268), (x + 56, 268))
        d.text((x + 28, 356), line1, font=fb(14), fill=ACCENT, anchor="ma")
        d.text((x + 28, 378), line2, font=fr(14), fill=SUB, anchor="ma")

    d.text((80, 432),
           "rebuildAll() 与 @PreDestroy 共用 ResidentAgentDrain：draining 期间新 turn 阻塞等待，"
           "上限取 agentscope.runtime.timeout-seconds（默认 120 秒）。",
           font=fr(15), fill=SUB, anchor="la")
    d.text((80, 462),
           "排空超时不抛错，记一条 warning 后继续重建；在途对话可能被新实例的 close 打断。",
           font=fr(15), fill=SUB, anchor="la")
    return img


def main() -> None:
    assembly_order().save(HERE / "fig-01-assembly-order.png", optimize=True)
    runtime_context().save(HERE / "fig-02-runtime-context.png", optimize=True)
    lifecycle().save(HERE / "fig-03-lifecycle.png", optimize=True)
    print("done")


if __name__ == "__main__":
    main()
