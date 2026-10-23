"""Draws a guide for each Skill Hub tab's background picture: the picture's suggested size, with every
Major and weapon node where the hub will draw it. Mirrors HubScreen's layout (keep the numbers in sync).

    python3 docs/art/hub-templates/make_templates.py

Writes, per tab, <tab>_template.png (the exact size, 1 pixel = 1 GUI pixel: paint under these guides,
then hide them) and <tab>_guide_4x.png (4x bigger, with names). The hub centres the picture on the
middle of the tab's Majors, so any size works; bigger pictures just show more around the edges.
"""
import json
import math
import pathlib

from PIL import Image, ImageDraw, ImageFont

ROOT = pathlib.Path(__file__).resolve().parents[3]
DATA = ROOT / 'src/main/resources/data/bloomlevelup/bloom'
LANG = json.loads((ROOT / 'src/main/resources/assets/bloomlevelup/lang/en_us.json').read_text())
OUT = pathlib.Path(__file__).resolve().parent

# HubScreen constants
CELL, ICON = 28, 22
WEAPON_RX, WEAPON_RY, MAJOR_RX, MAJOR_RY = 100.0, 54.0, 168.0, 96.0
UNIVERSALS_GAP, UNIVERSALS_PER_ROW, GRID_COLUMNS = 30, 13, 8
MARGIN = 24  # suggested picture edge beyond the outermost node


def jround(x):
    """Java's Math.round."""
    return math.floor(x + 0.5)


def qualify(ref):
    return ref if ':' in ref else 'bloomlevelup:' + ref


def majors_in(tab):
    majors = []

    for file in sorted((DATA / 'major' / tab).glob('*.json')):
        raw = json.loads(file.read_text())

        if 'disabled' in raw.get('flags', []):
            continue

        majors.append({
            'id': f'bloomlevelup:{tab}/{file.stem}',
            'sort': raw.get('sort', 0),
            'group': qualify(raw['group']) if 'group' in raw else None,
            'waives': raw.get('waives', []),
        })

    return sorted(majors, key=lambda m: (m['sort'], m['id']))


def layout(majors):
    """Nodes as (kind, id or group, x, y) and lines as (x0, y0, x1, y1), canvas coordinates."""
    nodes, lines = [], []

    if not any(m['group'] for m in majors):
        for k, m in enumerate(majors):
            nodes.append(('major', m['id'], (k % GRID_COLUMNS) * CELL, (k // GRID_COLUMNS) * CELL))
        return nodes, lines

    trees, middle, universal = {}, [], []

    for m in majors:
        if m['group']:
            trees.setdefault(m['group'], []).append(m)
        elif m['waives']:
            middle.append(m)
        else:
            universal.append(m)

    for i, m in enumerate(middle):
        nodes.append(('center', m['id'], jround((i - (len(middle) - 1) / 2.0) * CELL), 0))

    for index, (group, members) in enumerate(trees.items()):
        angle = -math.pi / 2.0 + 2.0 * math.pi * index / len(trees)
        wx, wy = jround(math.cos(angle) * WEAPON_RX), jround(math.sin(angle) * WEAPON_RY)
        nodes.append(('weapon', group, wx, wy))
        lines.append((0, 0, wx, wy))
        ax, ay = math.cos(angle) * MAJOR_RX, math.sin(angle) * MAJOR_RY
        tx, ty = -math.sin(angle) * MAJOR_RX, math.cos(angle) * MAJOR_RY
        length = max(1e-6, math.hypot(tx, ty))
        tx, ty = tx / length, ty / length

        for j, m in enumerate(members):
            offset = (j - (len(members) - 1) / 2.0) * CELL
            mx, my = jround(ax + tx * offset), jround(ay + ty * offset)
            nodes.append(('major', m['id'], mx, my))
            lines.append((wx, wy, mx, my))

    top = int(MAJOR_RY) + 14 + UNIVERSALS_GAP

    for k, m in enumerate(universal):
        row, col = divmod(k, UNIVERSALS_PER_ROW)
        in_row = min(UNIVERSALS_PER_ROW, len(universal) - row * UNIVERSALS_PER_ROW)
        nodes.append(('major', m['id'], jround((col - (in_row - 1) / 2.0) * CELL), top + row * CELL))

    return nodes, lines


def name(kind, ref):
    if kind == 'weapon':
        return ref.rsplit('/', 1)[1].capitalize()
    key = 'bloom.major.' + ref.replace(':', '.').replace('/', '.') + '.name'
    return LANG.get(key, ref.rsplit('/', 1)[1])


def make(tab):
    nodes, lines = layout(majors_in(tab))
    min_x = min(x - 16 for _, _, x, _ in nodes)
    max_x = max(x + 16 for _, _, x, _ in nodes)
    min_y = min(y - 16 for _, _, _, y in nodes)
    max_y = max(y + (24 if kind == 'weapon' else 16) for kind, _, _, y in nodes)
    # HubScreen.anchorX/Y: Java int division truncates toward zero.
    anchor_x, anchor_y = int((min_x + max_x) / 2), int((min_y + max_y) / 2)
    width = (max_x - min_x + 2 * MARGIN + 7) // 8 * 8
    height = (max_y - min_y + 2 * MARGIN + 7) // 8 * 8
    # Where a canvas point lands in the picture (HubScreen draws it at anchor - size / 2).
    px = lambda x: x - anchor_x + width // 2
    py = lambda y: y - anchor_y + height // 2

    for scale, labels in ((1, False), (4, True)):
        # The 1x template is transparent (put it on a layer above your painting); the 4x guide is a dark picture to look at.
        image = Image.new('RGBA', (width * scale, height * scale), (0, 0, 0, 0) if scale == 1 else (23, 19, 15, 255))
        draw = ImageDraw.Draw(image)
        s = lambda v: v * scale
        draw.rectangle([0, 0, s(width) - 1, s(height) - 1], outline=(255, 255, 255, 90))
        cx, cy = px(anchor_x), py(anchor_y)
        draw.line([s(cx) - s(4), s(cy), s(cx) + s(4), s(cy)], fill=(255, 255, 255, 60))
        draw.line([s(cx), s(cy) - s(4), s(cx), s(cy) + s(4)], fill=(255, 255, 255, 60))

        for x0, y0, x1, y1 in lines:
            draw.line([s(px(x0)), s(py(y0)), s(px(x1)), s(py(y1))], fill=(169, 159, 138, 110), width=2 * scale)

        font = ImageFont.load_default(size=11) if labels else None

        for kind, ref, x, y in nodes:
            half = 13 if kind in ('weapon', 'center') else ICON // 2
            colour = {'weapon': (229, 147, 63, 230), 'center': (224, 69, 58, 230)}.get(kind, (239, 230, 210, 200))
            box = [s(px(x) - half), s(py(y) - half), s(px(x) + half) - 1, s(py(y) + half) - 1]
            draw.rectangle(box, outline=colour, width=scale)

            if kind == 'weapon':
                draw.rectangle([s(px(x) - 14), s(py(y) + 14), s(px(x) + 14) - 1, s(py(y) + 21) - 1], outline=(229, 147, 63, 120))

            if labels:
                text = name(kind, ref)
                tw = draw.textlength(text, font=font)
                draw.text((s(px(x)) - tw / 2, box[3] + 3), text, fill=colour[:3] + (255,), font=font)

        suffix = 'template' if scale == 1 else 'guide_4x'
        image.save(OUT / f'{tab}_{suffix}.png')

    print(f'{tab}: {width} x {height} (Majors span {max_x - min_x} x {max_y - min_y})')


for tab in ('combat', 'utility', 'armor', 'misc'):
    make(tab)
