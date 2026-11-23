# -*- coding: utf-8 -*-
"""生成《AgentScope 落地实战》第 2 篇的 CSDN 封面（1280x720）。

底图是本篇专用的 AI 蓝图稿（六块等轴玻璃面板 + 网格刻度），与第 1 篇的星云底图区分开。
裁切窗口上移，避开模型平台在右下角加的水印。
"""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
BG = HERE / "bg-csdn-raw.png"

FONT_BOLD = "C:/Windows/Fonts/msyhbd.ttc"
FONT_REG = "C:/Windows/Fonts/msyh.ttc"

SERIES = "AgentScope 落地实战 02"
TITLE = "概念地图"
SUB = "五个对象 · 30 个装配点 · 一次懒启动"
TECH = "GatewayBootstrap · Builder · Channel"
META = "Java 21 · AgentScope 2.0.4 · 97 行测试"
SITE = "dream-saas.com"

WHITE = (246, 250, 255)
CYAN = (56, 189, 248)


def font(path: str, size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(path, size)


def scrim(img: Image.Image, left_alpha: int, bottom_alpha: int,
          span: float = 0.66, start: float = 0.72) -> Image.Image:
    """左侧压暗保证白字对比度，底部轻压一层。"""
    w, h = img.size
    layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = layer.load()
    for x in range(w):
        a = int(left_alpha * max(0.0, 1 - x / (w * span)) ** 1.3)
        for y in range(h):
            px[x, y] = (4, 9, 18, a)
    base = Image.alpha_composite(img.convert("RGBA"), layer)

    grad = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    gpx = grad.load()
    for y in range(h):
        a = int(bottom_alpha * max(0.0, (y - h * start) / (h * (1 - start))))
        for x in range(w):
            gpx[x, y] = (3, 7, 15, a)
    return Image.alpha_composite(base, grad)


def main() -> None:
    bg = Image.open(BG).convert("RGB")
    # 1536x1024 → 16:9 需要 864 高；取 y=24..888，避开右下角 y>930 的水印
    crop = bg.crop((0, 24, 1536, 888)).resize((1280, 720), Image.LANCZOS)
    img = scrim(crop, 242, 90)
    d = ImageDraw.Draw(img)

    x = 64

    chip_f = font(FONT_REG, 20)
    tw = d.textlength(SERIES, font=chip_f)
    d.rounded_rectangle([x, 52, x + tw + 32, 96], radius=22,
                        fill=(16, 38, 64, 225), outline=(56, 132, 190, 170))
    d.text((x + 16, 74), SERIES, font=chip_f, fill=(172, 209, 240), anchor="lm")

    d.rectangle([x, 124, x + 52, 129], fill=CYAN)

    d.text((x, 152), TITLE, font=font(FONT_BOLD, 62), fill=WHITE, anchor="la",
           stroke_width=2, stroke_fill=(4, 9, 18))
    d.text((x, 252), SUB, font=font(FONT_BOLD, 31), fill=(226, 238, 250), anchor="la",
           stroke_width=2, stroke_fill=(4, 9, 18))

    d.text((x, 330), TECH, font=font(FONT_REG, 19), fill=(148, 176, 206), anchor="la")
    d.text((x, 366), META, font=font(FONT_REG, 17), fill=(120, 148, 180), anchor="la")

    d.rectangle([x, 618, x + 240, 619], fill=(52, 90, 128))
    d.text((x, 636), SITE, font=font(FONT_REG, 17), fill=(126, 156, 190), anchor="la")

    out = HERE / "cover-csdn-1280x720.png"
    img.convert("RGB").save(out, quality=95)
    print("written", out, img.size)


if __name__ == "__main__":
    main()
