@echo off
cd /d "%~dp0"

echo [1/2] Building root MineLittlePony (downloads kirin/mson, creates jar)...
call gradlew.bat :remapJar -PskipSubmod
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ERROR: Root project build failed.
    echo Most likely cause: repo.minelittlepony-mod.com timed out.
    echo Wait a minute and try again, or check your internet connection.
    pause
    exit /b 1
)

echo.
echo [2/2] Building PonyNPC submod...
call gradlew.bat :submod-ponynpc:build
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ERROR: Submod build failed. See errors above.
    pause
    exit /b 1
)

echo.
echo ============================================
echo  BUILD OK
echo ============================================
dir /b submod-ponynpc\build\libs\*.jar
echo.
pause
