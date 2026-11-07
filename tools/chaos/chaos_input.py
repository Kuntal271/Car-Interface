#!/usr/bin/env python3
"""Input abuse (E): the Wi-Fi host / port text boxes and the Apps search box.  `input text` only types ASCII (no emoji / RTL through the
IME on this image); the other character classes reach the same code through `--es set` (see chaos_persist.py) and the hostile package labels.

  * 20 000 characters typed into the host box (100 000 takes over 40 min through `input text`), 20 000 into the search box
  * rapid typing / deleting (every keystroke in the host box changes the connection key: count threads / sockets)
  * ports 0, 65536, 99999999999, -5, +7, 1e3, spaces; the stored port must stay within 1..65535
  * IME show / hide, hardware keyboard keys (TAB, ENTER, ESC, DPAD, CTRL+A, CTRL+V, DEL)
"""
import os, re, sys, time
sys.path.insert(0, os.path.dirname(__file__))
from lib import *

QUICK = "--quick" in sys.argv
HOST_BOX = (412 + 210, 456 + 28)
PORT_BOX = (412 + 440 + 80, 456 + 28)
SEARCH_BOX = (24 + 300, 80 + 28)


def type_text(s, chunk=3000):
    for i in range(0, len(s), chunk):
        part = s[i:i + chunk]
        for ch in " '&()<>|;*\\\"`$": part = part.replace(ch, "%s" if ch == " " else "")
        sh(f"input text '{part}'", timeout=300)


def stored(k):
    m = re.search(rf'<string name="{k}">([^<]*)</string>', settings_xml())
    return m.group(1) if m else None


reset_device()
srv = start_hostile("valid")
cold("settings", page="connections", reset=True, sets=f"source=ObdTcp,tcpHost=10.0.2.2,tcpPort={ELM_PORT}")
time.sleep(3)
logcat_clear()

# 1. giant paste into the host box
t0 = time.time()
tap_design(*HOST_BOX); time.sleep(.5)
type_text("a" * (3_000 if QUICK else 20_000))
dt = time.time() - t0
hostlen = len(stored("tcpHost") or "")
Result.add("input/host_20k_chars", alive() and hostlen <= 253, f"typed in {dt:.0f}s, stored host length {hostlen}, ui={'ok' if ui_texts() else 'DEAD'}")
t1 = time.time(); u = ui_texts(); Result.add("input/ui_responsive_after_20k", u is not None and time.time() - t1 < 20, f"dump took {time.time() - t1:.1f}s")
log = logcat_dump(os.path.join(LOGS, "input-host100k.logcat"))
Result.add("input/no_fatal_host20k", not fatal_in_app(log))

# 2. rapid typing and deleting in the host box: threads / fds must not run away
for _ in range(40): sh("input keyevent KEYCODE_DEL")
st0 = proc_stats() or {}
for i in range(8 if QUICK else 25):
    type_text("10.0.2." + str(i % 10), 100)
    for _ in range(3): sh("input keyevent KEYCODE_DEL")
time.sleep(3)
st1 = proc_stats() or {}
Result.add("input/rapid_typing_threads_fds", alive() and st1.get("threads", 0) - st0.get("threads", 0) < 40 and st1.get("fds", 0) - st0.get("fds", 0) < 40,
           f"threads {st0.get('threads')}->{st1.get('threads')} fds {st0.get('fds')}->{st1.get('fds')}")

# 3. ports
bad_ports = []
for val in ["0", "65536", "99999999999", "-5", "+7", "1e3", "   ", "35000", "00001"]:
    tap_design(*PORT_BOX); sh("input keyevent KEYCODE_MOVE_END")
    for _ in range(12): sh("input keyevent KEYCODE_DEL")
    type_text(val, 100)
    time.sleep(.6)
    p = stored("tcpPort")
    if p is None or not p.isdigit() or not (1 <= int(p) <= 65535): bad_ports.append((val, p))
Result.add("input/port_always_valid", not bad_ports and alive(), str(bad_ports))

# 4. IME + keyboard keys
for k in ["KEYCODE_TAB", "KEYCODE_ENTER", "KEYCODE_ESCAPE", "KEYCODE_DPAD_LEFT", "KEYCODE_DPAD_RIGHT", "KEYCODE_DPAD_UP", "KEYCODE_DPAD_DOWN", "KEYCODE_FORWARD_DEL",
          "KEYCODE_MOVE_HOME", "KEYCODE_PAGE_DOWN", "KEYCODE_CTRL_LEFT", "KEYCODE_SHIFT_LEFT", "KEYCODE_BACK", "KEYCODE_SPACE", "KEYCODE_NUMPAD_ENTER", "KEYCODE_CLEAR"]:
    tap_design(*HOST_BOX); sh(f"input keyevent {k}")
for k in (277, 278, 279): sh(f"input keyevent {k}")                            # cut / copy / paste
Result.add("input/keyboard_keys", alive() and ui_texts() is not None)

# 5. search box
cold("apps"); time.sleep(3)
tap_design(*SEARCH_BOX); time.sleep(.4)
t0 = time.time(); type_text("q" * (3_000 if QUICK else 20_000)); dt = time.time() - t0
log = logcat_dump(os.path.join(LOGS, "input-search.logcat"))
t1 = time.time(); u = ui_texts()
Result.add("input/search_20k", alive() and not fatal_in_app(log) and u is not None and time.time() - t1 < 20, f"typed in {dt:.0f}s, dump {time.time() - t1:.1f}s, texts={len(u or [])}")
for _ in range(5): sh("input keyevent KEYCODE_BACK")
stop_proc(srv)
sh(f"am force-stop {PKG}"); start("home", reset=True)
bad = [r for r in Result.rows if not r[1]]
print(f"{len(Result.rows) - len(bad)} passed, {len(bad)} failed")
sys.exit(1 if bad else 0)
