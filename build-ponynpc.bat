@echo off
cd /d "%~dp0"

:: Ensure libs\ exists
if not exist "libs" mkdir libs

:: Check if MLP jar is already in libs\
set MLP_JAR=
for %%f in (libs\minelittlepony-*.jar) do set MLP_JAR=%%f

:: If missing — search in loom-cache and copy
if not defined MLP_JAR (
    echo [setup] MineLittlePony jar not found in libs\, searching loom-cache...
    for /r ".gradle\loom-cache\remapped_mods" %%f in (minelittlepony-*.jar) do (
        if not defined MLP_JAR set MLP_JAR=%%f
    )
    if not defined MLP_JAR (
        echo.
        echo ERROR: MineLittlePony jar not found in libs\ or .gradle\loom-cache\.
        echo Build MineLittlePony at least once to populate the cache, then retry.
        pause
        exit /b 1
    )
    echo [setup] Found: %MLP_JAR%
    copy "%MLP_JAR%" "libs\" >nul
    echo [setup] Copied to libs\
)

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
