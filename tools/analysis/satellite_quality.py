"""How the satellite-based fix quality behaves over a real recording.

usage: satellite_quality.py <session.fast> [more.fast ...]

The speed-test score falls back on satellites when the receiver reports no
believable accuracy (the OnePlus Watch 2 never does). This prints, for each
recording, how that quality figure is distributed over time and how long its dips
last, which is what decides whether "the weakest moment" is a fair summary of it.

Numbers only, never a position.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from session_report import read  # noqa: E402


def quality(used, strong):
    enough = min(1.0, max(0.0, (used - 4) / 6.0))
    clear = min(1.0, max(0.0, (strong - 1) / 5.0))
    return min(enough, clear)


def percentile(values, fraction):
    ordered = sorted(values)
    return ordered[min(len(ordered) - 1, int(len(ordered) * fraction))]


for path in sys.argv[1:]:
    header, fixes, hearts, sats, ended = read(path)
    if not sats:
        print(os.path.basename(path)[:8], 'no satellite records')
        continue
    q = [quality(s[3], s[2]) for s in sats]
    # A dip is a run of consecutive reports below "ready".
    dips, run = [], 0
    for value in q:
        if value < 0.4:
            run += 1
        elif run:
            dips.append(run)
            run = 0
    if run:
        dips.append(run)
    print('%s %-11s reports %4d | quality min %.2f  p10 %.2f  p25 %.2f  median %.2f | below 0.4: %2d%% of reports, %d dips, longest %d reports | in use min %d median %d | clear min %d median %d' % (
        os.path.basename(path)[:8], header.get('kind'), len(q), min(q), percentile(q, 0.10), percentile(q, 0.25), percentile(q, 0.50),
        round(100 * sum(1 for v in q if v < 0.4) / len(q)), len(dips), max(dips) if dips else 0,
        min(s[3] for s in sats), percentile([s[3] for s in sats], 0.5), min(s[2] for s in sats), percentile([s[2] for s in sats], 0.5)))
