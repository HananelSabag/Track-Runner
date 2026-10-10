# Tech stack וגרסאות

נכון ל-10.10.2026. הפרויקט שודרג לגרסאות האחרונות ונבנה איתן בהצלחה.

## מה בנוי ונבדק

| רכיב | גרסה |
|---|---|
| Gradle | 9.8.1 |
| Android Gradle Plugin | 9.4.1 (עם Kotlin מובנה) |
| Kotlin | 2.4.21 |
| JDK | 25 (ה-JBR של Android Studio 2026.2.1) |
| compileSdk | 37 (Android 17) |
| targetSdk | 36 — הגרסה הגבוהה ביותר שהאפליקציה באמת הורצה עליה |
| minSdk | 34 שעון · 31 טלפון |
| Wear Compose (Material 3) | 1.7.1 |
| Compose BOM (טלפון) | 2026.09.00 |
| core-ktx / lifecycle / activity-compose | 1.19.1 / 2.11.0 / 1.13.0 |
| Health Services | 1.1.0 |
| Play Services Wearable | 20.0.1 |
| Coroutines | 1.11.0 |

- שתי האפליקציות מתקמפלות ל-APK, ו-42 בדיקות יחידה עוברות.
- אפליקציית הטלפון הורצה על אמולטור Android 16.
- **אפליקציית השעון הורצה על אמולטור Wear OS 5** — בפעם הראשונה. [צילום](../design/watch-device-check.png).

## מה הותקן אצלך בשדרוג

הכול מהמאגר הרשמי של גוגל, לתוך תיקיית ה-SDK הקיימת:

| מה | גודל | למה |
|---|---|---|
| Android SDK Command-line Tools 23.0 | ~155MB | בלעדיו אי אפשר להתקין רכיבי SDK משורת פקודה. ההורדה אומתה מול ה-checksum הרשמי. כולל את ה-Android CLI החדש |
| SDK Platform 37 + Build-Tools 37.0.0 | ~290MB | נדרש לספריות העדכניות |
| Wear OS 5 system image (API 34, x86_64) | **4.2GB על הדיסק** | אמולטור שעון |
| AVD בשם `Wear_OS_5_Large_Round` | 2.9GB | השעון הווירטואלי |
| Gradle 9.8.1 | ~150MB | הורד אוטומטית ע"י ה-wrapper |

סך הכול כ-**7.7GB**, רובם האמולטור (יותר ממה שהערכתי קודם). להסרה: מחיקת ה-AVD ב-Device Manager ושל ה-system image ב-SDK Manager.

## Android Studio עודכן — ומה זה שינה

עדכנת ל-**Rabbit 1 (2026.2.1)**, שתומך ב-AGP 9.4.1 של הפרויקט. שני דברים השתנו בעקבות זה:

- **התיקייה זזה.** העדכון הותקן ל-`C:\Program Files\Android\Android Studio1`. התיקייה הישנה `Android Studio` נשארה עם שארית JDK שבורה. כל פקודות הבנייה במסמכים עודכנו לנתיב החדש.
- **ה-JDK המובנה הוא עכשיו 25** (היה 21). הפרויקט הזה נבנה איתו בלי בעיה.

**השפעה על Workout-Tracker — טעיתי כאן.** כתבתי שהעדכון לא ישבור אותו, כי Studio החדש תומך ב-AGP שלו. זה נכון, אבל לא לקחתי בחשבון את ה-JDK: הוא על Gradle 8.13 + Kotlin 2.0.21, שלא רצים על JDK 25, ואין JDK אחר במחשב. בדקתי — הבנייה שלו נכשלת עכשיו. התיקון קטן (JDK 21 דרך הגדרות Gradle ב-Studio, או שדרוג ה-wrapper שלו), והוא לא נגע בקוד שלו.

## מה השתנה במעבר ל-AGP 9

- **Kotlin מובנה:** התוסף `org.jetbrains.kotlin.android` הוסר; AGP מקמפל Kotlin בעצמו. גרסת Kotlin נקבעת דרך התוספים `kotlin-jvm` ו-`kotlin-compose` שנשארו.
- `jvmTarget` נגזר אוטומטית מ-`compileOptions`, אז הבלוק הנפרד הוסר ממודולי האנדרואיד.
- `android.useAndroidX` ו-`android.nonTransitiveRClass` הוסרו מ-`gradle.properties` — הם ברירת המחדל עכשיו.
- ה-build מדפיס אזהרת deprecation אחת (`Configuration.setVisible`, תוסר ב-Gradle 11). היא מגיעה מהתוספים, לא מקובצי ה-build שלנו, ולא משפיעה כרגע.

## ספריות מתוכננות (עוד לא ב-build)

נכנסות לקטלוג רק כשמשתמשים בהן בפועל, כדי שהקטלוג ישקף מה שנבדק.

| תפקיד | ספרייה | גרסה אחרונה | מתי |
|---|---|---|---|
| DI | Hilt + KSP | 2.60.1 / 2.3.12 | M2 |
| מסד נתונים | Room | 2.8.5 (יש גם Room 3.0.3 בקבוצה חדשה `androidx.room3` — ייבדק ב-M2) | M2 |
| הגדרות | DataStore | 1.2.1 | M2 |
| עבודות רקע | WorkManager | 2.12.0 | M3 |
| ניווט | Navigation 3 + ‏`wear.compose:compose-navigation3` | 1.2.0 / 1.7.1 | M2 |
| סריאליזציה | kotlinx-serialization-json | 1.11.0 | M3 |
| Ongoing Activity | `androidx.wear:wear-ongoing` | 1.1.0 | M2 |
| מיקום | Play Services Location | 21.4.0 | M1 |
| גרפים (טלפון) | Vico | 3.3.1 | M3 |
| מפה (אם יוחלט) | MapLibre Compose | 0.20.0 (לפני 1.0, עלול להשתנות) | M5 |
| Tile | Tiles / ProtoLayout | 1.6.2 / 1.4.2 | M5 |
| בדיקות | Turbine, ‏coroutines-test, ‏Robolectric | 1.2.1 / 1.11.0 / — | M1 |

## בחירות ונימוקים קצרים

- **Hilt** ולא Koin/ידני — זה מה שהדוגמאות הרשמיות של Health Services משתמשות בו, ויש לו אינטגרציה מוכנה עם ViewModel ו-WorkManager.
- **Room** — שאילתות על דגימות לפי אימון וזמן, טרנזקציות ל-upsert, ו-WAL שמגן מפני קריסה באמצע כתיבה.
- **JSON+gzip** להעברה ולא Protobuf — קריא בדיבוג, API יציב, והגודל זניח בנפחים שלנו.
- **Vico** לגרפים — Compose-native. אם הוא יגביל את העיצוב, גרף מהירות אחד ב-Canvas הוא כמה עשרות שורות.
- **בלי Horologist** כברירת מחדל — רוב מה שהוא נתן כבר בתוך Wear Compose M3 הרשמי.

## בנייה והרצה משורת הפקודה

`java` לא ב-PATH אצלך, אז צריך להצביע על ה-JBR:

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio1/jbr"
./gradlew :wear:assembleDebug :mobile:assembleDebug
./gradlew test
```

אמולטור שעון:

```bash
"$LOCALAPPDATA/Android/Sdk/emulator/emulator.exe" -avd Wear_OS_5_Large_Round
```

## מקורות

- [Android Developers — AGP release notes ותאימות ל-Android Studio](https://developer.android.com/build/releases/about-agp)
- [Android Developers — Wear Compose releases](https://developer.android.com/jetpack/androidx/releases/wear-compose)
- [JetBrains — עדכון פרויקטים ל-AGP 9](https://blog.jetbrains.com/kotlin/2026/01/update-your-projects-for-agp9/)
- [android/skills — agp-9-upgrade](https://github.com/android/skills)
