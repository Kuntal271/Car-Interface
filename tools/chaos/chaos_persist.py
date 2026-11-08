#!/usr/bin/env python3
"""Persistence abuse (D): corrupt / hostile SharedPreferences files under a stopped app, then cold start and check it comes up.

  python tools/chaos/chaos_persist.py
Cases cover cockpit_settings.xml (settings) and cockpit.xml (recent apps): empty, binary garbage, truncated XML, wrong types,
unknown enums, negative / overflowing numbers, NaN, a 1 MB host, an XML bomb, 100k keys; then: clear data while running,
delete the files while running, downgrade / upgrade install over existing data.
"""
import os, sys, time, random, tempfile
sys.path.insert(0, os.path.dirname(__file__))
from lib import *

SET = "shared_prefs/cockpit_settings.xml"
REC = "shared_prefs/cockpit.xml"
HDR = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n"


def put(rel, data):
    if isinstance(data, str): data = data.encode("utf-8", "surrogatepass")
    tmp = os.path.join(tempfile.gettempdir(), "chaos_pref.xml")
    open(tmp, "wb").write(data)
    adb("push", tmp, "/data/local/tmp/chaos_pref.xml")
    sh(f"run-as {PKG} mkdir -p shared_prefs")
    sh(f"run-as {PKG} cp /data/local/tmp/chaos_pref.xml {rel}")


def sstr(k, v): return f'<string name="{k}">{v}</string>\n'


CASES = {
    "empty_file": (SET, b""),
    "binary_garbage": (SET, bytes(random.Random(3).getrandbits(8) for _ in range(4096))),
    "truncated_xml": (SET, HDR + "<map>\n" + sstr("theme", "Midnight") + '<string name="tcpHost">10.0.'),
    "wrong_types": (SET, HDR + '<map>\n<int name="theme" value="3"/>\n<boolean name="source" value="true"/>\n<long name="tcpPort" value="99999999999"/>\n<float name="redline" value="NaN"/>\n<set name="tach"><string>1</string></set>\n<string name="perfOverlay">maybe</string>\n</map>\n'),
    "unknown_enums": (SET, HDR + "<map>\n" + sstr("theme", "Purple") + sstr("source", "Carrier\u00a0pigeon") + sstr("speed", "FURLONGS") + sstr("clock", "") + sstr("pressure", "bar") + "</map>\n"),
    "numbers": (SET, HDR + "<map>\n" + sstr("tcpPort", "-1") + sstr("redline", "99999999999999999999") + sstr("tach", "-5") + sstr("shift", "1e9") + sstr("speedScale", "0x10") + "</map>\n"),
    "nan_and_unicode_digits": (SET, HDR + "<map>\n" + sstr("tcpPort", "\uff13\uff15\uff10\uff10\uff10") + sstr("redline", "NaN") + sstr("tach", "\u0667\u0660\u0660\u0660") + "</map>\n"),
    "huge_host_1MB": (SET, HDR + "<map>\n" + sstr("source", "ObdTcp") + sstr("tcpHost", "a" * 1_000_000) + sstr("btName", "\u202e" * 100_000) + "</map>\n"),
    "xml_bomb": (SET, HDR + '<!DOCTYPE m [<!ENTITY a "aaaaaaaaaa"><!ENTITY b "&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;"><!ENTITY c "&b;&b;&b;&b;&b;&b;&b;&b;&b;&b;"><!ENTITY d "&c;&c;&c;&c;&c;&c;&c;&c;&c;&c;">]><map>' + '<string name="tcpHost">&d;&d;&d;&d;&d;&d;&d;&d;&d;&d;</string></map>\n'),
    "100k_keys": (SET, HDR + "<map>\n" + "".join(sstr(f"k{i}", "v") for i in range(100_000)) + "</map>\n"),
    "control_chars_host": (SET, HDR + "<map>\n" + sstr("source", "ObdTcp") + sstr("tcpHost", "10.0.2.2&#10;&#9;  ") + "</map>\n"),
    "recents_wrong_type": (REC, HDR + '<map>\n<int name="recent_apps" value="5"/>\n</map>\n'),
    "recents_garbage": (REC, HDR + "<map>\n" + sstr("recent_apps", ",,,,  ,x,\u200b,&#10;,a,b,c,d,e,f,g,h,i," + "z" * 100_000) + "</map>\n"),
    "recents_binary": (REC, bytes(random.Random(5).getrandbits(8) for _ in range(2048))),
}

QUICK = "--quick" in sys.argv
QUICK_SET = {"truncated_xml", "wrong_types", "huge_host_1MB", "recents_wrong_type", "recents_garbage", "unknown_enums"}
reset_device()
for name, (rel, data) in CASES.items():
    if QUICK and name not in QUICK_SET: continue
    sh(f"am force-stop {PKG}")
    sh(f"run-as {PKG} rm -f shared_prefs/cockpit_settings.xml shared_prefs/cockpit_settings.xml.bak shared_prefs/cockpit.xml shared_prefs/cockpit.xml.bak")
    put(rel, data)
    logcat_clear()
    screen = "apps" if rel == REC else "settings"
    start(screen, page="connections"); time.sleep(4)
    ok = alive() and ui_texts() is not None
    log = logcat_dump(os.path.join(LOGS, f"persist-{name}.logcat"))
    fat = fatal_in_app(log)
    # exercise the write path too: change a setting on the page, then a restart must still load
    if ok and screen == "settings":
        settings_cat(0); tap_design(1000, 186); time.sleep(.5)
    sh(f"am force-stop {PKG}"); start("settings", page="display"); time.sleep(3)
    ok2 = alive() and ui_texts() is not None
    log2 = logcat_dump(os.path.join(LOGS, f"persist-{name}.logcat"))
    Result.add(f"persist/{name}", ok and ok2 and not fatal_in_app(log2), ("FATAL " if fat or fatal_in_app(log2) else "") + ("" if ok and ok2 else "app dead or UI dead"))

# clear data / delete files while running
sh(f"am force-stop {PKG}"); start("settings", page="vehicle", reset=True); time.sleep(3)
logcat_clear()
sh(f"pm clear {PKG}"); time.sleep(1.5)
start("home"); time.sleep(3)
Result.add("persist/pm_clear_then_start", alive() and ui_texts() is not None)
start("settings", page="vehicle"); time.sleep(3)
sh(f"run-as {PKG} rm -rf shared_prefs"); time.sleep(.3)
settings_cat(0); tap_design(1000, 186); time.sleep(1)                          # a write after the file vanished under the running app
tap_design(1000, 258); time.sleep(1)
log = logcat_dump(os.path.join(LOGS, "persist-delete-running.logcat"))
Result.add("persist/delete_files_while_running", alive() and not fatal_in_app(log) and ui_texts() is not None)

# downgrade / upgrade install over existing data
base = os.path.join(ART, "baseline.apk"); fixed = os.path.join(ART, "fixed1.apk")
if os.path.exists(base) and os.path.exists(fixed):
    sh(f"am force-stop {PKG}"); start("settings", page="vehicle", sets="theme=Midnight,source=ObdTcp,tcpHost=10.0.2.2,tcpPort=35020,redline=6500,speed=MPH"); time.sleep(2)
    before = settings_xml()
    out1 = adb("install", "-r", "-d", base); out2 = adb("install", "-r", "-d", fixed); sh(f"cmd package set-home-activity {ACT}")
    after = settings_xml()
    start("home"); time.sleep(3)
    Result.add("persist/install_over_existing_data", "Success" in out1 and "Success" in out2 and before == after and alive() and ui_texts() is not None, f"settings file unchanged={before == after}")
sh(f"am force-stop {PKG}"); start("home", reset=True)
bad = [r for r in Result.rows if not r[1]]
print(f"{len(Result.rows) - len(bad)} passed, {len(bad)} failed")
sys.exit(1 if bad else 0)
