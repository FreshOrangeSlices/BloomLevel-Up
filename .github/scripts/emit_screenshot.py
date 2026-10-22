"""Prints one client-test screenshot as GitHub annotations (a small JPEG, base64, in 3900-character chunks).

Usage: emit_screenshot.py <screenshots dir> <name> [max chunks]
The annotations are titled shot-<name>-NN, so the picture can be put back together from the checks API
where downloading artifacts isn't possible. One step may carry 10 annotations of each kind (30 in all).
"""
import base64
import io
import pathlib
import sys

from PIL import Image

folder, name = pathlib.Path(sys.argv[1]), sys.argv[2]
limit = int(sys.argv[3]) if len(sys.argv) > 3 else 30
found = sorted(folder.rglob(name + '*.png'))

if not found:
    print(f'no screenshot named {name}')
    raise SystemExit(0)

image = Image.open(found[0]).convert('RGB')

if image.width > 960:
    image = image.resize((960, round(image.height * 960 / image.width)), Image.LANCZOS)

for quality in (60, 50, 40, 30, 20):
    out = io.BytesIO()
    image.save(out, 'JPEG', quality=quality, optimize=True)
    text = base64.b64encode(out.getvalue()).decode()
    chunks = [text[i:i + 3900] for i in range(0, len(text), 3900)]

    if len(chunks) <= limit:
        break

kinds = ['notice', 'warning', 'error']

for n, chunk in enumerate(chunks[:limit]):
    print(f'::{kinds[n // 10]} title=shot-{name}-{n:02d}::{chunk}')

print(f'{name}: {image.width}x{image.height}, quality {quality}, {len(chunks)} chunks')
