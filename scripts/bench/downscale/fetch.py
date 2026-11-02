"""Pick a fixed random sample of v2 pages (3 per project) and download their images."""
import csv, glob, os, random, sys, time, urllib.request
root = sys.argv[1]; out = sys.argv[2]
meta = {}
with open(os.path.join(root, "v2_metadata.csv"), encoding="utf-8") as f:
    for r in csv.reader(f, delimiter="\t"):
        if len(r) >= 6 and r[5].startswith("http"):
            meta[(r[0].strip(), r[1], r[4])] = r[5]
rng = random.Random(20261102)
picked = []
for proj in sorted(os.listdir(os.path.join(root, "v2"))):
    files = sorted(glob.glob(os.path.join(root, "v2", proj, "*", "*.json")))
    files = [p for p in files if (proj, p.split("/")[-2], os.path.basename(p)[:-5]) in meta]
    picked += rng.sample(files, min(3, len(files)))
os.makedirs(out, exist_ok=True)
for p in picked:
    proj, book, fid = p.split("/")[-3], p.split("/")[-2], os.path.basename(p)[:-5]
    dst = os.path.join(out, f"{proj}_{book[:8]}_{fid}")
    if os.path.exists(dst + ".jpg"): continue
    try:
        req = urllib.request.Request(meta[(proj, book, fid)], headers={"User-Agent": "koten-ocr-eval"})
        data = urllib.request.urlopen(req, timeout=60).read()
        open(dst + ".jpg", "wb").write(data)
        os.system(f"cp '{p}' '{dst}.json'")
        print("ok", dst, len(data))
    except Exception as e:
        print("fail", dst, e)
    time.sleep(1)
