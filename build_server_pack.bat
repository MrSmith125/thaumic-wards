@echo off
setlocal enabledelayedexpansion

echo =============================================
echo  Thaumic Wards - Server Pack Builder
echo =============================================
echo.

REM Check for modpack zip argument
if "%~1"=="" (
    echo Usage: build_server_pack.bat "path\to\modpack.zip"
    echo.
    echo This script builds a server-ready pack from a CurseForge modpack:
    echo   - Extracts the modpack
    echo   - Removes conflicting mods (FTB Chunks, FTB Teams, Chunk Loaders, Chicken Chunks)
    echo   - Adds the Thaumic Wards mod
    echo   - Generates optimized start scripts
    exit /b 1
)

set "MODPACK_ZIP=%~1"
set "OUTPUT_DIR=%~dp0MagicSMP-ServerPack"
set "MOD_JAR=%~dp0build\libs\thaumic_wards-1.0.0.jar"

REM Check modpack zip exists
if not exist "%MODPACK_ZIP%" (
    echo ERROR: Modpack zip not found: %MODPACK_ZIP%
    exit /b 1
)

REM Check mod jar exists
if not exist "%MOD_JAR%" (
    echo WARNING: Thaumic Wards jar not found. Building...
    call gradlew.bat build
    if errorlevel 1 (
        echo ERROR: Build failed!
        exit /b 1
    )
)

REM Clean and create output directory
if exist "%OUTPUT_DIR%" (
    echo Cleaning existing output directory...
    rmdir /s /q "%OUTPUT_DIR%"
)
mkdir "%OUTPUT_DIR%"

echo Extracting modpack...
powershell -command "Expand-Archive -Path '%MODPACK_ZIP%' -DestinationPath '%OUTPUT_DIR%\temp' -Force"

REM Move overrides to root
if exist "%OUTPUT_DIR%\temp\overrides" (
    xcopy /s /e /y "%OUTPUT_DIR%\temp\overrides\*" "%OUTPUT_DIR%\" >nul
)

REM Copy manifest for reference
if exist "%OUTPUT_DIR%\temp\manifest.json" (
    copy "%OUTPUT_DIR%\temp\manifest.json" "%OUTPUT_DIR%\" >nul
)

REM Clean temp
rmdir /s /q "%OUTPUT_DIR%\temp"

REM Create mods directory if not exists
if not exist "%OUTPUT_DIR%\mods" mkdir "%OUTPUT_DIR%\mods"

echo.
echo Removing conflicting mods...
set "REMOVED=0"

for %%f in ("%OUTPUT_DIR%\mods\ftbchunks-*.jar") do (
    if exist "%%f" (
        echo   Removing: %%~nxf
        del "%%f"
        set /a REMOVED+=1
    )
)
for %%f in ("%OUTPUT_DIR%\mods\ftbteams-*.jar") do (
    if exist "%%f" (
        echo   Removing: %%~nxf
        del "%%f"
        set /a REMOVED+=1
    )
)
for %%f in ("%OUTPUT_DIR%\mods\chunkloaders-*.jar") do (
    if exist "%%f" (
        echo   Removing: %%~nxf
        del "%%f"
        set /a REMOVED+=1
    )
)
for %%f in ("%OUTPUT_DIR%\mods\chickenchunks-*.jar") do (
    if exist "%%f" (
        echo   Removing: %%~nxf
        del "%%f"
        set /a REMOVED+=1
    )
)
echo   Removed %REMOVED% conflicting mod(s).

echo.
echo Adding Thaumic Wards...
copy "%MOD_JAR%" "%OUTPUT_DIR%\mods\" >nul
echo   Added thaumic_wards-1.0.0.jar

REM Generate start.bat
echo.
echo Generating start scripts...
(
echo @echo off
echo echo Starting Thaumic Wards SMP Server...
echo echo.
echo java -Xmx8G -Xms4G ^
echo   -XX:+UseG1GC ^
echo   -XX:+ParallelRefProcEnabled ^
echo   -XX:MaxGCPauseMillis=150 ^
echo   -XX:+UnlockExperimentalVMOptions ^
echo   -XX:+DisableExplicitGC ^
echo   -XX:+AlwaysPreTouch ^
echo   -XX:+UseNUMA ^
echo   -XX:ParallelGCThreads=20 ^
echo   -XX:ConcGCThreads=5 ^
echo   -XX:G1NewSizePercent=30 ^
echo   -XX:G1MaxNewSizePercent=40 ^
echo   -XX:G1HeapRegionSize=8M ^
echo   -XX:G1ReservePercent=20 ^
echo   -XX:G1HeapWastePercent=5 ^
echo   -XX:G1MixedGCCountTarget=4 ^
echo   -XX:InitiatingHeapOccupancyPercent=45 ^
echo   -XX:G1MixedGCLiveThresholdPercent=90 ^
echo   -XX:G1RSetUpdatingPauseTimePercent=5 ^
echo   -XX:SurvivorRatio=32 ^
echo   -XX:+PerfDisableSharedMem ^
echo   -XX:MaxTenuringThreshold=1 ^
echo   -jar forge-1.16.5-36.2.34.jar nogui
echo pause
) > "%OUTPUT_DIR%\start.bat"

REM Generate start.sh
(
echo #!/bin/bash
echo echo "Starting Thaumic Wards SMP Server..."
echo java -Xmx8G -Xms4G \
echo   -XX:+UseG1GC \
echo   -XX:+ParallelRefProcEnabled \
echo   -XX:MaxGCPauseMillis=150 \
echo   -XX:+UnlockExperimentalVMOptions \
echo   -XX:+DisableExplicitGC \
echo   -XX:+AlwaysPreTouch \
echo   -XX:+UseNUMA \
echo   -XX:ParallelGCThreads=20 \
echo   -XX:ConcGCThreads=5 \
echo   -XX:G1NewSizePercent=30 \
echo   -XX:G1MaxNewSizePercent=40 \
echo   -XX:G1HeapRegionSize=8M \
echo   -XX:G1ReservePercent=20 \
echo   -XX:G1HeapWastePercent=5 \
echo   -XX:G1MixedGCCountTarget=4 \
echo   -XX:InitiatingHeapOccupancyPercent=45 \
echo   -XX:G1MixedGCLiveThresholdPercent=90 \
echo   -XX:G1RSetUpdatingPauseTimePercent=5 \
echo   -XX:SurvivorRatio=32 \
echo   -XX:+PerfDisableSharedMem \
echo   -XX:MaxTenuringThreshold=1 \
echo   -jar forge-1.16.5-36.2.34.jar nogui
) > "%OUTPUT_DIR%\start.sh"

REM Generate eula.txt
echo eula=true > "%OUTPUT_DIR%\eula.txt"

echo.
echo =============================================
echo  Server pack built successfully!
echo  Output: %OUTPUT_DIR%
echo =============================================
echo.
echo Next steps:
echo   1. Install Forge 1.16.5-36.2.34 server in the output directory
echo   2. Download mods from CurseForge using the manifest.json
echo   3. Run start.bat to launch the server
echo.
pause
