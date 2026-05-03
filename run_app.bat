@echo off
echo ================================================
echo   AUCTION PLATFORM JAVA - RUN APP
echo ================================================
echo.

REM Navigate to project directory
cd /d "%~dp0"

REM Check if build exists
if not exist "build\classes\java\main\com\auction\app\MainApplication.class" (
    echo [!] Build not found. Building project first...
    echo.
    call gradlew.bat build -x test
    if %ERRORLEVEL% NEQ 0 (
        echo ERROR: Build failed!
        pause
        exit /b 1
    )
)

REM Run the application
echo [*] Starting AuctionPlatformJava...
echo.
call gradlew.bat run

pause
