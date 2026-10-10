# 05 · ערכות, סקילים ודוגמאות מוכנות

שאלת אם יש כבר דברים מוכנים לפיתוח אפליקציות שעון. יש, ושניים מהם שווים התקנה.

## סקילים לסוכני AI

### ‏`android/skills` — הרשמי של גוגל ⭐
ריפו שגוגל פרסמה באפריל 2026: הוראות מותאמות-סוכן שנגזרות מ-developer.android.com. רישיון Apache 2.0.

| סקיל | למה הוא רלוונטי לנו |
|---|---|
| **`wear/wear-compose-m3`** | בדיוק מה שצריך: Wear Compose Material 3, כולל ~50 קבצי דוגמה קנוניים וכללים (`TransformingLazyColumn`, ‏`ScreenScaffold`, ‏`EdgeButton`, מצב ambient, ניווט). עודכן ב-24.9.2026 |
| `jetpack-compose` | אפליקציית הטלפון |
| `navigation` | Navigation 3 |
| `testing/testing-setup` | תשתית בדיקות |
| `build-system/agp/agp-9-upgrade` | אם נשדרג toolchain (ראה [tech-stack](../architecture/tech-stack.md)) |
| `system/edge-to-edge`, ‏`performance/r8-analyzer`, ‏`play` | בהמשך |

התקנה: דרך ה-Android CLI החדש (`android skills add wear-compose-m3 --project=.`) או כ-plugin של Claude Code (בריפו יש `.claude-plugin`). ה-Android CLI **כבר מותקן אצלך** — הוא הגיע עם כלי ה-SDK בשדרוג — אז זו פקודה אחת.

> קראתי את עיקרי הסקיל של Wear ויישמתי את הכללים הרלוונטיים בשלד. התקנה קבועה תבטיח שכל סשן עתידי יקבל את כולו, כולל קבצי הדוגמה, אוטומטית.

### ‏`yschimke/compose-ai-tools` — רינדור Preview בלי אמולטור ⭐
מרנדר כל `@Preview` של Compose ל-PNG משורת הפקודה (דרך Robolectric), כולל Wear. המשמעות: **אני יכול לראות צילום של כל מסך שעון תוך כדי פיתוח**, בלי אמולטור ובלי שתצטרך לשלוח צילומי מסך. דורש Java 17+, ‏Gradle 8.13+, ‏AGP 8.13+, ‏Kotlin 2.0.21+ — תואם למה שיש.

זה כלי קהילתי, לא רשמי. ההתקנה שלו מוסיפה סקילים וכלי CLI.

### אחרים (לא נדרשים)
`Drjacky/claude-android-ninja`, ‏`piyushverma0/android-agent-skills`, והסקילים של Chris Banes — אנדרואיד כללי, חופפים למה שהרשמי כבר נותן. בקטלוג הסקילים שלך ב-claude.ai אין כלום לאנדרואיד/Wear.

**המלצה:** להתקין את `wear-compose-m3` + `jetpack-compose` + `testing-setup` מהרשמי, ואת `compose-preview`. שניהם מוסיפים הוראות ש-Claude יפעל לפיהן בסשנים הבאים, אז **אני מחכה לאישור שלך לפני התקנה.**

## קוד לדוגמה של גוגל

| מקור | מה לוקחים ממנו |
|---|---|
| [`android/health-samples`](https://github.com/android/health-samples) ← `ExerciseSampleCompose` | התבנית הקרובה ביותר למה שאנחנו בונים: `ExerciseClient` + foreground service + Ongoing Activity + Compose + Hilt |
| [`android/wear-os-samples`](https://github.com/android/wear-os-samples) | דוגמאות Data Layer, ‏Tiles, ‏Complications |
| [Horologist](https://github.com/google/horologist) (0.8.4-alpha) | עוזרי Data Layer ורכיבי בריאות. רוב רכיבי ה-UI שלו כבר נבלעו ב-Wear Compose M3 הרשמי, והסקיל הרשמי מנחה לעבור מהם לרכיבים הרשמיים. ניקח ממנו רק מה שבאמת חסר |
| [מדריך "Integrate a Wear OS module"](https://developer.android.com/health-and-fitness/fitness/basic-app/integrate-wear-os) | הדפוס הרשמי לשעון → טלפון דרך `DataClient` + `WearableListenerService` |

## בדיקות בלי לצאת לרוץ

- **אמולטור Wear OS:** מ-Wear OS 4 יש נתונים סינתטיים מובנים ל-Health Services — "ריצה" מדומה במהירות ממוצעת 2.3 מ'/ש עם מיקום, ואפשר לשנות מהירות ודופק. ב-Android Studio יש פאנל "Wear Health Services", ו-Extended Controls מאפשר להזרים מסלול GPS.
- **הרצה חוזרת של הקלטות:** ברגע שיש לוגר (M1), כל ריצה אמיתית הופכת לקובץ בדיקה שמוזרם דרך המנוע. זה הכלי החשוב ביותר לדיוק, והוא לא דורש שום מכשיר.
- **שעון אמיתי:** Wireless debugging (אפשרויות מפתח ← Wireless debugging ← צימוד עם קוד). אחרי צימוד אחד אני יכול להתקין builds ולקרוא לוגים ישירות.

## מה היה צריך להתקין

> עודכן: האמולטור כבר מותקן והאפליקציה הורצה עליו. נשאר רק עדכון Android Studio עצמו — [tech-stack.md](../architecture/tech-stack.md).

| מה | למה | איך | גודל |
|---|---|---|---|
| System image של Wear OS | אמולטור שעון | SDK Manager ב-Android Studio ← SDK Platforms ← "Show Package Details" ← Wear OS | ~1.5GB |
| עדכון Android Studio | רק אם בוחרים באופציה ב' ב-[tech-stack](../architecture/tech-stack.md) | Help ← Check for Updates | ~1.5GB |

אף אחד מהם לא חוסם התחלה: השעון האמיתי שלך הוא מכשיר הבדיקה הטוב ביותר ממילא.

## מקורות

- [android/skills](https://github.com/android/skills)
- [yschimke/compose-ai-tools](https://github.com/yschimke/compose-ai-tools)
- [Android Developers — Simulate sensor data with Health Services](https://developer.android.com/health-and-fitness/health-services/simulated-data)
- [Android Developers — Test on the Wear OS emulator](https://developer.android.com/training/wearables/get-started/emulator)
