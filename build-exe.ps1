$env:CSC_IDENTITY_AUTO_DISCOVERY = "false"
$env:WIN_CSC_LINK = ""

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Building Hardcore Minecraft Launcher" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "Building launcher executable..." -ForegroundColor Yellow
Write-Host "(Code signing disabled)" -ForegroundColor Gray
Write-Host ""

npm run build-win

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "Build complete!" -ForegroundColor Green
    Write-Host "Check LauncherExe folder for the .exe file" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
} else {
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Red
    Write-Host "Build failed! Check errors above." -ForegroundColor Red
    Write-Host "========================================" -ForegroundColor Red
}








