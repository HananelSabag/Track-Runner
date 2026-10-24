"""Replays the work/rest segmenter over a real recording, to see where it called rests.

usage: rest_replay.py <session.fast> [--new]

A port of WorkRestSegmenter (core/tracking) close enough to show the decisions:
each rest with its start, length, how far the player moved during it and his heart
rate. --new applies the rule that also ends a rest when the player keeps moving
around (added on 2026-10-24 after a football session was ended while he played).

Numbers only, never a position.
"""
import os
import statistics
import sys

sys.path.insert(0, os.path.dirname(__file__))
from session_report import read, dist  # noqa: E402

NEW = '--new' in sys.argv
header, fixes, hearts, sats, ended = read(sys.argv[1])
start = int(header['startedElapsedMs'])
pts = [f for f in fixes if f['has']]

# TrackAssembler: median of three neighbours.
speeds = []
for i in range(len(pts)):
    window = sorted(p['v'] for p in pts[max(0, i - 1):i + 2])
    speeds.append(window[len(window) // 2])
# WorkRestSegmenter.medianFilter: median of five.
calm = []
for i in range(len(pts)):
    window = sorted(speeds[max(0, i - 2):i + 3])
    calm.append(window[len(window) // 2])

STILL_R, STILL_V, MIN_REST, RESUME_V, RESUME_HOLD, LEAVE_R = 12.0, 5.0, 60_000, 7.2, 3_000, 30.0
MOVING_V, MOVING_WINDOW, MOVING_NEEDED = 3.5, 30_000, 15_000


def within(p, a, r):
    return p is None or a is None or dist(p, a) <= r


segments, kind, seg_start = [], 'WORK', pts[0]['t']
still_since, anchor, resume_since = -1, None, -1


def close(end, nxt):
    global kind, seg_start
    if end > seg_start:
        segments.append((kind, seg_start, end))
    seg_start, kind = end, nxt


for i, p in enumerate(pts):
    slow = calm[i] <= STILL_V
    if kind == 'WORK':
        inside = within(p['pos'], anchor, STILL_R)
        if slow and still_since >= 0 and inside:
            if anchor is None:
                anchor = p['pos']
            if p['t'] - pts[still_since]['t'] >= MIN_REST:
                close(pts[still_since]['t'], 'REST')
                resume_since = -1
        elif slow:
            still_since, anchor = i, p['pos']
        else:
            still_since, anchor = -1, None
    else:
        if anchor is None:
            anchor = p['pos']
        resume_since = (resume_since if resume_since >= 0 else i) if speeds[i] >= RESUME_V else -1
        moving_again = resume_since >= 0 and p['t'] - pts[resume_since]['t'] >= RESUME_HOLD
        walked_away = not within(p['pos'], anchor, LEAVE_R)
        keeps_moving = False
        if NEW:
            recent = [j for j in range(i, -1, -1) if p['t'] - pts[j]['t'] < MOVING_WINDOW]
            moving_ms = sum(1000 for j in recent if calm[j] >= MOVING_V)
            keeps_moving = moving_ms >= MOVING_NEEDED and (p['t'] - seg_start) >= MOVING_WINDOW
        if moving_again:
            close(pts[resume_since]['t'], 'WORK')
            still_since, anchor = -1, None
        elif walked_away or keeps_moving:
            close(p['t'], 'WORK')
            still_since, anchor = (i, p['pos']) if slow else (-1, None)
close(pts[-1]['t'], kind)

print('rule:', 'new (also ends a rest when he keeps moving around)' if NEW else 'as on the watch (0.5.0)')
rest_total = 0
for k, a, b in segments:
    if k != 'REST':
        continue
    rest_total += b - a
    inside = [q for q in pts if a <= q['t'] < b]
    moved = sum(dist(x['pos'], y['pos']) for x, y in zip(inside, inside[1:]) if x['pos'] and y['pos'])
    hr = [bpm for t, bpm in hearts if a <= t < b]
    print('  rest from min %5.1f for %5.1f min | moved %5.0f m (%3.0f m/min) | bpm median %s' % (
        (a - start) / 60000, (b - a) / 60000, moved, moved / max((b - a) / 60000, 0.01), statistics.median(hr) if hr else '-'))
print('rests: %d, %.1f min in total; longest %.1f min' % (
    sum(1 for s in segments if s[0] == 'REST'), rest_total / 60000, max([(b - a) / 60000 for k, a, b in segments if k == 'REST'] or [0])))
