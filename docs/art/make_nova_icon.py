from PIL import Image

# 16x16 Keyblade Nova hero-ability icon: a diagonal mini-keyblade over a radiant burst.
#   X outline  # blade  = blade-highlight  G guard  H handle
#   r ray-far  R ray-near  . transparent
MAP = [
    ".......r........",
    ".......r....r...",
    "....r..R..r.....",
    ".....R.R.R.XXX..",
    "......RRR.X=#X..",
    "rrRR RRRRRX#XX..",  # burst heart (space stays transparent)
    "......RRRX=#X...",
    ".....R.RX=#X.r..",
    "....r..X=#XR....",
    ".......X#X.R.r..",
    "......X=#X..R...",
    ".....XGGGGX.....",
    ".....XG==GX.....",
    ".....XGGGGX.....",
    "......XHHX......",
    ".......XX.......",
]

PAL = {
    "X": (34, 20, 10, 255),
    "#": (196, 132, 46, 255), "=": (244, 208, 106, 255),
    "G": (200, 120, 30, 255),
    "H": (110, 60, 28, 255),
    "R": (140, 220, 255, 255), "r": (80, 150, 210, 255),
}

img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
px = img.load()
for y, row in enumerate(MAP):
    for x, ch in enumerate(row):
        if ch != "." and ch != " " and ch in PAL:
            px[x, y] = PAL[ch]
img.save("keyblade_nova.png")
img.resize((256, 256), Image.NEAREST).save("keyblade_nova_x16.png")
print("done")
