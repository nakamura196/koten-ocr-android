# Downscale A/B for the koten models (see docs/notes/box-touch-and-ios-parity.md, 縮小の補間).
# Needs ~/git/ndlkotenocr-lite/src and the NDL みんなで翻刻 dataset (fetch.py). Run: python3 eval.py <sample dir> <models dir> pil,bilinear,area
"""A/B the downscale method in the koten pipeline (RTMDet 1280 + PARSeq 32x384).
Variants: pil = Pillow default (bicubic, antialiased; ndlkotenocr-lite and close to iOS .high)
          bilinear = cv2 INTER_LINEAR (2x2 taps, no antialias; what Android's FILTER_BITMAP_FLAG does)
          area = cv2 INTER_AREA (box average; candidate fix)
Detection: line F1 at IoU>=0.5 vs GT boxes. Recognition: CER on GT line crops."""
import glob, json, os, sys
import numpy as np, cv2, yaml
from PIL import Image
sys.path.insert(0, os.path.expanduser("~/git/ndlkotenocr-lite/src"))
from rtmdet import RTMDet
from parseq import PARSEQ
M = sys.argv[2]
def rs(img, w, h, how):
    if how == "pil": return np.array(Image.fromarray(img).resize((w, h)))
    if how == "pilgap": return np.array(Image.fromarray(img).resize((w, h), reducing_gap=1.5))
    return cv2.resize(img, (w, h), interpolation={"bilinear": cv2.INTER_LINEAR, "area": cv2.INTER_AREA}[how])
class Det(RTMDet):
    how = "pil"
    def preprocess(self, img):
        m = max(img.shape[:2]); p = np.zeros((m, m, 3), np.uint8); p[:img.shape[0], :img.shape[1]] = img
        self.image_width = self.image_height = m
        r = rs(p, self.input_width, self.input_height, self.how)[:, :, ::-1]
        x = (r - np.array([103.53, 116.28, 123.675], np.float32)) / np.array([57.375, 57.12, 58.395], np.float32)
        return x.transpose(2, 0, 1)[None].astype(np.float32)
class Rec(PARSEQ):
    how = "pil"
    def preprocess(self, img):
        if img.shape[0] > img.shape[1]: img = np.ascontiguousarray(np.rot90(img))  # CCW like PIL ROTATE_90
        r = rs(img, self.input_width, self.input_height, self.how)[:, :, ::-1] / 255.0
        return (2.0 * (r - 0.5)).transpose(2, 0, 1)[None].astype(np.float32)
def lev(a, b):
    prev = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        cur = [i]
        for j, cb in enumerate(b, 1): cur.append(min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (ca != cb)))
        prev = cur
    return prev[-1]
def iou(a, b):
    ix = max(0, min(a[2], b[2]) - max(a[0], b[0])); iy = max(0, min(a[3], b[3]) - max(a[1], b[1]))
    u = (a[2]-a[0])*(a[3]-a[1]) + (b[2]-b[0])*(b[3]-b[1]) - ix*iy
    return ix*iy/u if u > 0 else 0
det = Det(f"{M}/rtmdet-s-1280x1280.onnx", None)
chars = list(yaml.safe_load(open(f"{M}/NDLmoji.yaml", encoding="utf-8"))["model"]["charset_train"])
rec = Rec(f"{M}/parseq-ndl-32x384-tiny-10.onnx", chars)
V = sys.argv[3].split(",")
tot = {v: dict(tp=0, nd=0, ng=0, ed=0, nc=0, hit=0, np=0, ngc=0) for v in V}
for jp in sorted(glob.glob(os.path.join(sys.argv[1], "*.json"))):
    try: img = np.array(Image.open(jp[:-5] + ".jpg").convert("RGB")); H, W = img.shape[:2]
    except Exception: print("skip-bad", os.path.basename(jp)); continue
    k = 4096 / max(W, H)  # app intake (ImageIntake.MAX_LONG_SIDE); inSampleSize + <=2x bilinear ~ area
    if k < 1:
        img = cv2.resize(img, (round(W * k), round(H * k)), interpolation=cv2.INTER_AREA)
    else: k = 1
    gt = []
    for o in json.load(open(jp, encoding="utf-8"))["words"]:
        xs = [p[0] for p in o["boundingBox"]]; ys = [p[1] for p in o["boundingBox"]]
        if o.get("text"): gt.append(([round(min(xs)*k), round(min(ys)*k), round(max(xs)*k), round(max(ys)*k)], o["text"]))
    if not gt or max(g[0][2] for g in gt) > W * k * 1.02 or max(g[0][3] for g in gt) > H * k * 1.02:
        print("skip", os.path.basename(jp), W, H); continue
    row = [os.path.basename(jp)[:-5], f"{W}x{H}"]
    for v in V:
        Det.how = v; Rec.how = "pil" if v == "pilgap" else v
        ds = [list(map(int, d["box"])) for d in det.detect(img)]
        used = set(); tp = 0
        for g, _ in gt:
            best = max(((iou(g, d), k) for k, d in enumerate(ds) if k not in used), default=(0, -1))
            if best[0] >= 0.5: used.add(best[1]); tp += 1
        ed = nc = 0
        for (x0, y0, x1, y1), t in gt:
            crop = img[max(0, y0):y1, max(0, x0):x1]
            if crop.size == 0: continue
            ed += lev(rec.read(crop), t); nc += len(t)
        from collections import Counter
        pred = Counter()
        for x0, y0, x1, y1 in ds:
            c = img[max(0, y0):max(0, y1), max(0, x0):max(0, x1)]
            if c.size: pred.update(rec.read(c))
        g = Counter("".join(t for _, t in gt)); hit = sum((pred & g).values())
        T = tot[v]; T["hit"] += hit; T["np"] += sum(pred.values()); T["ngc"] += sum(g.values())
        T = tot[v]; T["tp"] += tp; T["nd"] += len(ds); T["ng"] += len(gt); T["ed"] += ed; T["nc"] += nc
        row.append(f"{v}: F1 {2*tp/(len(ds)+len(gt)):.3f} CER {ed/max(nc,1):.4f} chF1 {2*hit/max(1,sum(pred.values())+sum(g.values())):.3f}")
    print(" | ".join(row), flush=True)
print("TOTAL")
for v in V:
    T = tot[v]
    print(f"{v:9s} lines {T['ng']} det {T['nd']} recall {T['tp']/T['ng']:.4f} precision {T['tp']/T['nd']:.4f} F1 {2*T['tp']/(T['nd']+T['ng']):.4f} | chars {T['nc']} CER {T['ed']/T['nc']:.4f} | page char F1 {2*T['hit']/(T['np']+T['ngc']):.4f}")
