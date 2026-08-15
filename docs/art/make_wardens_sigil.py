from PIL import Image

# 16x16 Warden's Sigil — a flat grey-slate stone graven with a gold binding sigil:
# a circle with a keyhole notch (round hole, stem, and the ring broken where the
# stem exits), with faint ward-light leaking out.
#   X outline  S slate  t slate-highlight  s slate-shadow
#   G sigil-gold  g gold-glint  w ward-glow  . transparent
MAP = [
    "................",
    ".....XXXXXX.....",
    "...XXtttSSSXX...",
    "..XttSSSSSSSsX..",
    ".XtSSSGGGGwSSsX.",
    ".XtSSGSSSSGSSsX.",
    ".XSSGSSggSSGSsX.",
    ".XSwGSSGGSSGSsX.",
    ".XSSGSSGGSSGSsX.",
    ".XSSSGSGGSGSssX.",
    ".XSSSSGwSGSSssX.",
    "..XSSSSSSSsssX..",
    "...XXssssssXX...",
    ".....XXXXXX.....",
    "................",
    "................",
]

PAL = {
    "X": (26, 26, 30, 255),
    "S": (108, 112, 120, 255),
    "t": (146, 152, 160, 255),
    "s": (74, 78, 86, 255),
    "G": (212, 172, 60, 255),
    "g": (255, 226, 120, 255),
    "w": (255, 240, 180, 200),
}

img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
px = img.load()
for y, row in enumerate(MAP):
    for x, ch in enumerate(row):
        if ch != "." and ch in PAL:
            px[x, y] = PAL[ch]
img.save("wardens_sigil.png")
img.resize((256, 256), Image.NEAREST).save("wardens_sigil_x16.png")
print("done")
