from PIL import Image

# 16x16 Keywraith — a hooded spectral key-warden, trailing off into wisps.
#   X outline  H hood  h hood-highlight  F face-void  e eye-glint
#   B body     b body-glow  W wisp  K key-gold  k key-glint  . transparent
MAP = [
    "................",
    ".....XXXXX......",
    "....XHHHHHX.....",
    "...XHhHHHHHX....",
    "...XHFFFFFHX....",
    "...XHFeFeFHX....",
    "...XHFFFFFHX....",
    "....XBFFFBX.....",
    "...XBBBBBBBX....",
    "...XBbBBKBBX....",
    "..XBBBBXKXBBX...",
    "..XBBBBKkKBBX...",
    "..XWBBBXKXBWX...",
    "...XWBBBKBWX....",
    "....XWWBWWX.....",
    ".....X.W.X......",
]

PAL = {
    "X": (16, 26, 34, 255),
    "H": (56, 94, 110, 255),  "h": (96, 150, 168, 255),
    "F": (10, 14, 20, 255),   "e": (140, 236, 220, 255),
    "B": (74, 122, 138, 255), "b": (120, 180, 196, 255),
    "W": (60, 96, 112, 200),
    "K": (212, 172, 60, 255), "k": (255, 226, 120, 255),
}

img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
px = img.load()
for y, row in enumerate(MAP):
    for x, ch in enumerate(row):
        if ch != "." and ch in PAL:
            px[x, y] = PAL[ch]
img.save("keywraith.png")
img.resize((256, 256), Image.NEAREST).save("keywraith_x16.png")
print("done")
