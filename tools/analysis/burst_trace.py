"""Second-by-second trace of one session: reported speed against distance from the start point.

usage: burst_trace.py <file.fast> [from_s] [to_s]

For each fix: the reported speed, the speed implied by the last position step, how far
the reported speeds say the runner has gone, and how far the positions say (straight
line from the first fix in the range). Distances only, never a position.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from session_report import read, dist  # noqa: E402

header, fixes, hearts, sats, ended = read(sys.argv[1])
start = int(header['startedElapsedMs'])
lo = float(sys.argv[2]) if len(sys.argv) > 2 else 0
hi = float(sys.argv[3]) if len(sys.argv) > 3 else 1e9
rows = [f for f in fixes if lo <= (f['t'] - start) / 1000 <= hi]
sat_at = {}
for s in sats:
    sat_at[round((s[0] - start) / 1000)] = (s[3], s[2])
origin = rows[0]['pos']
cum = 0.0
print(' t(s)  reported  step-speed  cum-reported(m)  from-start(m)  bearing  used/clear')
for i, f in enumerate(rows):
    sec = (f['t'] - start) / 1000
    step = ''
    if i > 0:
        dt = (f['t'] - rows[i - 1]['t']) / 1000
        cum += (f['v'] + rows[i - 1]['v']) / 2 / 3.6 * dt
        step = '%.1f' % (dist(rows[i - 1]['pos'], f['pos']) / dt * 3.6)
    used = sat_at.get(round(sec), ('', ''))
    print('%5.1f  %7.1f  %9s  %14.1f  %12.1f  %7s  %s/%s' % (
        sec, f['v'], step, cum, dist(origin, f['pos']), '%.0f' % f['bearing'] if f['bearing'] is not None else '-', used[0], used[1]))
