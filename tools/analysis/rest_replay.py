"""Replays the work/rest segmenter over a real recording, to see where it called rests.

usage: rest_replay.py <session.fast> [--reported] [--old] [--still KMH] [--hr-veto BPM]

A port of WorkRestSegmenter (core/tracking) close enough to show its decisions:
each rest with its start, length, how far the player moved during it and his
heart rate.

  --reported   speeds as the receiver reported them (before Q17), not from positions
  --old        the rule before D41: a rest ends only by speed or by going 30 m away
  --still      the speed under which a player counts as standing (default 5.0)
  --hr-veto    a rest longer than two minutes whose heart, after its first two
               minutes, stays at or above BPM was play, not rest

Numbers only, never a position.
"""
import os
import statistics
import sys

sys.path.insert(0, os.path.dirname(__file__))
from session_report import read, dist  # noqa: E402

args = sys.argv[1:]
path = args[0]
REPORTED = '--reported' in args
OLD = '--old' in args
STILL_V = float(args[args.index('--still') + 1]) if '--still' in args else 5.0
HR_VETO = float(args[args.index('--hr-veto') + 1]) if '--hr-veto' in args else None

header, fixes, hearts, sats, ended = read(path)
start = int(header['startedElapsedMs'])

# Speeds as TrackAssembler makes them.
raw = []
for i, f in enumerate(fixes):
    p = fixes[i - 1] if i else None
    if not REPORTED and p and p['pos'] and f['pos'] and 0 < f['t'] - p['t'] <= 3000:
        v = dist(p['pos'], f['pos']) / ((f['t'] - p['t']) / 1000) * 3.6
    elif f['has']:
        v = f['v']
    else:
        continue
    if v > 45:
        continue
    raw.append((f, v))
pts = [f for f, _ in raw]
speeds = []
for i in range(len(raw)):
    window = sorted(v for _, v in raw[max(0, i - 1):i + 2])
    speeds.append(window[len(window) // 2])
calm = [sorted(speeds[max(0, i - 2):i + 3])[len(speeds[max(0, i - 2):i + 3]) // 2] for i in range(len(speeds))]

STILL_R, MIN_REST, RESUME_V, RESUME_HOLD, LEAVE_R = 12.0, 60_000, 7.2, 3_000, 30.0
RELOCATE_M, RELOCATE_MS = 15.0, 15_000


def within(p, a, r):
    return p is None or a is None or dist(p, a) <= r


segments, kind, seg_start = [], 'WORK', pts[0]['t']
still_since, anchor, resume_since, rest_start, lag = -1, None, -1, -1, -1


def close(end, nxt):
    global kind, seg_start
    if end > seg_start:
        segments.append([kind, seg_start, end])
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
                resume_since, rest_start, lag = -1, still_since, still_since
        elif slow:
            still_since, anchor = i, p['pos']
        else:
            still_since, anchor = -1, None
        continue
    if anchor is None:
        anchor = p['pos']
    resume_since = (resume_since if resume_since >= 0 else i) if speeds[i] >= RESUME_V else -1
    moving_again = resume_since >= 0 and p['t'] - pts[resume_since]['t'] >= RESUME_HOLD
    walked_away = not within(p['pos'], anchor, LEAVE_R)
    relocated = False
    if not OLD:
        while lag + 1 < i and pts[lag + 1]['t'] <= p['t'] - RELOCATE_MS:
            lag += 1
        relocated = lag >= rest_start and p['t'] - pts[lag]['t'] >= RELOCATE_MS and not within(p['pos'], pts[lag]['pos'], RELOCATE_M)
    if relocated and not moving_again:
        set_off = next((j for j in range(lag + 1, i + 1) if not within(pts[j]['pos'], pts[lag]['pos'], RELOCATE_M / 3)), i)
        close(pts[set_off]['t'], 'WORK')
        still_since, anchor = (i, p['pos']) if slow else (-1, None)
    elif moving_again:
        close(pts[resume_since]['t'], 'WORK')
        still_since, anchor = -1, None
    elif walked_away:
        close(p['t'], 'WORK')
        still_since, anchor = (i, p['pos']) if slow else (-1, None)
close(pts[-1]['t'], kind)

if HR_VETO is not None:
    for seg in segments:
        k, a, b = seg
        if k != 'REST' or b - a <= 120_000:
            continue
        later = [bpm for t, bpm in hearts if a + 120_000 <= t < b]
        if len(later) >= 30 and statistics.median(later) >= HR_VETO:
            seg[0] = 'WORK'

print('speeds from %s | rule %s | standing up to %.1f km/h%s' % (
    'the receiver' if REPORTED else 'positions', 'before D41' if OLD else 'D41', STILL_V,
    ' | heart veto at %d bpm' % HR_VETO if HR_VETO is not None else ''))
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
rests = [b - a for k, a, b in segments if k == 'REST']
print('rests: %d, %.1f min in total; longest %.1f min' % (len(rests), rest_total / 60000, max(rests or [0]) / 60000))
