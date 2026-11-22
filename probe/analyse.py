import json, os, re, sys, glob
root = sys.argv[1]
dump = glob.glob(os.path.join(root, '**', 'bloom-dump.jsonl'), recursive=True)
tips = glob.glob(os.path.join(root, '**', 'tooltips.jsonl'), recursive=True)
out = []
p = out.append
p(f"dump files: {dump}; tooltips: {tips}")
rows = [json.loads(l) for l in open(dump[0]) if l.strip()]
items = [r for r in rows if r.get('kind') == 'item']
tipmap = {}
if tips:
    for l in open(tips[0]):
        if l.strip():
            try:
                t = json.loads(l); tipmap[t.get('id')] = t
            except Exception:
                pass
FAM = ('sword', 'axe', 'trident', 'bow', 'crossbow')
def fams(r):
    return [f for f in FAM if f"bloomlevelup:weapon/{f}" in r['tags'] and f"bloomlevelup:weapon/not_{f}" not in r['tags']]
TOOLS = ('PickaxeItem', 'ShovelItem', 'HoeItem', 'ShearsItem')
def line(r):
    c = r.get('classes', [])
    hooks = r.get('hooks')
    h = ','.join(k for k, v in (hooks or {}).items() if v) if isinstance(hooks, dict) else (','.join(hooks) if isinstance(hooks, list) else '')
    return (f"{r['id']} \"{r.get('name')}\" dmg {r.get('damage')} spd {r.get('speed')} fam [{','.join(fams(r))}] bc {r.get('bettercombat') or '-'}"
            f" cls {c[0] if c else '?'}{' < ' + c[1] if len(c) > 1 else ''} use {r.get('use')} hooks [{h}] tags [{','.join(t for t in r['tags'] if 'bloomlevelup' in t)}]")
removed = set()
melee_fams = ('sword', 'axe', 'trident')
A, B, C, T = [], [], [], 0
for r in items:
    c = r.get('classes', [])
    if 'c:hidden_from_recipe_viewers' in r['tags']:
        continue
    weaponish = (r.get('damage') or 0) > 0 or any(x in c for x in ('SwordItem', 'AxeItem', 'TridentItem', 'BowItem', 'CrossbowItem')) or r.get('bettercombat')
    if not weaponish or 'armor' in r:
        continue
    if any(x in c for x in TOOLS) and not r.get('bettercombat'):
        T += 1
        continue
    f = fams(r)
    melee = any(x in f for x in melee_fams)
    ranged = any(x in f for x in ('bow', 'crossbow'))
    bc = r.get('bettercombat')
    if not f:
        (C if not bc else A).append(r)
    elif melee and not bc:
        B.append(r)
p(f"items {len(items)}; tool-class items skipped {T}")
p(f"\n== A: has a Better Combat swing but no Bloom type ({len(A)})")
for r in sorted(A, key=lambda r: r['id']): p(line(r))
p(f"\n== B: in a Bloom melee type but no Better Combat swing ({len(B)})")
for r in sorted(B, key=lambda r: r['id']): p(line(r))
p(f"\n== C: does damage but neither a Bloom type nor a Better Combat swing ({len(C)})")
for r in sorted(C, key=lambda r: r['id']): p(line(r))
p("\n== Named matches (lich, kraken, mace, spear, trident, scythe, halberd, soul_render, annihilator)")
pat = re.compile(r'lich|kraken|mace|spear|trident|scythe|halberd|render|annihilat|glaive|staff', re.I)
for r in items:
    if pat.search(r['id']) or pat.search(r.get('name') or ''):
        tt = r.get('tooltip')
        tt = ' / '.join(tt)[:400] if isinstance(tt, list) else str(tt)[:400] if tt else ''
        p(line(r) + (f" || tip: {tt}" if tt else ''))
bcdir = glob.glob(os.path.join(root, '**', 'config', 'bettercombat'), recursive=True)
p(f"\n== Better Combat config dirs: {bcdir}")
for d in bcdir:
    for f in sorted(os.listdir(d)):
        txt = open(os.path.join(d, f), errors='replace').read()
        p(f"--- {d}/{f} ({len(txt)} chars)")
        p(txt[:6000])
open('report.txt', 'w').write('\n'.join(out))
print(len('\n'.join(out)), 'chars')
