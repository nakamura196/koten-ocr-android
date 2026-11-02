import sys
from PIL import Image, ImageDraw, ImageFont
src, out = sys.argv[1], sys.argv[2]
FONT = "/System/Library/Fonts/ヒラギノ角ゴシック W{}.ttc"
f = lambda w, s: ImageFont.truetype(FONT.format(w), s)
ACCENT, INK, SUB = "#b5452f", "#2b2622", "#6b5f52"
icon = Image.open(src).convert("RGB")
icon.resize((512, 512), Image.LANCZOS).save(f"{out}/icon-512.png", optimize=True)
TEXT = {"ja": ("くずし字も活字も、", "撮るだけで文字に", "読み取りは端末の中だけ。無料"),
        "en": ("Photograph a page,", "get the text", "kuzushiji and modern Japanese · on-device · free")}
for lang, (l1, l2, l3) in TEXT.items():
    W, H = 1024, 500
    im = Image.new("RGB", (W, H), "#f3ebdc")
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, W, 8], fill=ACCENT)
    ic = icon.resize((260, 260), Image.LANCZOS)
    m = Image.new("L", ic.size, 0); ImageDraw.Draw(m).rounded_rectangle([0, 0, 259, 259], 58, fill=255)
    im.paste(ic, (80, 120), m)
    x = 400
    d.text((x, 120), "KotenOCR", font=f(8, 64), fill=INK)
    body = f(6, 40 if lang == "ja" else 40)
    d.text((x, 220), l1, font=body, fill=INK)
    d.text((x, 276), l2, font=body, fill=INK)
    d.text((x, 350), l3, font=f(3, 24 if lang == "ja" else 22), fill=SUB)
    im.save(f"{out}/feature-{lang}.png", optimize=True)
