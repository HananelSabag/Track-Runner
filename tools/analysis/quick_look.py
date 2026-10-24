"""One line per recording: what kind, how long, how many fixes and how often, and how it ended.

usage: quick_look.py <file.fast or .fast.part> [more ...]

Numbers only, never a position.
"""
import os
import statistics
import sys
from datetime import datetime, timezone

for path in sys.argv[1:]:
    header, fixes, sats, hearts, ended, lines = {}, [], 0, 0, None, 0
    for line in open(path, encoding='utf-8'):
        line = line.rstrip('\n')
        lines += 1
        if '=' in line and not line[:2] in ('G,', 'H,', 'S,', 'M,'):
            k, _, v = line.partition('=')
            header[k] = v
            continue
        c = line.split(',')
        if c[0] == 'G':
            fixes.append((int(c[1]), float(c[4]) if len(c) > 4 and c[4] else 0.0))
        elif c[0] == 'S':
            sats += 1
        elif c[0] == 'H':
            hearts += 1
        elif c[0] == 'M':
            ended = line
    start = int(header.get('startedElapsedMs', fixes[0][0] if fixes else 0))
    started = header.get('startedUtcMs')
    when = datetime.fromtimestamp(int(started) / 1000, timezone.utc).astimezone().strftime('%d.%m %H:%M:%S') if started else '?'
    if fixes:
        dts = [fixes[i][0] - fixes[i - 1][0] for i in range(1, len(fixes))]
        span = (fixes[-1][0] - fixes[0][0]) / 1000
        rate = (len(fixes) - 1) / span if span > 0 else 0
        top = max(v for _, v in fixes) * 3.6
        gaps = sum(1 for d in dts if d > 3000)
        print('%s %-11s %s | %s | app %s | fixes %d over %.0f s (%.2f/s, median gap %d ms, gaps>3s %d) | top reported %.1f km/h | sats %d | hr %d | end: %s' % (
            os.path.basename(path)[:8], header.get('kind'), when, header.get('device'), header.get('appVersion'), len(fixes), span, rate,
            statistics.median(dts) if dts else 0, gaps, top, sats, hearts, ended or 'NONE (cut off)'))
    else:
        print('%s %-11s %s | no fixes | sats %d | end: %s' % (os.path.basename(path)[:8], header.get('kind'), when, sats, ended or 'NONE'))
