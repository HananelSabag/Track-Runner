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

// Brand palette — keep in sync with docs/brand/brand.md and the two FastTheme.kt files.
const CYAN = '#22D3EE';
const ELECTRIC = '#3B82F6';
const VIOLET = '#8B5CF6';
const NAVY = '#0B1120';
const ICE = '#F1F5F9';
const WHITE = '#FFFFFF';

// The brand gradient runs cool to warm along the direction of travel.
const BRAND_GRADIENT = [CYAN, ELECTRIC, VIOLET];
// The launcher tile uses the two deeper stops so the white mark keeps its contrast.
const ICON_GRADIENT = [ELECTRIC, VIOLET];

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

function gradientDef(id, stops, x1, y1, x2, y2) {
  const items = stops.map((c, i) => `<stop offset="${n(i / (stops.length - 1))}" stop-color="${c}"/>`).join('');
  return `<linearGradient id="${id}" gradientUnits="userSpaceOnUse" x1="${n(x1)}" y1="${n(y1)}" x2="${n(x2)}" y2="${n(y2)}">${items}</linearGradient>`;
}

/**
 * Square tile icon: the mark fitted inside a circle of `radius` around the centre.
 * `background` and `foreground` are each either a colour or a list of gradient stops.
 */
function markSvg({ size, radius, background, foreground, corner }) {
  const shapes = markShapes();
  const c = enclosingCircle(shapes);
  const k = radius / c.r;
  const tx = ([x, y]) => [size / 2 + (x - c.cx) * k, size / 2 + (y - c.cy) * k];
  const defs = [];
  let backgroundFill = background;
  let foregroundFill = foreground;
  if (Array.isArray(background)) {
    defs.push(gradientDef('bg', background, 0, 0, size, size));
    backgroundFill = 'url(#bg)';
  }
  if (Array.isArray(foreground)) {
    const b = bounds(shapes);
    defs.push(gradientDef('fg', foreground, tx([b.minX, 0])[0], 0, tx([b.maxX, 0])[0], 0));
    foregroundFill = 'url(#fg)';
  }
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${size} ${size}" width="${size}" height="${size}">
  <defs>${defs.join('')}</defs>
  <rect width="${size}" height="${size}" rx="${corner}" fill="${backgroundFill}"/>
  ${svgPaths(shapes, tx, foregroundFill)}
</svg>
`;
}

function wordmarkSvg({ foreground, background, padding = 40 }) {
  const shapes = wordmarkShapes();
  const b = bounds(shapes);
  const w = b.maxX - b.minX + padding * 2;
  const h = b.maxY - b.minY + padding * 2;
  const tx = ([x, y]) => [x - b.minX + padding, y - b.minY + padding];
  let defs = '';
  let fill = foreground;
  if (Array.isArray(foreground)) {
    defs = `<defs>${gradientDef('fg', foreground, padding, 0, w - padding, 0)}</defs>\n  `;
    fill = 'url(#fg)';
  }
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${n(w)} ${n(h)}" width="${n(w * 2)}" height="${n(h * 2)}">
  ${defs}<rect width="${n(w)}" height="${n(h)}" rx="28" fill="${background}"/>
  ${svgPaths(shapes, tx, fill)}
</svg>
`;
}

const GENERATED_NOTE = '<!-- Generated by tools/brand/generate.mjs — edit the script, not this file. -->';

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
${GENERATED_NOTE}
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
${paths}
</vector>
`;
}

/** Adaptive-icon background: a diagonal gradient across the full 108dp canvas. */
function gradientBackgroundDrawable(stops) {
  const items = stops
    .map((c, i) => `                <item android:offset="${n(i / (stops.length - 1))}" android:color="#FF${c.slice(1)}" />`)
    .join('\n');
  return `<?xml version="1.0" encoding="utf-8"?>
${GENERATED_NOTE}
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:endX="108"
                android:endY="108"
                android:startX="0"
                android:startY="0"
                android:type="linear">
${items}
            </gradient>
        </aapt:attr>
    </path>
</vector>
`;
}

const ADAPTIVE_ICON = `<?xml version="1.0" encoding="utf-8"?>
${GENERATED_NOTE}
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
`;

function write(path, content) {
  const full = resolve(ROOT, path);
  mkdirSync(dirname(full), { recursive: true });
  writeFileSync(full, content);
  console.log('wrote', path);
}

write('docs/brand/logo-mark.svg', markSvg({ size: 512, radius: 176, background: ICON_GRADIENT, foreground: WHITE, corner: 116 }));
write('docs/brand/logo-mark-dark.svg', markSvg({ size: 512, radius: 176, background: NAVY, foreground: BRAND_GRADIENT, corner: 116 }));
write('docs/brand/logo-wordmark.svg', wordmarkSvg({ foreground: BRAND_GRADIENT, background: NAVY }));
write('docs/brand/logo-wordmark-light.svg', wordmarkSvg({ foreground: NAVY, background: ICE }));

for (const app of ['wear', 'mobile']) {
  const res = `${app}/src/main/res`;
  write(`${res}/drawable/ic_launcher_background.xml`, gradientBackgroundDrawable(ICON_GRADIENT));
  write(`${res}/drawable/ic_launcher_foreground.xml`, vectorDrawable(WHITE));
  write(`${res}/drawable/ic_launcher_monochrome.xml`, vectorDrawable(WHITE));
  write(`${res}/mipmap-anydpi/ic_launcher.xml`, ADAPTIVE_ICON);
}
