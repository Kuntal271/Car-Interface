#!/usr/bin/env python3
"""Builds tiny hostile test APKs (manifest + resources only, no code) with aapt2 / zipalign / apksigner, into artifacts/apks/.

  python tools/chaos/mkapks.py [--bulk 150]

A launcher activity whose class does not exist is "an app that crashes on start" (ClassNotFoundException) without any dex.
Variants: empty / 10 000-character / emoji / RTL / control-character labels, no label at all, no launcher activity, disabled-able,
a vector icon that cannot be inflated, 4096x4096 icons (64 MB decoded each), and --bulk N plain apps for a long grid.
"""
import argparse, os, subprocess, sys, shutil
sys.path.insert(0, os.path.dirname(__file__))
from lib import ART, SDK, ROOT, PY

BT = os.path.join(SDK, "build-tools", "35.0.0")
AAPT2, ZIPALIGN, APKSIGNER = (os.path.join(BT, x) for x in ("aapt2", "zipalign", "apksigner"))
JAR = os.path.join(SDK, "platforms", "android-35", "android.jar")
OUT = os.path.join(ART, "apks"); WORK = os.path.join(ART, "apkwork"); KS = os.path.join(ART, "chaos.keystore")
JAVA_HOME = "/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
os.environ["JAVA_HOME"] = JAVA_HOME; os.environ["PATH"] = JAVA_HOME + "/bin:" + os.environ["PATH"]

ap = argparse.ArgumentParser(); ap.add_argument("--bulk", type=int, default=150); a = ap.parse_args()
os.makedirs(OUT, exist_ok=True); shutil.rmtree(WORK, ignore_errors=True); os.makedirs(WORK)
if not os.path.exists(KS):
    subprocess.check_call(["keytool", "-genkeypair", "-keystore", KS, "-storepass", "chaoschaos", "-keypass", "chaoschaos", "-alias", "c", "-keyalg", "RSA", "-keysize", "2048",
                           "-validity", "3650", "-dname", "CN=chaos"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def esc(s): return s.replace("&", "&amp;").replace("<", "&lt;").replace('"', "&quot;")


def build(pkg, label=None, launcher=True, activity="Main", exported=True, icon=None, res=None, min_sdk=28, extra_app=""):
    d = os.path.join(WORK, pkg); os.makedirs(os.path.join(d, "res"), exist_ok=True)
    lab = "" if label is None else f' android:label="{esc(label)}"'
    ic = f' android:icon="@drawable/{icon}"' if icon else ""
    cat = '<category android:name="android.intent.category.LAUNCHER"/>' if launcher else '<category android:name="android.intent.category.DEFAULT"/>'
    act = (f'<activity android:name=".{activity}" android:exported="{str(exported).lower()}"{lab}><intent-filter><action android:name="android.intent.action.MAIN"/>{cat}</intent-filter></activity>' if activity else "")
    open(os.path.join(d, "AndroidManifest.xml"), "w").write(
        f'<?xml version="1.0" encoding="utf-8"?><manifest xmlns:android="http://schemas.android.com/apk/res/android" package="{pkg}">'
        f'<uses-sdk android:minSdkVersion="{min_sdk}" android:targetSdkVersion="30"/><application{lab}{ic}{extra_app}>{act}</application></manifest>')
    flat = []
    for rel, content in (res or {}).items():
        p = os.path.join(d, "res", rel); os.makedirs(os.path.dirname(p), exist_ok=True)
        open(p, "wb" if isinstance(content, bytes) else "w").write(content)
    if res:
        subprocess.check_call([AAPT2, "compile", "--dir", os.path.join(d, "res"), "-o", os.path.join(d, "res.zip")])
        flat = [os.path.join(d, "res.zip")]
    unsigned = os.path.join(d, "u.apk"); aligned = os.path.join(d, "a.apk"); final = os.path.join(OUT, pkg + ".apk")
    subprocess.check_call([AAPT2, "link", "-o", unsigned, "-I", JAR, "--manifest", os.path.join(d, "AndroidManifest.xml"), "--min-sdk-version", str(min_sdk), "--target-sdk-version", "30", *flat])
    subprocess.check_call([ZIPALIGN, "-f", "-p", "4", unsigned, aligned])
    subprocess.check_call([APKSIGNER, "sign", "--ks", KS, "--ks-pass", "pass:chaoschaos", "--out", final, aligned], stdout=subprocess.DEVNULL)
    return final


def png(side):
    from PIL import Image
    p = os.path.join(WORK, f"icon{side}.png"); Image.new("RGB", (side, side), (40, 160, 255)).save(p, optimize=True); return open(p, "rb").read()


specs = {
    "chaos.label.empty": dict(label=""),
    "chaos.label.none": dict(label=None),
    "chaos.label.huge": dict(label="W" * 10_000),
    "chaos.label.emoji": dict(label="\U0001F697\U0001F600 \U0001F468\u200D\U0001F469\u200D\U0001F467\u200D\U0001F466 \U0001F1EE\U0001F1F3"),
    "chaos.label.rtl": dict(label="\u0645\u0631\u062d\u0628\u0627 \u0628\u0627\u0644\u0639\u0627\u0644\u0645 \u05e9\u05dc\u05d5\u05dd"),
    "chaos.label.ctrl": dict(label="A\tB\nC\u202eDCBA\u200b\u2028Z"),
    "chaos.label.longwords": dict(label=" ".join(["Supercalifragilistic"] * 40)),
    "chaos.crashy": dict(label="Crashes on start", activity="Missing"),
    "chaos.nolauncher": dict(label="No launcher", launcher=False),
    "chaos.noactivity": dict(label="No activity", activity=None),
    "chaos.victim": dict(label="Victim (will be removed)"),
    "chaos.disable": dict(label="Disable me"),
    "chaos.badicon": dict(label="Bad icon", icon="bad", res={"drawable/bad.xml": '<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="0" android:viewportHeight="0"><path android:pathData="M0,0L24,24" android:fillColor="#fff"/></vector>'}),
    "chaos.twin.a": dict(label="Twin"), "chaos.twin.b": dict(label="Twin"),
}
for i in range(12):
    specs[f"chaos.bigicon.n{i}"] = dict(label=f"Big icon {i}", icon="big", res={"drawable-nodpi/big.png": png(4096)})
for i in range(a.bulk):
    specs[f"chaos.bulk.n{i:03d}"] = dict(label=f"Bulk app {i:03d}")
for pkg, kw in specs.items():
    try:
        build(pkg, **kw)
    except subprocess.CalledProcessError as e:
        print("FAILED", pkg, e)
print(len(os.listdir(OUT)), "apks in", OUT)
