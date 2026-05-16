@echo off
cd /d "%~dp0"

echo Building PonyNPC...
call gradlew.bat build
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ERROR: Build failed. See errors above.
    pause
    exit /b 1
)

echo.
echo ============================================
echo  BUILD OK
echo ============================================
dir /b build\libs\*.jar
echo.
pause
