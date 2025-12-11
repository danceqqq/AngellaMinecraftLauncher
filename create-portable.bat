@echo off
echo Creating portable launcher from win-unpacked...
echo.

if not exist "LauncherExe\win-unpacked\Hardcore Minecraft Launcher.exe" (
    echo Error: win-unpacked folder not found or launcher not built!
    echo Please run build-launcher.bat first.
    pause
    exit /b 1
)

echo Creating portable launcher...
echo.

REM Используем 7zip для создания portable exe (если установлен)
where 7z >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo Using 7zip to create portable launcher...
    7z a -sfx "LauncherExe\Hardcore Minecraft Launcher-1.0.0-portable.exe" "LauncherExe\win-unpacked\*" -mx=9
    if %ERRORLEVEL% EQU 0 (
        echo.
        echo ========================================
        echo Portable launcher created successfully!
        echo File: LauncherExe\Hardcore Minecraft Launcher-1.0.0-portable.exe
        echo ========================================
    ) else (
        echo.
        echo ========================================
        echo Failed to create portable launcher with 7zip.
        echo You can use the launcher from: LauncherExe\win-unpacked\Hardcore Minecraft Launcher.exe
        echo ========================================
    )
) else (
    echo 7zip not found. Creating simple portable package...
    echo.
    echo You can manually copy the entire "win-unpacked" folder to use as portable launcher.
    echo Or install 7zip and run this script again.
    echo.
    echo Launcher is available at: LauncherExe\win-unpacked\Hardcore Minecraft Launcher.exe
)

pause








