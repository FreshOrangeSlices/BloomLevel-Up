import sys
blob = open(sys.argv[1]).read()
start, end = int(sys.argv[2]), int(sys.argv[3])
for n in range(start, end):
    chunk = blob[n * 3900:(n + 1) * 3900]
    if chunk:
        print(f"::notice title=out-{n:02d}::" + chunk.replace("%", "%25").replace("\r", "").replace("\n", "%0A"))
