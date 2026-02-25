# DrinkReminder - Android App

## Project Status
All source code is written and complete (42 files). Ready to build.

## Build Toolchain (installed)
- **JDK 17** (Adoptium Temurin)
- **Android Studio** with Android SDK 36
- **AGP** 8.7.3, **Kotlin** 2.0.21, **Gradle** 8.9, **KSP** 2.0.21-1.0.28
- compileSdk/targetSdk = 36, minSdk = 31

## Next Step: Build the APK
1. Open this project folder in Android Studio
2. Let Gradle sync complete (it downloads the wrapper JAR + all dependencies)
3. Run `./gradlew assembleDebug` in terminal or click the green Run button
4. APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Tech Stack
- Kotlin 2.0 + Jetpack Compose (BOM 2024.11) + Material 3
- Room 2.6.1 (database), Hilt 2.52 (DI), WorkManager 2.10 (notifications), DataStore 1.1.1 (preferences), Glance 1.1.1 (widget)
- Package: `com.drinkreminder.app`

## Architecture
- Single-activity app with Compose Navigation (bottom nav: Home / History / Settings)
- MVVM pattern: ViewModels → Repository → Room DAO + DataStore
- Smart reminders via WorkManager spread evenly across waking hours (default 07:00-20:00)
- Home screen widget (Glance) with quick-add bottle action

## Key Files
- `build.gradle.kts` (root) — plugin versions
- `app/build.gradle.kts` — all dependencies and SDK config
- `app/src/main/java/com/drinkreminder/app/` — all Kotlin source
  - `data/` — Room DB, DAO, entity, DataStore prefs, repository
  - `di/` — Hilt module
  - `ui/` — Screens (home, history, settings), components, theme, navigation
  - `notification/` — WorkManager reminder worker + scheduler + notification helper
  - `widget/` — Glance widget + receiver
