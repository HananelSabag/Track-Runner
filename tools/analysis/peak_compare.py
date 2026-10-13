"""Top speed two ways: from the speeds the receiver reports, and from the ground its positions cover.

usage: peak_compare.py <folder with .fast files>

For each session: the best speed held for 3 s and for 5 s according to the reported
speed (as the app computes it today), and according to the straight-line distance
between positions 3 s and 5 s apart. A straight line between two positions cannot be
inflated by jitter along the way, and a single wild position is voted out by taking
the median of neighbouring windows.

Numbers only, never a position.
"""
import glob
import os
import statistics
import sys

sys.path.insert(0, os.path.dirname(__file__))
from session_report import read, dist  # noqa: E402


def best_reported(fixes, win_ms):
    v = [f['v'] for f in fixes]
    # The app's own cleaning: median of three before anything else.
    calm = [v[0]] + [sorted(v[i - 1:i + 2])[1] for i in range(1, len(v) - 1)] + [v[-1]] if len(v) > 2 else v
    best, j = 0, 0
    for i in range(len(fixes)):
        while fixes[i]['t'] - fixes[j]['t'] > win_ms + 250:
            j += 1
        if fixes[i]['t'] - fixes[j]['t'] >= win_ms - 250:
            best = max(best, sum(calm[j:i + 1]) / (i - j + 1))
    return best


def displacement_speeds(fixes, win_ms):
    """Speed over every window of about win_ms, from the straight line between its two ends."""
    out = []
    j = 0
    for i in range(len(fixes)):
        while fixes[i]['t'] - fixes[j]['t'] > win_ms + 250:
            j += 1
        span = fixes[i]['t'] - fixes[j]['t']
        if span >= win_ms - 250 and fixes[i]['pos'] and fixes[j]['pos']:
            out.append(dist(fixes[j]['pos'], fixes[i]['pos']) / (span / 1000) * 3.6)
        else:
            out.append(None)
    return out


def best_displacement(fixes, win_ms):
    s = displacement_speeds(fixes, win_ms)
    best = 0
    for i in range(1, len(s) - 1):
        trio = [x for x in s[i - 1:i + 2] if x is not None]
        if len(trio) == 3:
            best = max(best, statistics.median(trio))
    return best


print('%-9s %8s | %-23s | %-23s | %s' % ('session', 'duration', 'best 3 s (reported / positions)', 'best 5 s (reported / positions)', 'reported is lower by'))
for p in sorted(glob.glob(os.path.join(sys.argv[1], '*.fast')), key=lambda p: int(read(p)[0]['startedUtcMs'])):
    header, fixes, hearts, sats, ended = read(p)
    start = int(header['startedElapsedMs'])
    r3, d3 = best_reported(fixes, 3000), best_displacement(fixes, 3000)
    r5, d5 = best_reported(fixes, 5000), best_displacement(fixes, 5000)
    dur = (fixes[-1]['t'] - start) / 1000
    print('%-9s %5d:%02d | %10.1f / %-10.1f | %10.1f / %-10.1f | %3.0f%% (3 s), %3.0f%% (5 s)' % (
        os.path.basename(p)[:8], dur // 60, dur % 60, r3, d3, r5, d5, (1 - r3 / d3) * 100 if d3 else 0, (1 - r5 / d5) * 100 if d5 else 0))
