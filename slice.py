import sys
blob = open("lock.json").read()
start, end = int(sys.argv[1]), int(sys.argv[2])
for n in range(start, end):
    chunk = blob[n * 3900:(n + 1) * 3900]
    if chunk:
        print(f"::notice title=lock-json-{n:02d}::" + chunk.replace("%", "%25").replace("\\n", "%0A"))
