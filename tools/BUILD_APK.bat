@echo off
setlocal
cd /d "%~dp0.."
where gradle >nul 2>&1
if errorlevel 1 (
  echo [HIBA] Gradle nincs telepitve vagy nincs PATH-on.
  echo Nyisd meg a projektet Android Studio-ban, es Build ^> Build APK(s).
  pause
  exit /b 1
)
gradle --no-daemon :app:assembleDebug
if errorlevel 1 (
  echo [HIBA] Android build sikertelen. Ellenorizd az Android SDK 35-ot es a Java 17-et.
  pause
  exit /b 1
)
echo.
echo APK: %CD%\app\build\outputs\apk\debug\app-debug.apk
pause
