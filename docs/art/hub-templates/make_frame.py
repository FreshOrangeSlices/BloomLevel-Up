"""Gold nine-slice frame for the Skill Hub window: 28x28, 6-pixel border, transparent middle."""
import sys
from PIL import Image

SIZE, B = 28, 6
OUT = (43, 26, 6, 255)
SHINE = (255, 241, 168, 255)
LIGHT = (242, 196, 67, 255)
MID = (201, 143, 31, 255)
DEEP = (160, 110, 22, 255)
SHADOW = (107, 67, 12, 255)
CLEAR = (0, 0, 0, 0)
BEADS = [DEEP, MID, LIGHT, SHINE]  # one bead every 4 pixels along the middle band


def ring_color(d, lit, p):
    """Ring d (0 = outermost) of a side; lit = top/left side; p = position along the side (for the bead band)."""
    if d == 0 or d == 5:
        return OUT
    if d == 1:
        return SHINE if lit else SHADOW
    if d == 2:
        return LIGHT if lit else DEEP
    if d == 3:
        return BEADS[p % 4]
    if d == 4:
        return SHADOW if lit else LIGHT  # the inner bevel slopes the other way
    return CLEAR


def build():
    img = Image.new('RGBA', (SIZE, SIZE), CLEAR)
    px = img.load()

    for y in range(SIZE):
        for x in range(SIZE):
            dl, dr, dt, db = x, SIZE - 1 - x, y, SIZE - 1 - y
            d = min(dl, dr, dt, db)
            if d >= B:
                continue
            # Which side this pixel belongs to (corners split along the diagonal).
            side = min((dt, 't'), (dl, 'l'), (db, 'b'), (dr, 'r'))[1]
            lit = side in ('t', 'l')
            p = (x - B) if side in ('t', 'b') else (y - B)
            px[x, y] = ring_color(d, lit, p)

    # Corner studs: a small raised gem where the sides meet (the bevel stays around it).
    for cx, cy in ((0, 0), (SIZE - B, 0), (0, SIZE - B), (SIZE - B, SIZE - B)):
        for (dx, dy), c in {(2, 2): SHINE, (3, 2): LIGHT, (2, 3): LIGHT, (3, 3): SHADOW}.items():
            px[cx + dx, cy + dy] = c
    return img


def nine_slice(tex, w, h):
    """What the game draws: corners as they are, edges and middle tiled."""
    out = Image.new('RGBA', (w, h), CLEAR)
    S, b = tex.width, B
    inner = S - 2 * b
    def tile(region, x0, y0, x1, y1):
        piece = tex.crop(region)
        for yy in range(y0, y1, piece.height):
            for xx in range(x0, x1, piece.width):
                cw, ch = min(piece.width, x1 - xx), min(piece.height, y1 - yy)
                out.alpha_composite(piece.crop((0, 0, cw, ch)), (xx, yy))
    tile((0, 0, b, b), 0, 0, b, b)
    tile((S - b, 0, S, b), w - b, 0, w, b)
    tile((0, S - b, b, S), 0, h - b, b, h)
    tile((S - b, S - b, S, S), w - b, h - b, w, h)
    tile((b, 0, S - b, b), b, 0, w - b, b)
    tile((b, S - b, S - b, S), b, h - b, w - b, h)
    tile((0, b, b, S - b), 0, b, b, h - b)
    tile((S - b, b, S, S - b), w - b, b, w, h - b)
    return out


if __name__ == '__main__':
    tex = build()
    tex.save(sys.argv[1])
    if len(sys.argv) > 2:
        bg = Image.open(sys.argv[3]).convert('RGBA')
        W, H = 8 + 136 + bg.width + 12, 26 + bg.height + 14 + 12
        prev = Image.new('RGBA', (W, H), (60, 70, 80, 255))
        panel = Image.new('RGBA', (W - 12, H - 12), (20, 18, 16, 240))
        prev.alpha_composite(panel, (6, 6))
        prev.alpha_composite(bg, (6 + 4 + 136, 6 + 26))
        prev.alpha_composite(nine_slice(tex, W, H), (0, 0))
        prev.resize((W * 3, H * 3), Image.NEAREST).save(sys.argv[2])
        tex.resize((SIZE * 12, SIZE * 12), Image.NEAREST).save(sys.argv[2].replace('.png', '_tex.png'))
