# WorkTimeTracker Pro Android

نسخه اندروید پروژه `WorkTimeTracker_ManicStyle` با رابط گرافیکی مشابه، تم تیره/روشن، ثبت زمان استفاده از برنامه‌ها، تایم‌لاین، گزارش روزانه/ماهانه، تگ‌گذاری و خروجی PDF.

## قابلیت‌ها

- ثبت خودکار برنامه فعال با `Usage Access`
- سرویس foreground برای ادامه ثبت در پس‌زمینه
- اجرای خودکار پس از روشن شدن گوشی، در صورت فعال بودن تنظیمات برنامه و محدود نبودن توسط سازنده گوشی
- داشبورد روزانه با تاریخ شمسی، مجموع زمان، شروع/پایان روز، زمان فعال و زمان فاصله
- تایم‌لاین رنگی شبیه نسخه دسکتاپ
- جدول فعالیت‌ها با جست‌وجو، فیلتر `All / Tagged / Untagged / Applications / Documents`
- انتخاب چند ردیف و افزودن تگ/پروژه
- بخش `Top usage` برای برنامه‌های پرمصرف
- صفحه `Timesheet / Reports` برای گزارش بازه‌ای و ماه جاری
- خروجی PDF در مسیر فایل‌های برنامه: `Android/data/com.example.worktimetracker/files/Documents`
- تم تاریک/روشن و تنظیم روشن/خاموش بودن ثبت خودکار

## محدودیت‌های اندروید نسبت به ویندوز

اندروید به برنامه معمولی اجازه نمی‌دهد بدون رضایت کاربر، عنوان پنجره فعال یا اسکرین‌شات مخفی بگیرد. بنابراین این نسخه به‌جای APIهای ویندوز مثل `win32gui` و `ImageGrab` از `UsageStatsManager` استفاده می‌کند و نام برنامه/پکیج را ثبت می‌کند. برای ثبت دقیق‌تر، بعد از نصب باید مجوز `Usage Access` را فعال کنید.

## ساخت APK در GitHub

1. فایل ZIP را Extract کن.
2. پوشه پروژه را داخل یک Repository جدید GitHub آپلود کن.
3. از تب **Actions**، workflow با نام **Build Android APK** را اجرا کن، یا یک commit روی `main` / `master` بزن.
4. بعد از پایان build، از بخش **Artifacts** فایل `WorkTimeTracker-debug-apk` را دانلود کن.
5. APK داخل artifact مسیر زیر است:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## ساخت محلی

اگر Android SDK و Gradle نصب داری:

```bash
gradle --no-daemon assembleDebug
```

## نسخه‌های Build

- Android Gradle Plugin: `9.2.1`
- Gradle در GitHub Actions: `9.6.1`
- Java: `Temurin JDK 17`
- compileSdk / targetSdk: `37`
- minSdk: `23`

## فعال‌سازی بعد از نصب

1. برنامه را باز کن.
2. روی **Usage Access** بزن.
3. در تنظیمات اندروید، `WorkTimeTracker Pro` را فعال کن.
4. به برنامه برگرد و بگذار سرویس foreground فعال بماند.

روی بعضی گوشی‌ها مثل Xiaomi، Huawei، Oppo و Vivo باید Battery Optimization را هم برای برنامه غیرفعال کنی تا سرویس در پس‌زمینه بسته نشود.
