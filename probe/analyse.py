import os, re, sys, json, zipfile, subprocess, urllib.request, traceback
def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "FreshOrangeSlices/BloomLevel-Up probe"})
    return urllib.request.urlopen(req, timeout=180).read()
def dec(j, cls, limit=8000, grep=None):
    r = subprocess.run(['java', '-jar', 'cfr.jar', j, '--jarfilter', '^' + re.escape(cls) + '$'], capture_output=True, text=True)
    lines = [l for l in r.stdout.splitlines() if not l.startswith(' *') and not l.startswith('import ') and l.strip()]
    if grep: lines = [l for l in lines if re.search(grep, l)]
    return '\n'.join(lines)[:limit]
jars = json.load(open('probe/jars3.json'))
WANT = {
 'monster-expansion': (r'foundry|guidebook|kobeko_locator', r'(?i)kobeko|arborix', r'Kobeko\w*Entity|Arborix\w*Entity'),
 'Skarrier Mobs': (r'totem_of|resisteel_ingot|deep_alloy_ingot|invulnerite_chunk|invulnerite_ingot|crushing_pauldron|quake_boots|serraxe|behemoth_blade|infernal_crown|semi_living', None, None),
 'borninchaos': (r'dark_metal|spiny_shell|nightmare|transmuting|charm', None, None),
 'myths-and-legends': (r'gargoyle|ironshade|bound_soul|soulbinder|cursed_core|stony_shield|spirit_suppressing|antique_emblem|stone_sanctum|altar_of_the_dead|winter_stalker|karakondjul_pelt|necklace|higa', None, r'BloodMoon\w*|Chronicler\w*|Research\w*'),
 'mutant-monsters': (r'.', None, r'ChemicalX\w*|CreeperMinionEgg\w*|MutantCreeper$'),
 'illager-invasion': (r'.', None, r'ImbuingTable\w*|HornOfSight\w*|LostCandle\w*'),
 'undead-unleashed': (r'.', None, None),
}
os.makedirs('probe-out', exist_ok=True)
for name, (recipe_pat, lang_pat, class_pat) in WANT.items():
    out = []; p = out.append
    try:
        open('j.jar', 'wb').write(get(jars[name])); z = zipfile.ZipFile('j.jar'); names = z.namelist()
        for n in names:
            if re.search(r'data/[^/]+/recipes?/.+\.json$', n) and re.search(recipe_pat, n.split('/')[-1]):
                txt = re.sub(r'\s+', ' ', z.read(n).decode('utf8', 'replace'))
                p(f"recipe {n.split('/')[-1][:-5]}: {txt[:600]}")
        for n in names:
            if re.search(r'loot_tables/(blocks|chests)/', n) and re.search(recipe_pat, n) and n.endswith('.json'):
                p(f"loot {n}")
        if lang_pat:
            for n in names:
                if n.endswith('en_us.json'):
                    for k, v in json.loads(z.read(n).decode('utf8', 'replace')).items():
                        if re.search(lang_pat, k) and not k.startswith('item.'): p(f"lang {k}: {str(v)[:300]}")
        if class_pat:
            for n in names:
                base = n.split('/')[-1][:-6] if n.endswith('.class') else ''
                if base and '$' not in base and re.fullmatch(class_pat, base):
                    src = dec('j.jar', n[:-6].replace('/', '.'), 6000, r'(?i)mobInteract|m_6071_|tame|isFood|Items\.|getItem\(\)|\.is\(|chance|days|night|config|static final|return .*[0-9]')
                    p(f"==== {n}\n{src}")
        for n in names:
            if re.search(r'(?i)config', n) and n.endswith(('.toml', '.json')) and 'lang' not in n:
                p(f"config file {n}: {re.sub(chr(10), ' ', z.read(n).decode('utf8','replace'))[:1200]}")
    except Exception:
        out.append(traceback.format_exc())
    open(os.path.join('probe-out', re.sub(r'\W+', '_', name) + '_facts.txt'), 'w').write('\n'.join(out))
open('report.txt', 'w').write('done')
