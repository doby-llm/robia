"""Round-3 dedicated matting benchmark. Run from the r2/seg venv with python -I.
Outputs one full-size RGBA PNG and one JSONL record per requested ID/method.
"""
import argparse, glob, json, os, resource, sys, time
from pathlib import Path
import numpy as np
import onnxruntime as ort
from PIL import Image, ImageOps

ROOT = Path(__file__).resolve().parents[1]
MD = ROOT / "models"
RAW = sorted(glob.glob(os.environ.get("ROBIA_PHOTOS", "data/photos_raw") + "/*.jpg"))
SPECS = {
    "u2netp": {"file":"u2netp.onnx", "size":320, "mean":(0.485,0.456,0.406), "std":(0.229,0.224,0.225), "sigmoid":False},
    "isnet": {"file":"isnet-general-use.onnx", "size":1024, "mean":(0.5,0.5,0.5), "std":(1,1,1), "sigmoid":False},
    "birefnet_lite_fp16": {"file":"birefnet_lite_fp16.onnx", "size":1024, "mean":(0.485,0.456,0.406), "std":(0.229,0.224,0.225), "sigmoid":True},
    "birefnet_lite_fp32": {"file":"birefnet_lite.onnx", "size":1024, "mean":(0.485,0.456,0.406), "std":(0.229,0.224,0.225), "sigmoid":True},
    "ben2": {"file":"BEN2_Base.onnx", "size":1024, "mean":(0,0,0), "std":(1,1,1), "sigmoid":False},
    "mvanet": {"file":"mvanet.onnx", "size":1024, "mean":(0.485,0.456,0.406), "std":(0.229,0.224,0.225), "sigmoid":False},
}

def app_image(i):
    im = ImageOps.exif_transpose(Image.open(RAW[i])).convert("RGB")
    im.thumbnail((1024,1024), Image.LANCZOS)
    return im

def session(path):
    so=ort.SessionOptions(); so.intra_op_num_threads=4; so.inter_op_num_threads=1; so.log_severity_level=3; so.enable_cpu_mem_arena=False; so.enable_mem_pattern=False; so.graph_optimization_level=ort.GraphOptimizationLevel.ORT_ENABLE_BASIC; so.intra_op_num_threads=3
    return ort.InferenceSession(str(path), so, providers=["CPUExecutionProvider"])

def mask(sess, spec, im):
    size=spec["size"]
    x=np.asarray(im.resize((size,size), Image.BILINEAR), dtype=np.float32)/255.0
    if spec["file"] == "u2netp.onnx": x=x/max(float(x.max()),1e-6)
    x=(x-np.asarray(spec["mean"],np.float32))/np.asarray(spec["std"],np.float32)
    x=x.transpose(2,0,1)[None]
    out=sess.run(None,{sess.get_inputs()[0].name:x})[0][0,0].astype(np.float32)
    if spec["sigmoid"]:
        out=1/(1+np.exp(-np.clip(out,-30,30)))
    else:
        lo,hi=float(out.min()),float(out.max()); out=(out-lo)/max(hi-lo,1e-6)
    return np.asarray(Image.fromarray(np.clip(out*255,0,255).astype(np.uint8)).resize(im.size,Image.BILINEAR),dtype=np.float32)/255.0

def save_rgba(im, alpha, path):
    rgb=np.asarray(im,dtype=np.uint8)
    a=np.clip(alpha*255,0,255).astype(np.uint8)
    Image.fromarray(np.dstack([rgb,a]),"RGBA").save(path)

def rss_mb():
    return resource.getrusage(resource.RUSAGE_SELF).ru_maxrss/1024.0

def main():
    ap=argparse.ArgumentParser(); ap.add_argument("out"); ap.add_argument("ids",nargs="+",type=int); ap.add_argument("--methods",default=",".join(SPECS)); ap.add_argument("--force",action="store_true")
    a=ap.parse_args(); out=Path(a.out); out.mkdir(parents=True,exist_ok=True)
    methods=[x for x in a.methods.split(",") if x]
    for n in methods:
        if n not in SPECS: raise SystemExit(f"unknown method {n}")
        if not (MD/SPECS[n]["file"]).exists(): raise SystemExit(f"missing {MD/SPECS[n]['file']}")
    for n in methods:
        od=out/n; od.mkdir(exist_ok=True)
        log=od/"times.jsonl"
        done={}
        if log.exists() and not a.force:
            for line in log.read_text().splitlines():
                try: done[int(json.loads(line)["i"])] = True
                except Exception: pass
        sess=session(MD/SPECS[n]["file"])
        # warm-up is deliberately excluded from per-photo timing.
        warm=app_image(a.ids[0]); mask(sess,SPECS[n],warm)
        with log.open("a") as f:
            for i in a.ids:
                p=od/f"{i:03d}.png"
                if i in done and p.exists() and not a.force: continue
                im=app_image(i); t=time.perf_counter(); al=mask(sess,SPECS[n],im); ms=(time.perf_counter()-t)*1000
                save_rgba(im,al,p)
                rec={"i":i,"ms":round(ms,2),"peak_rss_mb":round(rss_mb(),1)}; f.write(json.dumps(rec)+"\n"); f.flush(); print(n,rec,flush=True)
        del sess

if __name__=="__main__": main()
