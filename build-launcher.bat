@echo off
chcp 65001 >nul
echo ========================================
echo Building Hardcore Minecraft Launcher
echo (pack only, no code signing)
echo ========================================
echo.

echo Checking dependencies...
echo Installing/updating dependencies...
call npm install
echo Installing required modules (smart-buffer, socks)...
call npm install smart-buffer socks --force

echo.
echo Closing any running launcher processes...
taskkill /F /IM "Hardcore Minecraft Launcher.exe" >nul 2>&1
taskkill /F /IM electron.exe >nul 2>&1
timeout /t 1 /nobreak >nul

echo.
echo Clearing old build directory...
if exist "LauncherExe\Hardcore Minecraft Launcher-win32-x64" (
    rmdir /s /q "LauncherExe\Hardcore Minecraft Launcher-win32-x64" 2>nul
)

echo.
echo Packing with electron-packager...
call npm run build

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo Build complete!
    echo Launcher: LauncherExe\Hardcore Minecraft Launcher-win32-x64\Hardcore Minecraft Launcher.exe
    echo ========================================
) else (
    echo.
    echo ========================================
    echo Build failed! Check errors above.
    echo ========================================
)

pause
