# Tech stack וגרסאות

נכון ל-10.10.2026. כל הגרסאות נבדקו מול Google Maven ו-Maven Central באותו יום.

## מה בנוי ונבדק היום

השלד נבנה על **הכלים שכבר מותקנים אצלך**, בלי להתקין כלום:

| רכיב | גרסה |
|---|---|
| Gradle | 8.13 |
| Android Gradle Plugin | 8.13.2 |
| Kotlin | 2.4.21 |
| JDK | 21 (ה-JBR של Android Studio) |
| compileSdk / targetSdk | 36 |
| minSdk | 34 שעון · 31 טלפון |
| Wear Compose (Material 3) | 1.6.2 |
| Compose BOM (טלפון) | 2026.06.01 |
| core-ktx / lifecycle / activity-compose | 1.18.0 / 2.10.0 / 1.13.0 |
| Health Services | 1.1.0 |
| Play Services Wearable | 20.0.1 |
| Coroutines | 1.11.0 |

`:wear:assembleDebug` ו-`:mobile:assembleDebug` עוברים, 9 בדיקות יחידה עוברות, ואפליקציית הטלפון הורצה על האמולטור.

## הממצא: ה-Android Studio שלך חוסם את דור הספריות הנוכחי ❓

ניסיתי קודם את הגרסאות העדכניות ביותר, וה-build נכשל. הסיבה חד-משמעית:

| ספרייה | אחרונה | מה היא דורשת |
|---|---|---|
| Compose (BOM 2026.09.00) | 1.12.1 | compileSdk 37 + AGP 9.1 ומעלה |
| core-ktx | 1.19.1 | compileSdk 37 + AGP 9.1 ומעלה |
| lifecycle | 2.11.0 | compileSdk 37 + AGP 9.1 ומעלה |
| Wear Compose | 1.7.1 | compileSdk 37 + AGP 9.1 ומעלה |

ו-Android Studio 2025.2.2 (Otter 2 Feature Drop) שמותקן אצלך תומך ב-AGP **עד 8.13 בלבד**. בנוסף Kotlin 2.4.21 כבר מזהיר ש-Gradle 8.13 מיושן ושמ-Kotlin 2.5 תידרש לפחות 8.14.4.

כלומר יש שתי דרכים:

### אופציה א' — להישאר על מה שמותקן (המצב הנוכחי)
- ✅ עובד היום, אפס התקנות, נפתח ב-Android Studio שלך.
- ❌ קפואים על ספריות מאביב 2026. Wear Compose 1.6.2 ולא 1.7.1.
- ❌ פרויקט חדש שמתחיל עם חוב: המעבר ל-AGP 9 יגיע בכל מקרה, ויהיה יקר יותר ככל שיהיה יותר קוד.

### אופציה ב' — לשדרג את Android Studio (המלצה)
- Android Studio **Quail 4 (2026.1.4)** או **Rabbit 1 (2026.2.1)** ← AGP 9.4.1, ‏Gradle 9.8.1, ‏compileSdk 37.
- ✅ כל הספריות בגרסה האחרונה; מתאים ל"חדיש ככל האפשר".
- ✅ ההגירה זולה **עכשיו**: ארבעה מודולים קטנים. AGP 9 מביא Kotlin מובנה, אז קובצי ה-build אפילו מתקצרים.
- ✅ לא שובר את Workout-Tracker: גרסאות Studio החדשות תומכות ב-AGP 7.1 עד 9.4, והוא על 8.13.2.
- ❌ הורדה של ~1.5GB ועדכון של הסביבה שלך, ולכן **לא עשיתי את זה בלי לשאול**.

מה שנדרש ממך באופציה ב': Help ← Check for Updates ב-Android Studio, ואז SDK Manager ← Android 17 (API 37). את השאר (wrapper, קטלוג גרסאות, הגירת AGP 9) אני עושה.

## ספריות מתוכננות (עוד לא ב-build)

נכנסות לקטלוג רק כשמשתמשים בהן בפועל, כדי שהקטלוג ישקף מה שנבדק.

| תפקיד | ספרייה | גרסה אחרונה | מתי |
|---|---|---|---|
| DI | Hilt + KSP | 2.60.1 / 2.3.12 | M2 |
| מסד נתונים | Room | 2.8.5 (יש גם Room 3.0.3 בקבוצה חדשה `androidx.room3` — ייבדק ב-M2) | M2 |
| הגדרות | DataStore | 1.2.1 | M2 |
| עבודות רקע | WorkManager | 2.12.0 | M3 |
| ניווט | Navigation 3 + ‏`wear.compose:compose-navigation3` | 1.2.0 / לפי Wear Compose | M2 |
| סריאליזציה | kotlinx-serialization-json | 1.11.0 | M3 |
| Ongoing Activity | `androidx.wear:wear-ongoing` | 1.1.0 | M2 |
| מיקום | Play Services Location | 21.4.0 | M1 |
| גרפים (טלפון) | Vico | 3.3.1 | M3 |
| Tile | Tiles / ProtoLayout | 1.6.2 / 1.4.2 | M5 |
| בדיקות | Turbine, ‏coroutines-test, ‏Robolectric | 1.2.1 / 1.11.0 / — | M1 |

## בחירות ונימוקים קצרים

- **Hilt** ולא Koin/ידני — זה מה שהדוגמאות הרשמיות של Health Services משתמשות בו, ויש לו אינטגרציה מוכנה עם ViewModel ו-WorkManager.
- **Room** — שאילתות על דגימות לפי ריצה וזמן, טרנזקציות ל-upsert, ו-WAL שמגן מפני קריסה באמצע כתיבה.
- **JSON+gzip** להעברה ולא Protobuf — קריא בדיבוג, API יציב, והגודל זניח בנפחים שלנו.
- **Vico** לגרפים — Compose-native. אם הוא יגביל את העיצוב, גרף מהירות אחד ב-Canvas הוא כמה עשרות שורות.
- **בלי Horologist** כברירת מחדל — רוב מה שהוא נתן כבר בתוך Wear Compose M3 הרשמי.

## בנייה משורת הפקודה

`java` לא ב-PATH אצלך, אז צריך להצביע על ה-JBR:

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :wear:assembleDebug :mobile:assembleDebug
./gradlew test
```

## מקורות

- [Android Developers — AGP release notes ותאימות ל-Android Studio](https://developer.android.com/build/releases/about-agp)
- [Android Developers — Wear Compose releases](https://developer.android.com/jetpack/androidx/releases/wear-compose)
- [JetBrains — עדכון פרויקטים ל-AGP 9](https://blog.jetbrains.com/kotlin/2026/01/update-your-projects-for-agp9/)
