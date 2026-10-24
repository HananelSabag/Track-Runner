"""Minute by minute: what the receiver reported, what the positions imply, the sky, the heart.

usage: minute_report.py <session.fast>

For reading a long session at a glance — where the signal was good, where the
player was moving, where the watch thought he was resting. Numbers only, never a position.
"""
import os
import statistics
import sys

sys.path.insert(0, os.path.dirname(__file__))
from session_report import read, dist  # noqa: E402

header, fixes, hearts, sats, ended = read(sys.argv[1])
start = int(header['startedElapsedMs'])
trim = None
for line in open(sys.argv[1], encoding='utf-8'):
    if line.startswith('M,') and 'AUTO_END' in line:
        parts = line.strip().split(',')
        trim = int(parts[3]) if len(parts) > 3 else None
print('kind', header.get('kind'), '| fixes', len(fixes), '| heart readings', len(hearts), '| satellite reports', len(sats),
      '| auto-ended, trimmed at minute %.1f' % ((trim - start) / 60000) if trim else '')
print('min | reported km/h: median  p90  max | positions m/min | moving s (>3 km/h) | sats used med / clear med | bpm med')
minutes = int((fixes[-1]['t'] - start) / 60000) + 1
for m in range(minutes):
    lo, hi = start + m * 60000, start + (m + 1) * 60000
    fx = [f for f in fixes if lo <= f['t'] < hi]
    if not fx:
        print('%3d | no fixes' % m)
        continue
    v = sorted(f['v'] for f in fx)
    walked = 0.0
    for a, b in zip(fx, fx[1:]):
        if a['pos'] and b['pos'] and 0 < b['t'] - a['t'] <= 3000:
            walked += dist(a['pos'], b['pos'])
    moving = sum(1 for f in fx if f['v'] > 3)
    st = [s for s in sats if lo <= s[0] < hi]
    hr = [b for t, b in hearts if lo <= t < hi]
    mark = ' <- trim' if trim and lo <= trim < hi else ''
    print('%3d | %5.1f %5.1f %5.1f | %6.0f | %3d | %3s / %3s | %s%s' % (
        m, statistics.median(v), v[int(len(v) * 0.9)], v[-1], walked, moving,
        statistics.median([s[3] for s in st]) if st else '-', statistics.median([s[2] for s in st]) if st else '-',
        statistics.median(hr) if hr else '-', mark))
