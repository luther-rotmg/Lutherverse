from PIL import Image

# 16x16 Wardstone Sentinel — a squat granite lock-golem: grey-blue stone slab body,
# carved gold keyhole glyph in the chest, cracked top-right corner, stubby legs.
#   X outline  S stone  s stone-shadow  t stone-highlight  C crack
#   K keyhole-gold  k gold-glint  o keyhole-void  . transparent
MAP = [
    "................",
    "..XXXXXXXXXXX...",
    "..XtttttttttC...",
    "..XtSSSSSSSSCX..",
    "..XSSsSSSsSSSX..",
    "..XSSSkKKKSSSX..",
    "..XSSSKooKSSSX..",
    "..XSSSKooKSSSX..",
    "..XSSSKooKSSSX..",
    "..XSSKooooKSSX..",
    "..XSSKKKKKKSSX..",
    "..XsSSSSSSSSsX..",
    "..XXXXXXXXXXXX..",
    "....XSX..XSX....",
    "....XsX..XsX....",
    "....XXX..XXX....",
]

PAL = {
    "X": (24, 28, 40, 255),
    "S": (106, 118, 140, 255), "s": (74, 84, 104, 255),
    "t": (150, 162, 184, 255), "C": (44, 50, 66, 255),
    "K": (212, 172, 60, 255),  "k": (255, 226, 120, 255),
    "o": (18, 20, 28, 255),
}

img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
px = img.load()
for y, row in enumerate(MAP):
    for x, ch in enumerate(row):
        if ch != "." and ch in PAL:
            px[x, y] = PAL[ch]
img.save("wardstone_sentinel.png")
img.resize((256, 256), Image.NEAREST).save("wardstone_sentinel_x16.png")
print("done")
