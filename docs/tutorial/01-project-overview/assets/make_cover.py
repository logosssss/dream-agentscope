# -*- coding: utf-8 -*-
"""生成《AgentScope 落地实战》第 1 篇公众号封面。

输入：同目录下的 AI 底图（含平台水印，裁切时避开右下角）。
输出：cover-900x383.png（2.35:1 主封面）、cover-500x500.png（1:1 分享图）。

文字用本地字体叠加，避免生成模型把中文画坏。
"""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
BG = HERE / "bg-raw.png"

FONT_BOLD = "C:/Windows/Fonts/msyhbd.ttc"
FONT_REG = "C:/Windows/Fonts/msyh.ttc"

SERIES = "AgentScope 落地实战 01"
TITLE_CN = "Dream-SaaS 项目概览"
TITLE_SUB = "多 Agent 平台底座"
TECH = "Java 21 · Spring Boot 3 · AgentScope 2.0.4"
META = "两个进程 · 两个前端 · 十个 Maven 模块"
SITE = "dream-saas.com"

CYAN = (56, 189, 248)
WHITE = (245, 249, 255)
DIM = (150, 175, 205)
FAINT = (110, 138, 172)


def font(path: str, size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(path, size)


def scrim(img: Image.Image, left_alpha: int, bottom_alpha: int,
          span: float = 0.70, start: float = 0.55) -> Image.Image:
    """左侧与底部压暗，保证文字对比度。span 控制左侧压暗的横向衰减范围。"""
    w, h = img.size
    layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = layer.load()
    for x in range(w):
        a = int(left_alpha * max(0.0, 1 - x / (w * span)))
        for y in range(h):
            px[x, y] = (5, 10, 20, a)
    base = Image.alpha_composite(img.convert("RGBA"), layer)

    grad = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    gpx = grad.load()
    for y in range(h):
        a = int(bottom_alpha * max(0.0, (y - h * start) / (h * (1 - start))))
        for x in range(w):
            gpx[x, y] = (3, 7, 15, a)
    return Image.alpha_composite(base, grad)


def wide(bg: Image.Image) -> Image.Image:
    # 1536x1024 → 裁到 2.35:1，纵向取 y=170..824，避开右下角水印
    crop = bg.crop((0, 170, 1536, 824)).resize((900, 383), Image.LANCZOS)
    img = scrim(crop, 228, 110)
    d = ImageDraw.Draw(img)

    x = 56
    chip_f = font(FONT_REG, 18)
    tw = d.textlength(SERIES, font=chip_f)
    d.rounded_rectangle([x, 46, x + tw + 28, 84], radius=19,
                        fill=(30, 58, 92, 220), outline=(56, 120, 180, 160))
    d.text((x + 14, 65), SERIES, font=chip_f, fill=(168, 205, 238), anchor="lm")

    d.rectangle([x, 108, x + 46, 112], fill=CYAN)

    d.text((x, 134), TITLE_CN, font=font(FONT_BOLD, 42), fill=WHITE, anchor="la",
           stroke_width=2, stroke_fill=(5, 11, 22))
    d.text((x, 190), TITLE_SUB, font=font(FONT_BOLD, 42), fill=WHITE, anchor="la",
           stroke_width=2, stroke_fill=(5, 11, 22))

    d.text((x, 262), TECH, font=font(FONT_REG, 18), fill=DIM, anchor="la")
    d.text((x, 298), META, font=font(FONT_REG, 16), fill=FAINT, anchor="la")
    d.text((x, 334), SITE, font=font(FONT_REG, 15), fill=(110, 140, 175), anchor="la")
    return img.convert("RGB")


def square(bg: Image.Image) -> Image.Image:
    crop = bg.crop((240, 20, 1140, 920)).resize((500, 500), Image.LANCZOS)
    img = scrim(crop, 90, 238, span=0.82, start=0.26)
    d = ImageDraw.Draw(img)

    x = 40
    d.text((x, 286), SERIES, font=font(FONT_REG, 14), fill=(150, 190, 226), anchor="la")
    d.rectangle([x, 312, x + 34, 315], fill=CYAN)
    d.text((x, 330), "Dream-SaaS", font=font(FONT_BOLD, 34), fill=WHITE, anchor="la",
           stroke_width=2, stroke_fill=(5, 11, 22))
    d.text((x, 374), TITLE_SUB, font=font(FONT_BOLD, 30), fill=WHITE, anchor="la",
           stroke_width=2, stroke_fill=(5, 11, 22))
    d.text((x, 424), "Java 21 · AgentScope 2.0.4", font=font(FONT_REG, 14), fill=DIM, anchor="la")
    return img.convert("RGB")


def csdn(bg: Image.Image) -> Image.Image:
    """CSDN 文章封面：16:9，1280x720。

    CSDN 前端按 1280x720 做硬校验，比例不符会被拦；缩略图按 240x135 展示，
    因此正文信息必须撑满左侧，小图状态下仍可辨认。
    """
    # 1536x1024 → 16:9，取 y=60..924，避开右下角水印
    crop = bg.crop((0, 60, 1536, 924)).resize((1280, 720), Image.LANCZOS)
    img = scrim(crop, 236, 96, span=0.74, start=0.58)
    d = ImageDraw.Draw(img)

    x = 88
    chip_f = font(FONT_REG, 22)
    tw = d.textlength(SERIES, font=chip_f)
    d.rounded_rectangle([x, 78, x + tw + 34, 124], radius=23,
                        fill=(30, 58, 92, 225), outline=(56, 120, 180, 170))
    d.text((x + 17, 101), SERIES, font=chip_f, fill=(176, 212, 242), anchor="lm")

    d.rectangle([x, 168, x + 60, 173], fill=CYAN)

    d.text((x, 202), TITLE_CN, font=font(FONT_BOLD, 60), fill=WHITE, anchor="la",
           stroke_width=2, stroke_fill=(5, 11, 22))
    d.text((x, 278), TITLE_SUB, font=font(FONT_BOLD, 60), fill=WHITE, anchor="la",
           stroke_width=2, stroke_fill=(5, 11, 22))

    d.text((x, 396), TECH, font=font(FONT_REG, 23), fill=DIM, anchor="la")
    d.text((x, 440), META, font=font(FONT_REG, 21), fill=FAINT, anchor="la")

    d.rectangle([x, 600, x + 300, 601], fill=(70, 100, 136))
    d.text((x, 620), SITE, font=font(FONT_REG, 20), fill=(112, 142, 178), anchor="la")
    return img.convert("RGB")


def main() -> None:
    bg = Image.open(BG)
    wide(bg).save(HERE / "cover-900x383.png", optimize=True)
    square(bg).save(HERE / "cover-500x500.png", optimize=True)
    csdn(bg).save(HERE / "cover-csdn-1280x720.png", optimize=True)
    print("done")


if __name__ == "__main__":
    main()
