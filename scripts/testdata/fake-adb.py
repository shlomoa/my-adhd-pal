#!/usr/bin/env python3
"""Stateful fake of `adb` for scripts/test-run-poc.sh: simulates a watch (WATCH1) and a phone (PHONE1).

FAKE_DIR holds the state; FAKE_SCENARIO picks the behaviour: good, slow, zero, nobt, installfail, noperm, nophone.
It understands only the commands scripts/run-poc.sh sends and writes the same result files the apps write.
"""
import json
import os
import re
import sys

D = os.environ["FAKE_DIR"]
SC = os.environ.get("FAKE_SCENARIO", "good")
PKG = "com.shlomoa.myadhdpal.poc"
args = sys.argv[1:]
STATE = os.path.join(D, "state.json")


def load():
    if os.path.exists(STATE):
        return json.load(open(STATE))
    return {"running": False, "route": "", "label": "", "count": 0, "hold": False, "batch": 0, "listening": False, "installed": False}


def save(s):
    json.dump(s, open(STATE, "w"))


def files(serial):
    d = os.path.join(D, serial, "files")
    os.makedirs(d, exist_ok=True)
    return d


def out(text=""):
    sys.stdout.write(text + ("\n" if text and not text.endswith("\n") else ""))


def write_results(s):
    """Called at stop: write the watch and phone files for the run that just ended."""
    label = s["label"]
    wf, pf = files("WATCH1"), files("PHONE1")
    matrix = label[:3] in ("r1-", "r2-")
    n = 0 if SC == "zero" else (25 if matrix else max(s["count"], 5))
    watch_ms = 2000 if SC == "slow" else 80
    gap = 6000.0 if s["batch"] > 0 else 1000.0
    for i in range(1, n + 1):
        src = 1_000_000_000 * i
        route = s["route"]
        with open(os.path.join(wf, f"watch-{label}.csv"), "a") as f:
            f.write(f"{label},{route},{i},70,3,{src},{src + (watch_ms + i % 5) * 1_000_000},on\n")
        if matrix:
            for path, ms in (("dl", 200), ("bt", 300)):
                if path == "bt" and SC == "nobt":
                    continue
                with open(os.path.join(pf, f"phone-{label}-{path}.csv"), "a") as f:
                    f.write(f"{label},{i},70,{src},{src},0,{src + (ms + i % 5) * 1_000_000},on\n")
    with open(os.path.join(wf, f"watch-summary-{label}.txt"), "w") as f:
        f.write(f"label={label} route={s['route']}\nreadings={n}\n")
        f.write(f"gap_between_arrivals: n={max(n - 1, 0)} min=1.0ms p50=1.0ms p95={gap}ms max={gap}ms\n")


def start(s, extra):
    s.update(running=True, route=extra.get("route", "r2"), label=extra.get("label", ""), count=0,
             hold=extra.get("hold", "true") == "true", batch=int(extra.get("batch_us", 0)))
    if SC == "zero":
        with open(os.path.join(files("WATCH1"), "events.txt"), "a") as f:
            f.write("3s availability UNAVAILABLE_DEVICE_OFF_BODY\n")


def main():
    if args[0] == "devices":
        out("List of devices attached\nWATCH1\tdevice\nPHONE1\tdevice")
        return
    if args[0] == "logcat":
        out("I/POC: fake")
        return
    serial, rest = args[1], args[2:]
    s = load()
    cmd = " ".join(rest)
    if rest[0] == "install":
        if SC == "installfail":
            out("Failure [INSTALL_FAILED_FAKE]")
        else:
            s["installed"] = True
            out("Performing Streamed Install\nSuccess")
    elif "getprop ro.build.characteristics" in cmd:
        out("watch,nosdcard" if serial == "WATCH1" else "default")
    elif "getprop ro.product.model" in cmd:
        out("SM-FAKE")
    elif "getprop ro.build.version.release" in cmd:
        out("16")
    elif "getprop ro.build.version.sdk" in cmd:
        out("36")
    elif "pm list packages" in cmd:
        if s["installed"]:
            out(f"package:{PKG}")
    elif "pm grant" in cmd:
        if SC == "noperm" and "READ_HEART_RATE" in cmd:
            out("Exception occurred while executing 'grant':\njava.lang.SecurityException: fake")
    elif "settings get" in cmd:
        out("30000")
    elif "dumpsys power" in cmd:
        out("  mWakefulness=Awake")
        if s["running"] and s["hold"]:
            out("    PARTIAL_WAKE_LOCK 'poc:pulse' ACQ=-1s (uid=10200 pid=1)")
    elif "dumpsys display" in cmd:
        out("  mScreenState=ON")
    elif "dumpsys sensorservice" in cmd:
        out("Sensor List: heart rate")
        if s["running"] and s["route"] == "r2":
            out(f"  Operating Mode: NORMAL | {PKG} | WakeLockRefCount 0 | uid 10200")
    elif "dumpsys package" in cmd:
        out("  android.permission.health.READ_HEART_RATE: granted=true")
    elif "dumpsys activity services" in cmd:
        out("  * ServiceRecord{fake}")
    elif "am start" in cmd and "MainActivity" in cmd:
        s["listening"] = SC != "nophone"
        if s["listening"]:
            with open(os.path.join(files("PHONE1"), "events.txt"), "a") as f:
                f.write("1s listening\n")
    elif "am start" in cmd and "ControlActivity" in cmd:
        extra = dict(re.findall(r"--(?:es|ei|ez) (\w+) (\S+)", cmd))
        c = extra.get("cmd")
        if c == "start":
            start(s, extra)
        elif c == "stop" and s["running"]:
            write_results(s)
            s["running"] = False
            s["hold"] = False
        elif c == "wakelock":
            s["hold"] = extra.get("hold") == "true"
    elif "run-as" in cmd:
        d = files(serial)
        if "rm -f" in cmd:
            for name in os.listdir(d):
                os.remove(os.path.join(d, name))
        elif "ls files" in cmd:
            out("\n".join(sorted(os.listdir(d))))
        elif "cat files/status.txt" in cmd:
            if s["running"] and SC != "zero":
                s["count"] += 3
            out(f"running={str(s['running']).lower()} route={s['route']} label={s['label']} count={s['count']} "
                f"msSinceLastReading=300 wakeLockHeld={str(s['hold']).lower()} wakeUpSensor=true rateUs=0 "
                f"batchUs={s['batch']} btConnected=true error=none")
        else:
            m = re.search(r"cat files/(\S+)", cmd)
            if m and os.path.exists(os.path.join(d, m.group(1))):
                out(open(os.path.join(d, m.group(1))).read())
    save(s)


main()
