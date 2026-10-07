from PIL import Image

S = 12          # scale
N = 24          # source grid
img = Image.new("RGB", (N * S, N * S), "#1b1e26")
px = img.load()
BLUE = (59, 130, 246)
BG = (27, 30, 38)

def rect(x, y, w, h, color):
    for yy in range(y * S, (y + h) * S):
        for xx in range(x * S, (x + w) * S):
            px[xx, yy] = color

# three finder patterns (outer + hole)
for ox, oy in ((3, 3), (13, 3), (3, 13)):
    rect(ox, oy, 8, 8, BLUE)
    rect(ox + 2, oy + 2, 4, 4, BG)

# small modules
for sx, sy in ((13, 13), (18, 13), (13, 18), (18, 18)):
    rect(sx, sy, 3, 3, BLUE)

import os
os.makedirs("docs", exist_ok=True)
img.save("docs/logo.png")
img.resize((128, 128), Image.LANCZOS).save("docs/logo-128.png")
print("scritto docs/logo.png", img.size)
