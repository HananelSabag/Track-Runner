# 01 · הפלטפורמה והמכשירים

מה יש לנו ביד: השעון, הטלפון, גרסאות Wear OS, והכללים של הפלטפורמה שמכתיבים איך האפליקציה חייבת להיבנות.

## OnePlus Watch 2

| | |
|---|---|
| שבבים | Snapdragon W5 Gen 1 (מריץ Wear OS) + BES2700 (מריץ RTOS) — "Dual-Engine" |
| זיכרון | 2GB RAM, 32GB אחסון |
| מסך | AMOLED עגול 1.43", ‏466×466 |
| סוללה | 500mAh |
| GNSS | דו-תדרי L1+L5, רב-מערכתי (GPS, GLONASS, Galileo, BeiDou, QZSS) |
| חיישנים | מד תאוצה, ג'ירוסקופ, דופק אופטי, SpO2, מגנטומטר, ברומטר, אור |
| מערכת בהשקה | Wear OS 4 (API 33) |

**מצב העדכונים — צריך לוודא אצלך.** Wear OS 5 (API 34) התחיל להתגלגל לשעון רק בסוף דצמבר 2025 ובחלק מהאזורים, עם דיווחים על ירידה בסוללה אחרי העדכון. OnePlus הבטיחה Wear OS 6 במהלך 2026 ולפי הדיווחים זה העדכון הגדול האחרון של הדגם. **לא מצאתי אישור שהוא כבר שוחרר.** לכן אני לא יודע אם "הגרסה הכי גבוהה" אצלך היא API 34 או 36 — זה משפיע על הרשאות (ראה למטה) ונבדק בהרצה הראשונה של מסך "בדיקת מכשיר".

**מה נובע מהארכיטקטורה הכפולה:**
- מעקב הכושר המובנה (OHealth) רץ על ה-RTOS החסכוני. אפליקציית צד שלישי כמו שלנו רצה על ה-Snapdragon, ולכן הקלטה ארוכה שלנו תעלה יותר סוללה מהאימון המובנה. צריך למדוד.
- במצב Power Saver השעון רץ רק על RTOS ואפליקציות Wear OS לא זמינות בכלל.
- איכות המימוש של Health Services על השעון הזה לאפליקציות צד שלישי **לא מתועדת בשום מקום שמצאתי** — לא לטוב ולא לרע. זו הסיבה המרכזית לשלב M1 (מדידה אמיתית לפני שמחליטים על מקור המהירות).

**קלט פיזי:** לפי ביקורות ההשקה הכתר של Watch 2 מסתובב אבל לא משמש כקלט גלילה (תוקן ב-Watch 3). ה-UI לא יסתמך על גלילת כתר; כן נתמוך בה כי היא עובדת בשעונים אחרים.

**ביקורות GPS:** בכביש ובשטח פתוח — טוב עד מצוין. תחת עצים ובמסלול אתלטיקה — חלש יותר מ-Garmin (סטייה מהמסלול, התראות הקפה מוקדמות). כלומר הדיוק תלוי סביבה, והאפליקציה צריכה להציג את איכות האות ולא להעמיד פנים.

## OnePlus 13

| | |
|---|---|
| שבב | Snapdragon 8 Elite |
| GNSS | GPS ‏L1+L5 · Galileo ‏E1+E5a · BeiDou ‏B1I+B1C+B2a · QZSS ‏L1+L5 · GLONASS ‏G1 · NavIC ‏L5 |
| מערכת | Android 15 בהשקה, עודכן ל-Android 16 |

הטלפון הוא מקלט GNSS חזק יותר מהשעון: אנטנה גדולה יותר, יותר מערכות לוויינים בתדר כפול, ובלי תנודת היד. החיסרון שלו: בכיס או בתיק הגוף מסתיר לו חלק מהשמיים.

**סיכון OxygenOS:** ניהול הסוללה האגרסיבי של OnePlus ידוע בסגירת אפליקציות רקע. לאפליקציית הטלפון זה אומר שהקלטה חייבת לרוץ ב-foreground service, ושב-onboarding נבקש לבטל אופטימיזציית סוללה. הסנכרון עצמו מופעל ע"י Google Play services ולכן אמור לשרוד, אבל זה ייבדק במכשיר.

## גרסאות Wear OS

| Wear OS | API | רלוונטיות |
|---|---|---|
| 4 | 33 | גרסת ההשקה של Watch 2 |
| 5 | 34 | הרצפה שלנו (`minSdk`). נוסף סוג foreground service ‏`health` |
| 5.1 | 35 | — |
| 6 | 36 | הרשאות בריאות פרטניות: `READ_HEART_RATE` במקום `BODY_SENSORS` |
| 7 | 37 | הוכרז במאי 2026. Live Updates מחליף את Ongoing Activity, ‏Widgets מחליפים Tiles |

**החלטה:** `minSdk = 34` לשעון. זה מה שהשעון שלך מריץ לכל הפחות, וזה מה שאפשר לבדוק בפועל. תמיכה ב-API 33 היא שינוי קטן אם יהיה צורך, אבל אין מכשיר לבדוק עליו.

## כללי פלטפורמה שמעצבים את האפליקציה

1. **הקלטה = foreground service.** סוג `health|location` בשעון, `location` בטלפון. בלי זה המערכת עוצרת את האימון (`AUTO_END_PERMISSION_LOST`).
2. **Ongoing Activity חובה** לכל פעילות מעל דקה (הנחיית איכות WO-V4): התראה קבועה + אייקון על לוח השעון שמחזיר לאפליקציה. ב-Wear OS 7 זה הופך ל-Live Update.
3. **מצב Ambient:** כשהמסך כבוי/מעומעם מותר לעדכן את התצוגה לכל היותר פעם בדקה, והמעבד הראשי ישן. הנתונים ממשיכים להיאסף ב-batch ומגיעים כשמרימים את היד. לכן הסטופר שעל המסך לא יכול להיות תלוי בקצב העדכונים.
4. **אימון אחד בכל רגע בכל המערכת.** Health Services מריץ רק exercise אחד; אם נתחיל אחד בזמן ש-OHealth מקליט — נעצור אותו. חובה לבדוק ולהזהיר.
5. **מיקום בשעון לא תמיד מגיע מהשעון.** המערכת בוחרת "את הדרך החסכונית ביותר" ויכולה להביא מיקום מהטלפון כשהוא מחובר. Health Services מדווח על זה במפורש: `ACQUIRED_TETHERED` (דרך הטלפון) מול `ACQUIRED_UNTETHERED` (שבב השעון). כדי לדעת מי מדד מה, המנוע שלנו חייב לבקש מקור מפורש ולתייג כל דגימה.
6. **הרשאות:** `ACCESS_FINE_LOCATION`, ‏`ACTIVITY_RECOGNITION`, ‏`POST_NOTIFICATIONS`. דופק — רק אם נחליט להקליט אותו, ואז `BODY_SENSORS` עד API 35 ו-`READ_HEART_RATE` מ-API 36.
7. **שתי האפליקציות חייבות אותו `applicationId` ואותה חתימה** — אחרת ה-Data Layer לא מחבר ביניהן.

## סביבת הפיתוח המקומית

> הטבלה מתארת את המצב **לפני** השדרוג. מה הותקן מאז ומה נשאר — ב-[tech-stack.md](../architecture/tech-stack.md).

| רכיב | מצב |
|---|---|
| Android Studio | 2025.2.2 (Otter 2 Feature Drop) — תומך ב-AGP עד 8.13 |
| JDK | JBR 21 בתוך Android Studio (אין `java` ב-PATH) |
| SDK Platforms | 35, 36 |
| Build-Tools | 35.0.0, 36.1.0 |
| Emulator | 36.3.10, ‏AVD אחד: `Pixel_9_Pro_Fold` |
| adb | 36.0.2 |
| Gradle (cache) | 8.13 + AGP 8.13.2 |
| git / gh / Node | 2.52 / מחובר כ-HananelSabag / 24.13 |

**מה חסר:**
- **אין system image של Wear OS** → אין כרגע אמולטור שעון.
- **אין `cmdline-tools`** → אי אפשר להתקין רכיבי SDK משורת פקודה; רק דרך SDK Manager של Android Studio.
- **Android Studio ישן בשנה** → חוסם את דור הספריות הנוכחי. פירוט ב-[tech-stack.md](../architecture/tech-stack.md).

## מקורות

- [Android Authority — לוח הזמנים של Wear OS 6 ל-OnePlus Watch 2/3](https://www.androidauthority.com/oneplus-watch-3-watch-2-wear-os-6-timeline-3606220/)
- [Android Central — בעיות סוללה אחרי Wear OS 5](https://www.androidcentral.com/wearables/oneplus-watch/oneplus-watch-2-wear-os-5-update-causing-serious-battery-problems)
- [Android Police — אורך התמיכה בעדכונים](https://www.androidpolice.com/your-oneplus-watch-wont-get-wear-os-updates-as-long-as-you-think/)
- [GSMArena — hands-on, ארכיטקטורת Dual-Engine](https://www.gsmarena.com/oneplus_watch_2_hands_on_review-news-61763.php)
- [TechRadar — GPS מול Garmin](https://www.techradar.com/health-fitness/smartwatches/i-tested-the-oneplus-watch-2s-gps-tracking-against-garmins-best-watch-heres-how-it-stacked-up) · [Android Central — Watch 2R במסלול](https://www.androidcentral.com/wearables/oneplus-watch-2r-fitness-test) · [The Tested Hub](https://thetestedhub.com/reviews/oneplus-watch-2/)
- [OnePlus 13 — מפרט רשמי](https://www.oneplus.in/13/specs) · [GSMArena](https://m.gsmarena.com/oneplus_13-13477.php)
- [Android Developers — Record an exercise with ExerciseClient](https://developer.android.com/health-and-fitness/health-services/active-data)
- [Android Developers — Detect location on Wear OS](https://developer.android.com/training/wearables/apps/location-detection)
- [Android Developers — LocationAvailability](https://developer.android.com/reference/androidx/health/services/client/data/LocationAvailability)
- [Android Developers — Wear OS 6 behavior changes](https://developer.android.com/training/wearables/versions/6/changes)
- [Android Developers Blog — What's new in Wear OS 7](https://android-developers.googleblog.com/2026/05/whats-new-wear-os-7.html)
- [Android Developers — Ongoing activities](https://developer.android.com/training/wearables/notifications/ongoing-activity)
