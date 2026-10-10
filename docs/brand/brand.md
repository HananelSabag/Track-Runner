# מותג — FAST

![logo preview](logo-preview.png)

## שם

- **המותג ושם האפליקציה: FAST.** תמיד באותיות גדולות.
- **הריפו והפרויקט: Track-Runner.** שם עבודה גנרי, לא מוצג למשתמש.
- תיאור משני כשצריך: "מד מהירות אישי" / "Speed tracker".

## פלטה (גרסה 2)

הגרסה הראשונה — צהוב-ליים וכתום על שחור — הוחלפה. הנוכחית לקוחה מאותה משפחה של Workout-Tracker: כחול, ציאן וסגול על נייבי עמוק.

### מותג
| שם | Hex | תפקיד |
|---|---|---|
| **Electric** | `#3B82F6` | הצבע הראשי: מילויים, כפתורים, הקשת החיה |
| **Sky** | `#60A5FA` | אותו כחול בהיר יותר, לטקסט ולאייקונים על רקע כהה |
| **Cyan** | `#22D3EE` | קצה "קריר" של הגרדיאנט |
| **Violet** / **Lilac** | `#8B5CF6` / `#A78BFA` | קצה "חם" של הגרדיאנט; דופק |

**גרדיאנט המותג:** Cyan → Electric → Violet, בכיוון התנועה. משמש ללוגו, לקשת המהירות ולמספרי שיא.

### משמעות
| שם | Hex | תפקיד |
|---|---|---|
| **Rose** | `#FB7185` | שיאים וספרינט. לא לקישוט |
| **Rose Deep** | `#F43F5E` | פעולה הרסנית: סיום, מחיקה |
| **Mint** | `#34D399` | תקין: GPS מוכן, סונכרן |

### ניטרלים
| שם | Hex | תפקיד |
|---|---|---|
| **Black** | `#000000` | רקע בשעון (OLED: לא צורך חשמל ומעלים את המסגרת) |
| **Navy** | `#0B1120` | רקע בטלפון |
| **Surface** | `#141E33` / `#1E293B` | כרטיסים ומשטחים |
| **Ice** | `#F1F5F9` | טקסט ראשי |
| **Mist** / **Slate** | `#94A3B8` / `#64748B` | טקסט משני; מנוחה |

### אזורי מהירות
סקאלה אחת מקריר לחם, בלי צהוב ובלי כתום — כך שאזור נקרא בלי מקרא:

| הליכה | ג'וג | ריצה | מהירות גבוהה | ספרינט |
|---|---|---|---|---|
| `#22D3EE` | `#3B82F6` | `#8B5CF6` | `#D946EF` | `#FB7185` |

מנוחה: Slate `#64748B`.

הממשק כהה בלבד, בשני המכשירים. הצבעים מוגדרים בקוד ב-`theme/FastTheme.kt` של כל אפליקציה וב-`tools/brand/generate.mjs`.

## לוגו

נוצר כולו בקוד: [`tools/brand/generate.mjs`](../../tools/brand/generate.mjs) מייצר מאותה גיאומטריה גם את קובצי ה-SVG שכאן וגם את אייקון האנדרואיד של שתי האפליקציות. שינוי בסקריפט + הרצה אחת מעדכן הכול.

**הרעיון:** אותיות בלוקיות נטויות קדימה, עם שלושה פסי מהירות מאחורי ה-F. הסימן המקוצר (F + פסים) הוא האייקון.

| קובץ | שימוש |
|---|---|
| `logo-mark.svg` | **אייקון האפליקציה:** F לבן על גרדיאנט Electric → Violet |
| `logo-mark-dark.svg` | הסימן בגרדיאנט על נייבי |
| `logo-wordmark.svg` | הלוגו המלא בגרדיאנט, על נייבי |
| `logo-wordmark-light.svg` | הלוגו המלא, על בהיר |

## טיפוגרפיה

- **מספרים:** גופן צר, כבד ונטוי, עם ספרות ברוחב קבוע כדי שהמספר לא יזוז כשהוא מתחלף. ב-mockups מופיע Bahnschrift כממלא-מקום. מועמדים חופשיים לשימוש: Barlow Condensed, ‏Saira Condensed.
- **טקסט:** גופן שתומך בעברית ובלטינית באותה איכות — Rubik או Heebo.
- בחירת הגופנים הסופית תיעשה כשנראה את המסך החי על השעון עצמו.

## קול

קצר, ישיר, מספרי. "27.3 קמ״ש · שיא חדש" ולא "כל הכבוד! שברת שיא אישי 🎉".

## פרומפט למודל תמונה

אם תרצה לנסות כיוונים נוספים במודל יצירת תמונות:

```
Minimal vector logo for a sports speed-tracking app called "FAST".
A bold, heavy, forward-slanted (about 12° italic) geometric letter "F" built from
flat rectangular strokes with sharp corners, with three short horizontal speed
lines trailing behind it on the left, fading in opacity from top to bottom.
The "F" is white, on a smooth diagonal gradient background from electric blue
#3B82F6 (top-left) to violet #8B5CF6 (bottom-right). Flat design, no 3D, no
shadows, no texture.
Centered in a square with generous padding so it survives a circular mask
(smartwatch launcher icon). Must stay legible at 48x48 pixels.
Also provide a horizontal lockup with the full word "FAST" in the same blocky
italic lettering, filled with a horizontal gradient cyan #22D3EE → blue #3B82F6
→ violet #8B5CF6 on a deep navy #0B1120 background.
Vector style, crisp edges, modern sportswear-brand feel.
```

וריאציות ששווה לבקש: פסי המהירות חותכים את ה-F במקום לבוא מאחוריו; סמל "הרצה קדימה" (▶▶) משולב באות; גרסה חד-צבעית.
