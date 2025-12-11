@echo off
echo Testing launcher...
echo.

cd LauncherExe\win-unpacked

if not exist "Hardcore Minecraft Launcher.exe" (
    echo ERROR: Hardcore Minecraft Launcher.exe not found!
    pause
    exit /b 1
)

echo Running launcher...
echo.
"Hardcore Minecraft Launcher.exe"

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Launcher exited with error code: %ERRORLEVEL%
    echo.
    echo Checking for missing files...
    echo.
    if not exist "resources\app.asar" (
        echo ERROR: app.asar not found!
    ) else (
        echo OK: app.asar exists
    )
    if not exist "resources.pak" (
        echo ERROR: resources.pak not found!
    ) else (
        echo OK: resources.pak exists
    )
    if not exist "snapshot_blob.bin" (
        echo ERROR: snapshot_blob.bin not found!
    ) else (
        echo OK: snapshot_blob.bin exists
    )
)

pause








