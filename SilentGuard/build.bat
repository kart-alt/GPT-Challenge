@echo off
REM Silent Guard - Quick Build & Run Script for Windows
REM This script helps you quickly build and deploy the app

echo ========================================
echo SILENT GUARD - BUILD SCRIPT
echo ========================================
echo.

REM Check if gradlew exists
if not exist gradlew.bat (
    echo ERROR: gradlew.bat not found!
    echo Please run this script from the SilentGuard root directory.
    pause
    exit /b 1
)

echo What would you like to do?
echo.
echo 1. Clean build
echo 2. Build debug APK
echo 3. Build and install on device
echo 4. View logs (Logcat)
echo 5. Create dummy ML model (requires Python)
echo 6. Full build + install + logs
echo.

set /p choice="Enter choice (1-6): "

if "%choice%"=="1" goto clean
if "%choice%"=="2" goto build
if "%choice%"=="3" goto install
if "%choice%"=="4" goto logs
if "%choice%"=="5" goto model
if "%choice%"=="6" goto full
goto invalid

:clean
echo.
echo === Cleaning project ===
call gradlew.bat clean
echo Done!
pause
exit /b 0

:build
echo.
echo === Building debug APK ===
call gradlew.bat assembleDebug
echo.
echo APK location: app\build\outputs\apk\debug\app-debug.apk
pause
exit /b 0

:install
echo.
echo === Building and installing on device ===
echo Make sure device is connected with USB debugging enabled!
echo.
call gradlew.bat installDebug
echo.
echo App installed! Check your device.
pause
exit /b 0

:logs
echo.
echo === Starting Logcat (Silent Guard only) ===
echo Press Ctrl+C to stop
echo.
adb logcat -s SilentGuardApp AudioClassifier MotionAnalyzer DecisionEngine ContextValidator AlertManager DistressDetectionService
pause
exit /b 0

:model
echo.
echo === Creating dummy ML model ===
cd scripts
python create_model.py
cd ..
pause
exit /b 0

:full
echo.
echo === Full build, install, and logs ===
echo.
echo [1/3] Building...
call gradlew.bat assembleDebug
echo.
echo [2/3] Installing...
call gradlew.bat installDebug
echo.
echo [3/3] Starting logs (press Ctrl+C to stop)...
timeout /t 2 /nobreak
adb logcat -s SilentGuardApp AudioClassifier MotionAnalyzer DecisionEngine
pause
exit /b 0

:invalid
echo.
echo Invalid choice. Please run again and choose 1-6.
pause
exit /b 1
