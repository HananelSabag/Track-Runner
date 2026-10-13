"""Feeds the Wear emulator a scripted training: one GPS fix a second, plus heart rate.

usage: simulate.py <scenario> [serial]

Each step of a scenario is (speed in km/h, seconds, heart rate the body is heading for).
The heart rate drifts towards that target with a lag and a little jitter, as a real one
does. That matters: the sensor reports only when its value changes, so a perfectly
steady simulated heart would look to the app like a sensor that has gone silent.
"""
import math
import os
import random
import subprocess
import sys
import time

ADB = os.path.expandvars(r"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe")
SERIAL = sys.argv[2] if len(sys.argv) > 2 else "emulator-5554"

# A made-up pitch; nothing to do with anywhere real.
LAT0, LON0 = 32.0800, 34.7800
M_PER_DEG_LAT = 111_320.0
M_PER_DEG_LON = 111_320.0 * math.cos(math.radians(LAT0))
PITCH_LENGTH_M = 60.0

SCENARIOS = {
    # (km/h, seconds, bpm)
    "football": [
        (10.0, 35, 150), (16.0, 1, 158), (22.0, 1, 162), (26.0, 1, 166), (27.4, 2, 170), (24.0, 1, 172),
        (9.0, 22, 165), (0.0, 78, 120),
        (11.0, 28, 150), (18.0, 1, 160), (24.0, 1, 165), (28.2, 3, 172), (21.0, 1, 174),
        (6.0, 18, 160), (12.0, 20, 162),
    ],
    "run": [
        (9.0, 20, 140), (12.5, 60, 155), (0.0, 30, 130), (13.0, 40, 160), (15.5, 20, 168), (10.0, 20, 160),
    ],
    "sprint": [
        (0.0, 3, 100), (6.0, 1, 110), (13.0, 1, 120), (19.0, 1, 130), (24.0, 1, 140), (27.0, 1, 150),
        (28.3, 4, 160), (25.0, 1, 165), (17.0, 1, 165), (8.0, 1, 160), (2.0, 2, 150), (0.0, 4, 140),
    ],
    "stand": [(0.0, 600, 90)],
}


def emu(*args):
    subprocess.run([ADB, "-s", SERIAL, "emu", *args], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def main():
    steps = SCENARIOS[sys.argv[1]]
    east = 0.0
    direction = 1
    last_bpm = None
    heart = float(steps[0][2])
    random.seed(7)
    tick = time.monotonic()
    for kmh, seconds, bpm in steps:
        for _ in range(seconds):
            step = kmh / 3.6
            if not 0.0 <= east + direction * step <= PITCH_LENGTH_M:
                direction = -direction
            east += direction * step
            lon = LON0 + east / M_PER_DEG_LON
            emu("geo", "fix", f"{lon:.7f}", f"{LAT0:.7f}", "20", "12", f"{kmh / 1.852:.2f}")
            heart += (bpm - heart) * 0.07 + random.uniform(-0.8, 0.8)
            if round(heart) != last_bpm:
                last_bpm = round(heart)
                emu("sensor", "set", "heart-rate", str(last_bpm))
            tick += 1.0
            time.sleep(max(0.0, tick - time.monotonic()))
    print("done", sys.argv[1])


main()
