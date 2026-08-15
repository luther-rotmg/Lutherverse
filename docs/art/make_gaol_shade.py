from PIL import Image

# 16x16 GaolShade — the Keywraith's crueler kin: same hooded-spectre silhouette,
# but a sickly green-grey palette and a small gold coin-pouch glint at the waist
# where the Keywraith carries its key.
#   X outline  H hood  h hood-highlight  F face-void  e eye-glint
#   B body     b body-glow  W wisp  P pouch-leather  g coin-glint  . transparent
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
    "...XBbBBBBBX....",
    "..XBBBBBPPBBX...",
    "..XBBBBPgPBBX...",
    "..XWBBBBPBBWX...",
    "...XWBBBBBWX....",
    "....XWWBWWX.....",
    ".....X.W.X......",
]

PAL = {
    "X": (16, 26, 20, 255),
    "H": (72, 96, 74, 255),   "h": (116, 148, 112, 255),
    "F": (10, 16, 12, 255),   "e": (170, 224, 128, 255),
    "B": (96, 120, 94, 255),  "b": (138, 168, 130, 255),
    "W": (76, 102, 80, 200),
    "P": (94, 74, 46, 255),   "g": (255, 216, 96, 255),
}

img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
px = img.load()
for y, row in enumerate(MAP):
    for x, ch in enumerate(row):
        if ch != "." and ch in PAL:
            px[x, y] = PAL[ch]
img.save("gaol_shade.png")
img.resize((256, 256), Image.NEAREST).save("gaol_shade_x16.png")
print("done")
