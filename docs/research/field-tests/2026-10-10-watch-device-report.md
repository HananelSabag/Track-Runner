# דוח מכשיר — OnePlus Watch 2 האמיתי

**10.10.2026** · הפעם הראשונה שהאפליקציה רצה על השעון עצמו. הדוח נקרא מהלוג דרך Wireless debugging.

## הדוח כפי שהתקבל

```
== Device ==
  Model: OnePlus OPWWE231
  Android: 14 (API 34)
  Wear OS: 5
  Display: 466x466 px, round
== GNSS ==
+ On-board GNSS: yes
+ Location setting: on
  Chipset: not reported
  Hardware year: not reported
  Raw measurements: no
  Signals: not reported by the chipset
== Health Services ==
+ Running exercise: supported
+ Speed: yes
+ Speed stats (max/avg): yes
+ Pace: yes
+ Distance: yes
+ Location: yes
+ Cadence: yes
+ Heart rate: yes
  Auto-pause: yes
  Data types in total: 21
== Motion sensors ==
+ Accelerometer: up to 100 Hz, batch 950
+ Gyroscope: up to 100 Hz, batch 40960
+ Step detector: on change
+ Barometer: up to 100 Hz, batch 40960
+ Magnetometer: up to 100 Hz, batch 40960
== Paired devices ==
+ Hananel Phone: connected, nearby
```

Build: `OPWWE231_11_A.190`, עדכון אבטחה 2026-05-01.

## מה זה אומר

**1. Health Services נותן את כל מה שצריך.** מהירות, סטטיסטיקות מהירות, קצב, מרחק, מיקום, קדנס ודופק — כולם נתמכים בריצה. זו הייתה השאלה הפתוחה הגדולה של המחקר ("האם OnePlus בכלל חושפת את זה לאפליקציות צד שלישי"), והתשובה חיובית. עדיין לא ידוע *כמה טוב* הנתון — זה ייבדק בהקלטה.

**2. ה-GNSS נראה דרך "חלון צר".** השעון לא מדווח שם שבב, שנת חומרה, רשימת אותות או מדידות גולמיות. זה מתאים לארכיטקטורת שני השבבים: ה-GPS כנראה יושב בצד החסכוני, וצד ה-Wear OS מקבל ממנו רק את התוצאה. ההשלכות:
- אי אפשר לאשר מהתוכנה את התדר הכפול (L1+L5) שבמפרט. זה לא אומר שהוא לא קיים.
- הגישה הישירה ל-GNSS עשויה להיות דלה יותר ממה שהנחתי — למשל בלי אי-ודאות מהירות לכל דגימה. **אם כך, Health Services יהיה המקור הראשי.** ההקלטה הראשונה תכריע.

**3. חיישני התנועה טובים מהצפוי.** מד תאוצה וג'ירוסקופ ב-100Hz, עם אגירה פנימית גדולה (הג'ירוסקופ אוגר כ-7 דקות). כלומר אפשר לדגום תנועה בקצב גבוה בלי להעיר את המעבד כל הזמן. זה פותח את הדלת לזיהוי פרצים ושינויי כיוון ממד התאוצה — בדיוק מה ש-GPS של 1Hz מפספס בכדורגל.

**4. הקישור לטלפון עובד.** השעון רואה את "Hananel Phone" כמחובר וקרוב, כך שערוץ הסנכרון זמין.

**5. הגרסה תואמת.** Wear OS 5 / API 34 — בדיוק מה שהאפליקציה מכוונת אליו ומה שהאמולטור מריץ.

## מה עוד לא נבדק

- נתוני GNSS ו-Health Services בפועל: קצב דגימה, רעש, האם מגיעה אי-ודאות מהירות. דורש הקלטה בחוץ.
- האם Health Services תומך בסוג אימון "כדורגל" או שנקליט כריצה/אימון כללי.
- צריכת סוללה בזמן הקלטה.
- מסך האפליקציה לא צולם: השעון היה על המטען, ומסך הטעינה מכסה כל אפליקציה.
