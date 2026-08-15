from PIL import Image

# 16x16 TumblerWisp — a tiny teal-white mote of locksmith's magic, airy and mostly
# transparent: a bright core, a faint glow diamond, four trailing sparks, and a
# two-pixel gold key-silhouette glint hanging beneath the core.
#   W core-white  C core-teal  g glow (faint)  s trailing spark
#   K key-gold    k key-glint  . transparent
MAP = [
    "................",
    "................",
    ".......g........",
    "......gCg.......",
    ".....gCWCg......",
    ".....CWWWC......",
    ".....gCWCg......",
    "......gCg.......",
    ".......K........",
    ".......k........",
    "......s.........",
    ".....s..........",
    "....s...........",
    "..s.............",
    "................",
    "................",
]

PAL = {
    "W": (236, 255, 250, 255),
    "C": (140, 236, 220, 255),
    "g": (96, 190, 178, 120),
    "s": (140, 236, 220, 160),
    "K": (212, 172, 60, 255),
    "k": (255, 226, 120, 255),
}

img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
px = img.load()
for y, row in enumerate(MAP):
    for x, ch in enumerate(row):
        if ch != "." and ch in PAL:
            px[x, y] = PAL[ch]
img.save("tumbler_wisp.png")
img.resize((256, 256), Image.NEAREST).save("tumbler_wisp_x16.png")
print("done")
