@echo off
echo ================================================
echo   AUCTION PLATFORM JAVA - BUILD & TEST
echo ================================================
echo.

REM Clean previous build
echo [1/4] Cleaning previous build...
call gradlew.bat clean

REM Build project without tests
echo.
echo [2/4] Building project...
call gradlew.bat build -x test
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ERROR: Build failed!
    pause
    exit /b 1
)

REM Run tests (if any)
echo.
echo [3/4] Running tests...
call gradlew.bat test
if %ERRORLEVEL% NEQ 0 (
    echo WARNING: Tests failed or not found
    echo.
)

REM Summary
echo.
echo ================================================
echo   BUILD & TEST COMPLETE
echo ================================================
echo.
echo Output:
echo   - Compiled: build/classes/java/main/
echo   - Tests: build/reports/tests/test/
echo   - JAR: build/libs/
echo.
echo To run the app:
echo   gradlew.bat run
echo.
pause
