# Every key binding the pack's client jars register and the key it starts on (javap: the name and key code passed
# to KeyMapping's constructor), and which keys more than one binding starts on.
import collections, os, re, subprocess, sys, zipfile
mods = sys.argv[1]
esc = lambda t: t.replace("%", "%25").replace("\r", "").replace("\n", "%0A")
GLFW = {32: "SPACE", 39: "'", 44: ",", 45: "-", 46: ".", 47: "/", 59: ";", 61: "=", 91: "[", 92: "\\", 93: "]", 96: "`",
        256: "ESC", 257: "ENTER", 258: "TAB", 259: "BACKSPACE", 260: "INSERT", 261: "DELETE", 262: "RIGHT", 263: "LEFT",
        264: "DOWN", 265: "UP", 266: "PGUP", 267: "PGDN", 268: "HOME", 269: "END", 280: "CAPS", 340: "LSHIFT", 341: "LCTRL",
        342: "LALT", 343: "LSUPER", 344: "RSHIFT", 345: "RCTRL", 346: "RALT", -1: "unbound"}
def name(code):
    if 65 <= code <= 90 or 48 <= code <= 57:
        return chr(code)
    if 290 <= code <= 314:
        return f"F{code - 289}"
    if 320 <= code <= 329:
        return f"NUM{code - 320}"
    return GLFW.get(code, str(code))
rows = []
for jar in sorted(os.listdir(mods)):
    if not jar.endswith(".jar"):
        continue
    def scan(z, label):
        for n in z.namelist():
            if n.endswith(".jar"):
                try:
                    import io
                    scan(zipfile.ZipFile(io.BytesIO(z.read(n))), label)
                except Exception:
                    pass
            elif n.endswith(".class"):
                data = z.read(n)
                if b"KeyMapping" not in data or b"<init>" not in data:
                    continue
                path = f"/tmp/kd/{label}/{n}"
                os.makedirs(os.path.dirname(path), exist_ok=True)
                open(path, "wb").write(data)
                lines = subprocess.run(["javap", "-c", "-p", "-constants", path], capture_output=True, text=True).stdout.splitlines()
                for i, line in enumerate(lines):
                    if "invokespecial" in line and "KeyMapping.\"<init>\"" in line:
                        window = lines[max(0, i - 14):i]
                        key = next((m.group(1) for l in reversed(window) for m in [re.search(r"String ([\w.\-:/]+)", l)] if m), "?")
                        code = None
                        for l in window:
                            m = re.search(r"(?:bipush|sipush)\s+(-?\d+)|iconst_m1|InputConstants\.UNKNOWN", l)
                            if m:
                                code = int(m.group(1)) if m.group(1) else -1
                            elif re.search(r"iconst_m1", l):
                                code = -1
                        mouse = any("Type.MOUSE" in l for l in window)
                        rows.append((label, key, ("MOUSE" + str(code)) if mouse and code is not None else (name(code) if code is not None else "?")))
    scan(zipfile.ZipFile(os.path.join(mods, jar)), jar[:28])
by_key = collections.defaultdict(list)
for label, key, code in rows:
    by_key[code].append(f"{key} ({label})")
text = "\n".join(f"{code}: " + "; ".join(v) for code, v in sorted(by_key.items()) if code not in ("unbound",))
clash = "\n".join(f"{code}: " + "; ".join(v) for code, v in sorted(by_key.items()) if len(v) > 1 and code not in ("unbound", "?"))
for i in range(0, len(text), 3700):
    print(f"::notice title=keys-{i // 3700}::{esc(text[i:i + 3700])}")
print(f"::notice title=keys-shared::{esc(clash or 'none')}")
