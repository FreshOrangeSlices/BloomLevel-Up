import sys
t = open('report.txt').read()
start, count = int(sys.argv[1]), int(sys.argv[2])
esc = lambda x: x.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
chunks = [t[i:i + 3900] for i in range(0, len(t), 3900)]
levels = ['notice', 'warning', 'error']
for n in range(start, min(len(chunks), start + count)):
    lvl = levels[(n - start) // 10]
    print(f'::{lvl} title=probe-{n:02d} of {len(chunks)}::{esc(chunks[n])}')
