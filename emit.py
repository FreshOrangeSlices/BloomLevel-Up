import sys
s = open('dump.txt').read()
esc = lambda t: t.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
size = 3900
chunks = [s[i:i + size] for i in range(0, len(s), size)]
lo, hi = int(sys.argv[1]), int(sys.argv[2])
kinds = ['notice', 'warning', 'error']
for i in range(lo, min(hi, len(chunks))):
    k = kinds[((i - lo) // 10) % 3]
    print(f"::{k} title=dump-{i:03d}::{esc(chunks[i])}")
print(f"{len(chunks)} chunks total")
