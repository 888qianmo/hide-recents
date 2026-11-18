#!/usr/bin/env python3
"""生成模块界面预览图（按 MainActivity 的实际布局参数 1:1 还原，仅示意，非真机截图）。"""
from PIL import Image, ImageDraw, ImageFont

S = 2  # 1dp = 2px
W, H = 390, 844


def px(v):
    return int(v * S)


BG = "#F0F1F3"
CARD = "#FFFFFF"
TEXT = "#1C1B1C"
SUB = "#7C7275"
ACCENT = "#BE3455"
CHIP_BG = "#FFD9E0"
CHIP_FG = "#7A1230"
ALT = "#E7E8EB"
OUTLINE = "#E3D6D9"


def font(sp, bold=False):
    path = "/system/fonts/NotoSansCJK-Regular.ttc"
    for idx in (2, 0, 1, 3):
        try:
            return ImageFont.truetype(path, px(sp), index=idx)
        except Exception:
            continue
    return ImageFont.load_default()


def text(d, xy, s, sp, color=TEXT, bold=False, anchor="la"):
    d.text((px(xy[0]), px(xy[1])), s, font=font(sp, bold), fill=color, anchor=anchor)


img = Image.new("RGB", (px(W), px(H)), BG)
d = ImageDraw.Draw(img)

# 状态栏
text(d, (26, 14), "21:24", 13, TEXT)
d.rounded_rectangle([px(300), px(19), px(318), px(25)], radius=px(2), fill=TEXT)
d.ellipse([px(326), px(18), px(338), px(27)], fill=TEXT)
d.rounded_rectangle([px(344), px(19), px(358), px(26)], radius=px(2), fill=TEXT)
text(d, (364, 14), "5G", 11, SUB)

# 顶栏
text(d, (16, 46), "后台卡片隐藏", 22, TEXT, bold=True)
text(d, (16, 78), "最近任务里不出现勾选的应用", 12, SUB)

# Switch（任务书启用）
sx, sy = 306, 48
d.rounded_rectangle([px(sx), px(sy + 4), px(sx + 46), px(sy + 32)], radius=px(14), fill=ACCENT)
d.ellipse([px(sx + 18), px(sy + 8), px(sx + 42), px(sy + 28)], fill="#FFFFFF")
text(d, (sx - 26, sy + 10), "启用", 12, SUB)

# 状态卡
d.rounded_rectangle([px(16), px(100), px(374), px(176)], radius=px(20), fill=CARD)
text(d, (32, 118), "3", 26, TEXT, bold=True)
text(d, (58, 128), "个应用已隐藏", 13, TEXT)
text(d, (58, 148), "推荐作用域 · 系统框架（android）", 11, SUB)
d.rounded_rectangle([px(300), px(122), px(358), px(146)], radius=px(8), fill=CHIP_BG)
text(d, (329, 127), "推荐", 10, CHIP_FG, anchor="ma")

# 搜索框
d.rounded_rectangle([px(16), px(188), px(374), px(232)], radius=px(24), fill=ALT)
d.ellipse([px(30), px(202), px(42), px(214)], outline=SUB, width=px(1.2))
d.line([px(41), px(213), px(47), px(219)], fill=SUB, width=px(1.4))
text(d, (54, 201), "搜索应用名或包名", 14, SUB)

rows = [
    ("系", "#5C6B7F", "系统框架", "android", True),
    ("12", "#2F6FED", "123云盘", "com.mfcloudcalculate.networkdisk", False),
    ("Q", "#6C4CF1", "阿里云盘", "com.alicloud.databox", True),
    ("玩", "#4B5C7D", "爱玩机工具箱", "com.byyoung.setting", False),
    ("安", "#31A05F", "安全键盘", "com.oplus.securitykeyboard", True),
    ("安", "#2F80C9", "安全事件", "com.coloros.securityguard", False),
]

y = 244
for glyph, color, name, pkg, on in rows:
    d.rounded_rectangle([px(16), px(y), px(374), px(y + 66)], radius=px(20), fill=CARD)
    d.rounded_rectangle([px(28), px(y + 11), px(70), px(y + 53)], radius=px(12), fill=color)
    text(d, (49, y + 32), glyph, 15 if len(glyph) == 1 else 12, "#FFFFFF", anchor="mm")
    text(d, (80, y + 14), name, 15, TEXT)
    text(d, (80, y + 36), pkg, 11, SUB)
    cx, cy, r = 344, y + 33, 11
    if on:
        d.ellipse([px(cx - r), px(cy - r), px(cx + r), px(cy + r)], fill=ACCENT)
        d.line([px(cx - 4.5), px(cy), px(cx - 1), px(cy + 3.5), px(cx + 5), px(cy - 3.5)],
               fill="#FFFFFF", width=px(1.8), joint="curve")
    else:
        d.ellipse([px(cx - r), px(cy - r), px(cx + r), px(cy + r)], outline=OUTLINE, width=px(1.5))
    y += 76

# 底部按钮
d.rounded_rectangle([px(16), px(760), px(374), px(812)], radius=px(26), fill=ACCENT)
text(d, (195, 778), "保存并生效", 15, "#FFFFFF", bold=True, anchor="ma")

img.save("/root/workspace/hide-recents/preview.png")
print("saved preview.png", img.size)
