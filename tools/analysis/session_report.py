"""Reads recorded session files and reports what the receiver actually delivered.

usage: session_report.py <folder with .fast files>

Prints numbers only — durations, speeds, rates, correlations. It never prints a
position, so its output is safe to quote; the files themselves are not.
"""
import glob
import math
import os
import statistics
import sys


def dist(a, b):
    lat = math.radians((a[0] + b[0]) / 2)
    return 6371000 * math.hypot(math.radians(b[1] - a[1]) * math.cos(lat), math.radians(b[0] - a[0]))


def corr(x, y):
    n = len(x)
    if n < 3:
        return float('nan')
    mx, my = sum(x) / n, sum(y) / n
    sx = math.sqrt(sum((v - mx) ** 2 for v in x))
    sy = math.sqrt(sum((v - my) ** 2 for v in y))
    return sum((a - mx) * (b - my) for a, b in zip(x, y)) / (sx * sy) if sx and sy else float('nan')


def read(path):
    header, fixes, hearts, sats, ended = {}, [], [], [], False
    in_header = True
    for line in open(path, encoding='utf-8'):
        line = line.rstrip('\n')
        if in_header:
            if line == '#':
                in_header = False
            elif '=' in line:
                k, v = line.split('=', 1)
                header[k] = v
            continue
        c = line.split(',')
        if c[0] == 'G' and len(c) >= 10:
            fixes.append(dict(
                t=int(c[1]), has=c[3] == '1', v=float(c[4]) * 3.6,
                sacc=c[5], pos=(float(c[6]), float(c[7])) if c[6] and c[7] else None, hacc=c[8],
                bearing=float(c[9]) if c[9] else None,
            ))
        elif c[0] == 'H':
            hearts.append((int(c[1]), int(c[2])))
        elif c[0] == 'S':
            sats.append((int(c[1]), int(c[2]), int(c[3]), int(c[4])))
        elif c[0] == 'M':
            ended = True
    return header, fixes, hearts, sats, ended


def clock(ms):
    s = int(ms / 1000)
    return '%d:%02d' % (s // 60, s % 60)


def report(path):
    header, fixes, hearts, sats, ended = read(path)
    start = int(header['startedElapsedMs'])
    print('=' * 72)
    print(os.path.basename(path)[:8], header.get('kind'), '| app', header.get('appVersion'), '| ended cleanly:', ended)
    if not fixes:
        print('  no fixes')
        return
    dur = fixes[-1]['t'] - start
    dts = [fixes[i]['t'] - fixes[i - 1]['t'] for i in range(1, len(fixes))]
    with_speed = [f for f in fixes if f['has']]
    print('  duration %s | fixes %d (%d with speed) | first fix %.1f s after start' % (
        clock(dur), len(fixes), len(with_speed), (fixes[0]['t'] - start) / 1000))
    if dts:
        gaps = [d for d in dts if d > 1500]
        print('  fix interval: median %d ms, min %d, max %d | gaps over 1.5 s: %d (longest %.1f s)' % (
            statistics.median(dts), min(dts), max(dts), len(gaps), max(dts) / 1000))
        hist = {}
        for d in dts:
            b = min(int(round(d / 100.0)) * 100, 3000)
            hist[b] = hist.get(b, 0) + 1
        print('  interval histogram (ms: count):', ', '.join('%d: %d' % kv for kv in sorted(hist.items())))
    print('  accuracy fields: speed', sorted(set(f['sacc'] for f in fixes))[:4], 'position', sorted(set(f['hacc'] for f in fixes))[:4])

    v = [f['v'] for f in fixes]
    print('  speed km/h: max %.1f | mean %.1f | distinct step sizes %s' % (
        max(v), sum(v) / len(v), sorted(set(round(abs(v[i] - v[i - 1]), 2) for i in range(1, len(v))))[:6]))

    # Best average over 2 s and 3 s windows of the raw speed.
    for win in (2000, 3000):
        best = 0
        j = 0
        for i in range(len(fixes)):
            while fixes[i]['t'] - fixes[j]['t'] > win + 250:
                j += 1
            if fixes[i]['t'] - fixes[j]['t'] >= win - 250:
                best = max(best, sum(v[j:i + 1]) / (i - j + 1))
        print('  best %d s average: %.1f km/h' % (win / 1000, best))

    # The ten fastest seconds, in order, with what the positions say for the same second.
    step = [None]
    for i in range(1, len(fixes)):
        dt = (fixes[i]['t'] - fixes[i - 1]['t']) / 1000
        if fixes[i]['pos'] and fixes[i - 1]['pos'] and 0 < dt < 3:
            step.append(dist(fixes[i - 1]['pos'], fixes[i]['pos']) / dt * 3.6)
        else:
            step.append(None)
    peak = max(range(len(v)), key=lambda i: v[i])
    lo, hi = max(0, peak - 7), min(len(v), peak + 8)
    print('  around the fastest reading (time since start | reported km/h | from positions km/h):')
    print('   ', '  '.join('%s|%.1f|%s' % (clock(fixes[i]['t'] - start), v[i], '%.1f' % step[i] if step[i] is not None else '-') for i in range(lo, hi)))

    # Does the reported speed trail the positions? Correlate against the position speed of 0-4 s earlier.
    for smooth in (1, 2):
        out = []
        for lag in range(0, 5):
            xs, ys = [], []
            for i in range(lag + smooth, len(v)):
                window = step[i - lag - smooth + 1:i - lag + 1]
                if any(s is None for s in window):
                    continue
                xs.append(v[i])
                ys.append(sum(window) / len(window))
            out.append('%d s: %.3f' % (lag, corr(xs, ys)))
        print('  reported vs position speed (averaged over %d s), by how much earlier:' % smooth, ' | '.join(out), '(n=%d)' % len(xs))

    # Distances: what the speeds add up to, against the path the positions trace.
    dop = sum((v[i] + v[i - 1]) / 2 / 3.6 * (fixes[i]['t'] - fixes[i - 1]['t']) / 1000 for i in range(1, len(v)) if fixes[i]['t'] - fixes[i - 1]['t'] < 3000)
    path_m = sum(dist(fixes[i - 1]['pos'], fixes[i]['pos']) for i in range(1, len(fixes)) if fixes[i]['pos'] and fixes[i - 1]['pos'] and fixes[i]['t'] - fixes[i - 1]['t'] < 3000)
    print('  distance: %.0f m from speeds, %.0f m along the positions (ratio %.3f)' % (dop, path_m, dop / path_m if path_m else float('nan')))
    # The same, but only while moving faster than a walk, where position noise matters less.
    fast_d = fast_p = 0.0
    for i in range(1, len(v)):
        dt = (fixes[i]['t'] - fixes[i - 1]['t']) / 1000
        if dt < 3 and min(v[i], v[i - 1]) >= 7 and fixes[i]['pos'] and fixes[i - 1]['pos']:
            fast_d += (v[i] + v[i - 1]) / 2 / 3.6 * dt
            fast_p += dist(fixes[i - 1]['pos'], fixes[i]['pos'])
    if fast_p > 30:
        print('  while above 7 km/h: %.0f m from speeds, %.0f m along the positions (ratio %.3f)' % (fast_d, fast_p, fast_d / fast_p))

    if hearts:
        hd = [hearts[i][0] - hearts[i - 1][0] for i in range(1, len(hearts))]
        b = [h[1] for h in hearts]
        print('  heart rate: %d readings over %s | interval median %d ms, max %.1f s | bpm min %d, mean %d, max %d' % (
            len(hearts), clock(hearts[-1][0] - hearts[0][0]), statistics.median(hd) if hd else 0, max(hd) / 1000 if hd else 0,
            min(b), sum(b) / len(b), max(b)))
        changes = sum(1 for i in range(1, len(b)) if b[i] != b[i - 1])
        print('  heart rate changed value in %d of %d consecutive readings' % (changes, len(b) - 1))
    else:
        print('  heart rate: none recorded')
    if sats:
        used = [s[3] for s in sats]
        strong = [s[2] for s in sats]
        print('  satellites (%d reports): in use min %d / median %d / max %d | heard clearly min %d / median %d / max %d' % (
            len(sats), min(used), statistics.median(used), max(used), min(strong), statistics.median(strong), max(strong)))
    else:
        print('  satellites: none recorded')


if __name__ == '__main__':
    for p in sorted(glob.glob(os.path.join(sys.argv[1], '*.fast')), key=lambda p: int(read(p)[0]['startedUtcMs'])):
        report(p)
