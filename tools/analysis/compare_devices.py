"""One run, two receivers: the watch's recording against the phone's reference measurement.

usage: compare_devices.py <watch.fast> <phone.fast>

The two files are lined up by the satellites' own clock, which both receivers stamp on
every fix, so no manual syncing is needed. For the stretch they share, it prints what
each receiver reported and what each one's positions imply, second by second around
the fastest part, and the totals.

Numbers only, never a position.
"""
import os
import statistics
import sys

sys.path.insert(0, os.path.dirname(__file__))
from session_report import read, dist  # noqa: E402


def load(path):
    header, fixes, hearts, sats, ended = read(path)
    utc = {}
    for line in open(path, encoding='utf-8'):
        c = line.rstrip('\n').split(',')
        if c[0] == 'G' and len(c) >= 10:
            utc[int(c[1])] = int(c[2])
    for f in fixes:
        f['utc'] = utc[f['t']]
    return header, fixes


def describe(name, header, fixes):
    dts = [fixes[i]['utc'] - fixes[i - 1]['utc'] for i in range(1, len(fixes))]
    rate = 1000 / statistics.median(dts) if dts else 0
    print('%-6s %s | %d fixes | median interval %d ms (%.1f a second) | speed accuracy %s | position accuracy %s' % (
        name, header.get('device'), len(fixes), statistics.median(dts) if dts else 0, rate,
        sorted(set(f['sacc'] for f in fixes))[:3], sorted(set(f['hacc'] for f in fixes))[:3]))


def per_second(fixes, t0, t1):
    """Reported speed and position-implied speed for each whole second of [t0, t1]."""
    out = {}
    for sec in range(int((t1 - t0) / 1000) + 1):
        lo, hi = t0 + sec * 1000, t0 + (sec + 1) * 1000
        inside = [f for f in fixes if lo <= f['utc'] < hi]
        if not inside:
            continue
        reported = sum(f['v'] for f in inside) / len(inside)
        before = [f for f in fixes if f['utc'] < lo]
        step = None
        if before and inside[-1]['pos'] and before[-1]['pos']:
            dt = (inside[-1]['utc'] - before[-1]['utc']) / 1000
            if 0 < dt < 3:
                step = dist(before[-1]['pos'], inside[-1]['pos']) / dt * 3.6
        out[sec] = (reported, step, inside[-1]['pos'])
    return out


def best(series, n):
    vals = [series.get(i) for i in range(max(series) + 1)] if series else []
    top = 0
    for i in range(len(vals) - n + 1):
        w = vals[i:i + n]
        if all(x is not None for x in w):
            top = max(top, sum(w) / n)
    return top


wh, wf = load(sys.argv[1])
ph, pf = load(sys.argv[2])
describe('watch', wh, wf)
describe('phone', ph, pf)
t0 = max(wf[0]['utc'], pf[0]['utc'])
t1 = min(wf[-1]['utc'], pf[-1]['utc'])
if t1 <= t0:
    sys.exit('The two recordings do not overlap in time.')
print('shared stretch: %.0f s' % ((t1 - t0) / 1000))
w, p = per_second(wf, t0, t1), per_second(pf, t0, t1)

wr = {k: v[0] for k, v in w.items()}
ws = {k: v[1] for k, v in w.items()}
pr = {k: v[0] for k, v in p.items()}
ps = {k: v[1] for k, v in p.items()}
print('best 3 s, km/h:  watch reported %.1f | watch positions %.1f | phone reported %.1f | phone positions %.1f' % (
    best(wr, 3), best(ws, 3), best(pr, 3), best(ps, 3)))

peak = max(pr, key=lambda k: pr[k]) if pr else 0
print('around the phone\'s fastest second (second | watch reported | watch positions | phone reported | phone positions | metres apart):')
for sec in range(max(0, peak - 8), peak + 9):
    if sec in w and sec in p:
        gap = dist(w[sec][2], p[sec][2]) if w[sec][2] and p[sec][2] else float('nan')
        fmt = lambda x: '%5.1f' % x if x is not None else '    -'
        print('  %4d | %s | %s | %s | %s | %5.1f' % (sec, fmt(w[sec][0]), fmt(w[sec][1]), fmt(p[sec][0]), fmt(p[sec][1]), gap))

common = sorted(set(w) & set(p))
if common:
    wd = sum(w[s][0] for s in common) / 3.6
    pd = sum(p[s][0] for s in common) / 3.6
    first, last = common[0], common[-1]
    print('distance over the shared stretch: watch speeds add up to %.0f m, phone speeds to %.0f m' % (wd, pd))
    print('straight line start to end: %.0f m by the watch, %.0f m by the phone' % (
        dist(w[first][2], w[last][2]), dist(p[first][2], p[last][2])))
