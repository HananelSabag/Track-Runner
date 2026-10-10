// Generates every FAST logo asset from one geometry definition, so the SVGs in
// docs/brand and the Android launcher icons can never drift apart.
//
//   node tools/brand/generate.mjs
//
// Letters are drawn on a 100-unit-tall grid (y grows downward) and then slanted.

import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '../..');

const VOLT = '#C8FF2E';
const INK = '#0B0D0F';
const WHITE = '#F4F7F5';

const CAP = 100; // cap height
const SLANT = 0.2; // x shift per unit of height

// Upright letter outlines. Each letter is a list of polygons; a second polygon
// is a counter (hole), cut with the even-odd rule.
const LETTERS = {
  F: [[[0, 0], [72, 0], [72, 24], [24, 24], [24, 40], [60, 40], [60, 62], [24, 62], [24, 100], [0, 100]]],
  A: [
    [[0, 100], [0, 18], [18, 0], [54, 0], [72, 18], [72, 100], [48, 100], [48, 72], [24, 72], [24, 100]],
    [[24, 24], [48, 24], [48, 50], [24, 50]],
  ],
  S: [[[72, 0], [72, 24], [24, 24], [24, 38], [72, 38], [72, 100], [0, 100], [0, 76], [48, 76], [48, 62], [0, 62], [0, 0]]],
  T: [[[0, 0], [72, 0], [72, 24], [48, 24], [48, 100], [24, 100], [24, 24], [0, 24]]],
};
const LETTER_WIDTH = 72;
const LETTER_GAP = 14;

// Speed streaks trailing behind the F, as [x0, x1, y0, y1, opacity].
const STREAKS = [
  [-46, -12, 40, 53, 0.9],
  [-30, -12, 62, 75, 0.6],
  [-38, -12, 84, 97, 0.35],
];

const slant = ([x, y]) => [x + SLANT * (CAP - y), y];
const rect = (x0, x1, y0, y1) => [[x0, y0], [x1, y0], [x1, y1], [x0, y1]];
const shift = (poly, dx) => poly.map(([x, y]) => [x + dx, y]);

/** Shapes of the mark: the F plus its streaks, slanted, in grid units. */
function markShapes() {
  const letter = LETTERS.F.map((poly) => ({ points: poly.map(slant), opacity: 1 }));
  const streaks = STREAKS.map(([x0, x1, y0, y1, opacity]) => ({
    points: rect(x0, x1, y0, y1).map(slant),
    opacity,
  }));
  return [...streaks, ...letter];
}

/** Shapes of the full wordmark, streaks included. */
function wordmarkShapes() {
  const shapes = STREAKS.map(([x0, x1, y0, y1, opacity]) => ({
    points: rect(x0, x1, y0, y1).map(slant),
    opacity,
  }));
  [...'FAST'].forEach((ch, i) => {
    const dx = i * (LETTER_WIDTH + LETTER_GAP);
    shapes.push({ compound: LETTERS[ch].map((poly) => shift(poly, dx).map(slant)), opacity: 1 });
  });
  return shapes;
}

const polygons = (shape) => shape.compound ?? [shape.points];

function bounds(shapes) {
  const pts = shapes.flatMap((s) => polygons(s).flat());
  const xs = pts.map((p) => p[0]);
  const ys = pts.map((p) => p[1]);
  return { minX: Math.min(...xs), maxX: Math.max(...xs), minY: Math.min(...ys), maxY: Math.max(...ys) };
}

/** Centre that minimises the farthest vertex, so the mark sits optically centred in a circle. */
function enclosingCircle(shapes) {
  const pts = shapes.flatMap((s) => polygons(s).flat());
  const b = bounds(shapes);
  let best = { r: Infinity };
  for (let cx = b.minX; cx <= b.maxX; cx += 0.5) {
    for (let cy = b.minY; cy <= b.maxY; cy += 0.5) {
      const r = Math.max(...pts.map(([x, y]) => Math.hypot(x - cx, y - cy)));
      if (r < best.r) best = { cx, cy, r };
    }
  }
  return best;
}

const n = (v) => Number(v.toFixed(2));

function pathData(shape, tx) {
  return polygons(shape)
    .map((poly) => poly.map(tx).map(([x, y], i) => `${i ? 'L' : 'M'}${n(x)},${n(y)}`).join(' ') + ' Z')
    .join(' ');
}

function svgPaths(shapes, tx, fill) {
  return shapes
    .map((s) => `<path d="${pathData(s, tx)}" fill="${fill}" fill-rule="evenodd"${s.opacity < 1 ? ` fill-opacity="${s.opacity}"` : ''}/>`)
    .join('\n  ');
}

/** Square tile icon: the mark fitted inside a circle of `radius` around the centre. */
function markSvg({ size, radius, background, foreground, corner }) {
  const shapes = markShapes();
  const c = enclosingCircle(shapes);
  const k = radius / c.r;
  const tx = ([x, y]) => [size / 2 + (x - c.cx) * k, size / 2 + (y - c.cy) * k];
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${size} ${size}" width="${size}" height="${size}">
  <rect width="${size}" height="${size}" rx="${corner}" fill="${background}"/>
  ${svgPaths(shapes, tx, foreground)}
</svg>
`;
}

function wordmarkSvg({ foreground, background, padding = 40 }) {
  const shapes = wordmarkShapes();
  const b = bounds(shapes);
  const w = b.maxX - b.minX + padding * 2;
  const h = b.maxY - b.minY + padding * 2;
  const tx = ([x, y]) => [x - b.minX + padding, y - b.minY + padding];
  const bg = background ? `<rect width="${n(w)}" height="${n(h)}" rx="28" fill="${background}"/>\n  ` : '';
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${n(w)} ${n(h)}" width="${n(w * 2)}" height="${n(h * 2)}">
  ${bg}${svgPaths(shapes, tx, foreground)}
</svg>
`;
}

/** Android adaptive-icon layer: 108dp canvas, artwork kept inside the 66dp safe circle. */
function vectorDrawable(color) {
  const shapes = markShapes();
  const c = enclosingCircle(shapes);
  const k = 30 / c.r; // 30dp radius leaves a margin inside the 33dp safe zone
  const tx = ([x, y]) => [54 + (x - c.cx) * k, 54 + (y - c.cy) * k];
  const paths = shapes
    .map((s) => {
      const alpha = s.opacity < 1 ? `\n        android:fillAlpha="${s.opacity}"` : '';
      return `    <path
        android:fillColor="${color}"${alpha}
        android:fillType="evenOdd"
        android:pathData="${pathData(s, tx)}" />`;
    })
    .join('\n');
  return `<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by tools/brand/generate.mjs — edit the script, not this file. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
${paths}
</vector>
`;
}

const ADAPTIVE_ICON = `<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by tools/brand/generate.mjs — edit the script, not this file. -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
`;

const ICON_COLORS = `<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by tools/brand/generate.mjs — edit the script, not this file. -->
<resources>
    <color name="ic_launcher_background">${VOLT}</color>
</resources>
`;

function write(path, content) {
  const full = resolve(ROOT, path);
  mkdirSync(dirname(full), { recursive: true });
  writeFileSync(full, content);
  console.log('wrote', path);
}

write('docs/brand/logo-mark.svg', markSvg({ size: 512, radius: 176, background: INK, foreground: VOLT, corner: 116 }));
write('docs/brand/logo-mark-volt.svg', markSvg({ size: 512, radius: 176, background: VOLT, foreground: INK, corner: 116 }));
write('docs/brand/logo-wordmark.svg', wordmarkSvg({ foreground: VOLT, background: INK }));
write('docs/brand/logo-wordmark-light.svg', wordmarkSvg({ foreground: INK, background: WHITE }));

for (const app of ['wear', 'mobile']) {
  const res = `${app}/src/main/res`;
  write(`${res}/drawable/ic_launcher_foreground.xml`, vectorDrawable(INK));
  write(`${res}/drawable/ic_launcher_monochrome.xml`, vectorDrawable('#FFFFFF'));
  write(`${res}/mipmap-anydpi/ic_launcher.xml`, ADAPTIVE_ICON);
  write(`${res}/values/ic_launcher_background.xml`, ICON_COLORS);
}
