"""Compares ways of turning a watch's positions into speeds, against the phone as the reference.

usage: position_speed_methods.py <watch.fast> <phone.fast or .part> [seconds]

For the first [seconds] (default 180) both recorded, prints best 1 s / 3 s and the
error against the phone's reported speed for each method; then, over the whole
watch session, how many seconds each method puts above 18 and 22 km/h. Numbers only.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from session_report import read, dist  # noqa: E402


def load(path):
    h, fixes, *_ = read(path)
    utc = {}
    for line in open(path, encoding='utf-8'):
        c = line.rstrip('\n').split(',')
        if c[0] == 'G' and len(c) >= 10:
            utc[int(c[1])] = int(c[2])
    for f in fixes:
        f['utc'] = utc[f['t']]
    return h, fixes


def median(v):
    return sorted(v)[len(v) // 2]


def backward(fixes):
    out = []
    for i, f in enumerate(fixes):
        p = fixes[i - 1] if i else None
        if p and p['pos'] and f['pos'] and 0 < f['t'] - p['t'] <= 3000:
            out.append(dist(p['pos'], f['pos']) / ((f['t'] - p['t']) / 1000) * 3.6)
        else:
            out.append(f['v'])
    return out


def central(fixes):
    out = []
    for i, f in enumerate(fixes):
        a = fixes[i - 1] if i else None
        b = fixes[i + 1] if i + 1 < len(fixes) else None
        if a and b and a['pos'] and b['pos'] and 0 < b['t'] - a['t'] <= 4000:
            out.append(dist(a['pos'], b['pos']) / ((b['t'] - a['t']) / 1000) * 3.6)
        else:
            out.append(backward(fixes[max(0, i - 1):i + 1])[-1])
    return out


def smooth(v, half):
    return [median(v[max(0, i - half):i + half + 1]) for i in range(len(v))]


def drop_fast(v, limit=45.0):
    return [x if x <= limit else 0.0 for x in v]


METHODS = {
    'reported (median 3)': lambda f: smooth([x['v'] for x in f], 1),
    'positions back (median 3)': lambda f: smooth(drop_fast(backward(f)), 1),
    'positions back (median 5)': lambda f: smooth(drop_fast(backward(f)), 2),
    'positions central (median 3)': lambda f: smooth(drop_fast(central(f)), 1),
}

wh, wf = load(sys.argv[1])
ph, pf = load(sys.argv[2])
window = int(sys.argv[3]) if len(sys.argv) > 3 else 180
t0 = max(wf[0]['utc'], pf[0]['utc'])
phone_by_s = {(f['utc'] - t0) // 1000: f['v'] for f in pf}
for name, method in METHODS.items():
    speeds = method(wf)
    by_s = {(f['utc'] - t0) // 1000: s for f, s in zip(wf, speeds)}
    shared = [s for s in range(window) if s in by_s and s in phone_by_s]
    errors = [by_s[s] - phone_by_s[s] for s in shared]
    best1 = max(by_s[s] for s in shared)
    best3 = max(sum(by_s.get(s + k, 0) for k in range(3)) / 3 for s in shared)
    above18 = sum(1 for s in speeds if s >= 18)
    above22 = sum(1 for s in speeds if s >= 22)
    print('%-30s first %ds: best 1 s %5.1f, best 3 s %5.1f, mean error vs phone %+5.1f, mean |error| %4.1f | whole session: s >= 18 km/h %4d, >= 22 %3d' % (
        name, window, best1, best3, sum(errors) / len(errors), sum(abs(e) for e in errors) / len(errors), above18, above22))
p3 = max(sum(phone_by_s.get(s + k, 0) for k in range(3)) / 3 for s in range(window))
print('%-30s first %ds: best 1 s %5.1f, best 3 s %5.1f' % ('phone, reported', window, max(phone_by_s.get(s, 0) for s in range(window)), p3))
