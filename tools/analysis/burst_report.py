"""Checks the reported speed against the ground actually covered, burst by burst.

usage: burst_report.py <folder with .fast files>

For every stretch of fast running it compares three things over the same window:
what the reported speeds add up to, the straight line between where the runner
was at the start and at the end, and the path traced by the positions in between.
It also asks which of the two leads: the reported speed, or the speed implied by
the positions.

Prints numbers only, never a position.
"""
import glob
import math
import os
import statistics
import sys

sys.path.insert(0, os.path.dirname(__file__))
from session_report import read, dist, corr, clock  # noqa: E402


def mean_pos(fixes, i, span=1):
    pts = [f['pos'] for f in fixes[max(0, i - span):i + span + 1] if f['pos']]
    return (sum(p[0] for p in pts) / len(pts), sum(p[1] for p in pts) / len(pts))


def analyse(path):
    header, fixes, hearts, sats, ended = read(path)
    start = int(header['startedElapsedMs'])
    v = [f['v'] for f in fixes]
    t = [f['t'] for f in fixes]
    print('=' * 72)
    print(os.path.basename(path)[:8], '| fixes', len(fixes), '| reported max %.1f km/h' % max(v))

    step = [None]
    for i in range(1, len(fixes)):
        dt = (t[i] - t[i - 1]) / 1000
        step.append(dist(fixes[i - 1]['pos'], fixes[i]['pos']) / dt * 3.6 if 0 < dt < 3 else None)

    # Which leads? Positive: the reported speed matches the positions of that many seconds EARLIER
    # (reported lags). Negative: it matches positions that come LATER (positions lag).
    out = []
    for lag in range(-4, 5):
        xs, ys = [], []
        for i in range(len(v)):
            j = i - lag
            if 1 <= j < len(v) and step[j] is not None:
                xs.append(v[i])
                ys.append(step[j])
        out.append('%+d s: %.3f' % (lag, corr(xs, ys)))
    print('  reported speed against position speed shifted by:', ' | '.join(out))

    # Bursts: contiguous stretches at or above 12 km/h (reported), merged across 2 s dips.
    fast = [i for i in range(len(v)) if v[i] >= 12]
    bursts = []
    for i in fast:
        if bursts and t[i] - t[bursts[-1][1]] <= 2500:
            bursts[-1][1] = i
        else:
            bursts.append([i, i])
    for a, b in bursts:
        if t[b] - t[a] < 2000:
            continue
        # A margin on each side, so positions that trail behind have time to catch up.
        lo = a
        while lo > 0 and t[a] - t[lo - 1] <= 4000:
            lo -= 1
        hi = b
        while hi < len(v) - 1 and t[hi + 1] - t[b] <= 6000:
            hi += 1
        reported_m = sum((v[i] + v[i - 1]) / 2 / 3.6 * (t[i] - t[i - 1]) / 1000 for i in range(lo + 1, hi + 1))
        path_m = sum(dist(fixes[i - 1]['pos'], fixes[i]['pos']) for i in range(lo + 1, hi + 1))
        straight_m = dist(mean_pos(fixes, lo), mean_pos(fixes, hi))
        top = max(v[a:b + 1])
        peak_pos = max(s for s in step[a:hi + 1] if s is not None)
        # Best 2 s average from each source inside the burst window.
        def best(series, lo_i, hi_i, win=2000):
            best_v, j = 0, lo_i
            for i in range(lo_i, hi_i + 1):
                while t[i] - t[j] > win + 250:
                    j += 1
                vals = [series[k] for k in range(j, i + 1) if series[k] is not None]
                if t[i] - t[j] >= win - 250 and vals:
                    best_v = max(best_v, sum(vals) / len(vals))
            return best_v
        print('  burst at %s for %.0f s: reported top %.1f (best 2 s %.1f) | positions top %.1f (best 2 s %.1f)' % (
            clock(t[a] - start), (t[b] - t[a]) / 1000, top, best(v, lo, hi), peak_pos, best(step, lo, hi)))
        print('      over %.0f s incl. margins: reported speeds add up to %.0f m | straight line %.0f m | position path %.0f m'
              ' | straightness %.2f | reported/straight %.2f' % (
                  (t[hi] - t[lo]) / 1000, reported_m, straight_m, path_m, straight_m / path_m if path_m else 0,
                  reported_m / straight_m if straight_m else 0))

    if sats:
        strong = sorted(s[2] for s in sats)
        used = sorted(s[3] for s in sats)
        q = lambda xs, p: xs[min(len(xs) - 1, int(len(xs) * p))]
        print('  satellites heard clearly: p5 %d, p25 %d, median %d, p75 %d | in use: p5 %d, median %d | reports with fewer than 3 clear: %d of %d' % (
            q(strong, .05), q(strong, .25), q(strong, .5), q(strong, .75), q(used, .05), q(used, .5), sum(1 for s in strong if s < 3), len(strong)))

    # Standing noise: what the receiver reports when the positions say nothing is moving.
    still = [v[i] for i in range(3, len(v)) if all(s is not None and s < 2.0 for s in step[i - 2:i + 1])]
    if len(still) >= 5:
        print('  while the positions barely move (%d s): reported speed median %.2f, p90 %.2f km/h' % (
            len(still), statistics.median(still), sorted(still)[int(len(still) * .9)]))


for p in sorted(glob.glob(os.path.join(sys.argv[1], '*.fast')), key=lambda p: int(read(p)[0]['startedUtcMs'])):
    analyse(p)
