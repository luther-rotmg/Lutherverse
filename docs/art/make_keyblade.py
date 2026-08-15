from PIL import Image

# 16x16 keyblade silhouette. Diagonal (tip top-right, handle bottom-left):
#   X outline  # blade  = blade-highlight  G guard  g guard-dark  H handle
#   T teeth    C keychain token  c token-highlight  . transparent
MAP = [
    "..........XXXX..",
    ".........X=##X..",   # key-bit block (top)
    ".........X#XXX..",   # notch tooth (gap)
    ".........X=##X..",   # key-bit block (bottom)
    "........XX=#X...",   # shaft leaves the bit
    ".......X=#X.....",
    "......X=#X......",
    ".....X=#X.......",
    "....X=#X........",
    "...X=#X.........",
    "..X=#X..........",
    ".XGGGGX.........",   # guard (rainguard box)
    ".XGg=gGX........",
    ".XGGGGX.........",
    "..XHHX..........",   # handle
    ".XCcX...........",   # keychain token
]

PALETTES = {
    "keyblade": {  # fire — gold blade, ember accents
        "X": (34, 20, 10, 255),
        "#": (196, 132, 46, 255), "=": (244, 208, 106, 255),
        "G": (200, 120, 30, 255), "g": (150, 84, 20, 255),
        "T": (255, 150, 40, 255),
        "H": (110, 60, 28, 255),
        "C": (255, 106, 43, 255), "c": (255, 210, 74, 255),
    },
    "frost_keyblade": {  # frost — icy blade, cyan accents
        "X": (18, 30, 40, 255),
        "#": (120, 190, 220, 255), "=": (214, 242, 251, 255),
        "G": (90, 160, 200, 255), "g": (52, 104, 140, 255),
        "T": (120, 220, 255, 255),
        "H": (39, 74, 90, 255),
        "C": (150, 224, 255, 255), "c": (255, 255, 255, 255),
    },
    "storm_keyblade": {  # storm — violet blade, electric gold accents
        "X": (26, 18, 42, 255),
        "#": (140, 110, 220, 255), "=": (208, 190, 255, 255),
        "G": (110, 80, 190, 255), "g": (70, 48, 130, 255),
        "T": (255, 240, 120, 255),
        "H": (58, 42, 96, 255),
        "C": (255, 232, 92, 255), "c": (255, 255, 210, 255),
    },
}

def build(name, pal):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y, row in enumerate(MAP):
        for x, ch in enumerate(row):
            if ch != "." and ch in pal:
                px[x, y] = pal[ch]
    img.save(name + ".png")
    img.resize((256, 256), Image.NEAREST).save(name + "_x16.png")
    return img

for name, pal in PALETTES.items():
    build(name, pal)
print("done")
