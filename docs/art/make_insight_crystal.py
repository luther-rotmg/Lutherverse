from PIL import Image

# 16x16 Insight Crystal — a twin-spired teal crystal with an inner glow.
#   X outline  C crystal  c crystal-light  g glow-core  s shard  . transparent
MAP = [
    "................",
    ".......X........",
    "......XcX...X...",
    "......XcCX.XsX..",
    ".....XcCCX..X...",
    ".....XcCgCX.....",
    "....XcCggCX.....",
    "....XcCggCCX....",
    "...XcCCgCCCX....",
    "...XcCCCCCCX....",
    "..XcCCCXCCCCX...",
    "..XCCCX.XCCX....",
    ".X.XCX...XCX....",
    "X...X.....X.....",
    ".X..s..s........",
    "................",
]

PAL = {
    "X": (12, 30, 38, 255),
    "C": (56, 160, 172, 255), "c": (140, 230, 235, 255),
    "g": (220, 255, 250, 255),
    "s": (90, 200, 210, 255),
}

img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
px = img.load()
for y, row in enumerate(MAP):
    for x, ch in enumerate(row):
        if ch != "." and ch in PAL:
            px[x, y] = PAL[ch]
img.save("insight_crystal.png")
img.resize((256, 256), Image.NEAREST).save("insight_crystal_x16.png")
print("done")
