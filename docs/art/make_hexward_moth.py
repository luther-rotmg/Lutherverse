from PIL import Image

# 16x16 HexwardMoth — a plump moth seen from above, dusty violet wings speckled
# with pale hex-runes, small dark body, two dot eyes, thin antennae.
#   X outline  a antenna  e eye-dot
#   V wing-violet  v wing-dust (lighter)  s hex-rune speckle (pale)
#   B body-dark    b body-highlight       . transparent
MAP = [
    "................",
    "....a......a....",
    ".....a....a.....",
    ".....XXXXXX.....",
    ".....XeBBeX.....",
    "..XXX.XBBX.XXX..",
    ".XVVVXXBBXXVVVX.",
    "XVVVVVXBbXVVVVVX",
    "XVsVVVXBBXVVVsVX",
    "XVVVVvXBBXvVVVVX",
    "XVVsVVXBbXVVsVVX",
    ".XVVVVXBBXVVVVX.",
    ".XVsVVXBBXVVsVX.",
    "..XVVVXBBXVVVX..",
    "...XVVXBBXVVX...",
    "....XXXXXXXX....",
]

PAL = {
    "X": (38, 26, 48, 255),
    "a": (70, 52, 84, 255),
    "e": (250, 240, 190, 255),
    "V": (122, 96, 150, 255),
    "v": (156, 130, 182, 255),
    "s": (226, 214, 240, 255),
    "B": (52, 40, 58, 255),
    "b": (86, 70, 96, 255),
}

img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
px = img.load()
for y, row in enumerate(MAP):
    for x, ch in enumerate(row):
        if ch != "." and ch in PAL:
            px[x, y] = PAL[ch]
img.save("hexward_moth.png")
img.resize((256, 256), Image.NEAREST).save("hexward_moth_x16.png")
print("done")
