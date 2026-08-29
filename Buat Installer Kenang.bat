@echo off
rem ============================================================
rem  Buat Installer Kenang - klik file ini untuk menghasilkan
rem  satu file installer (DISTRIBUSI\Kenang-Setup.msi) yang siap
rem  dikirim ke user. User tinggal klik file itu, aplikasi
rem  terpasang sendiri (Java/JRE sudah dibundel, tanpa install
rem  aplikasi lain), lalu ikon Kenang muncul di Start Menu.
rem ============================================================
chcp 65001 >nul
title Membuat installer Kenang
setlocal

set "ROOT=%~dp0"
set "JAVA_HOME=%ROOT%.tools\jdk21"
set "OUT=%ROOT%DISTRIBUSI"

echo.
echo  Menutup Kenang yang masih berjalan (jika ada)...
taskkill /f /im Kenang.exe >nul 2>&1

echo  Membangun installer - proses ini butuh beberapa menit, biarkan saja...
echo.
cd /d "%ROOT%kenang-desktop"
call "%ROOT%kenang-desktop\gradlew.bat" --console=plain -q :app:packageMsi
if errorlevel 1 (
    echo.
    echo  GAGAL membuat installer. Baca pesan kesalahan di atas.
    if /i not "%~1"=="auto" pause
    exit /b 1
)

if not exist "%OUT%" mkdir "%OUT%"
set "MSI="
for %%f in ("%ROOT%kenang-desktop\app\build\compose\binaries\main\msi\Kenang-*.msi") do set "MSI=%%~ff"
if not defined MSI (
    echo  GAGAL: file MSI tidak ditemukan setelah build.
    if /i not "%~1"=="auto" pause
    exit /b 1
)
copy /y "%MSI%" "%OUT%\Kenang-Setup.msi" >nul

echo.
echo  ============================================================
echo   SELESAI! File siap dikirim ke user:
echo   %OUT%\Kenang-Setup.msi
echo.
echo   Cara pakai untuk user: kirim file itu (WA/GDrive/flashdisk),
echo   user tinggal klik 2x - aplikasi terpasang otomatis dan
echo   Kenang muncul di Start Menu + Desktop. Tidak perlu install
echo   Java atau aplikasi lain.
echo  ============================================================
echo.
if /i not "%~1"=="auto" (
    start "" explorer /select,"%OUT%\Kenang-Setup.msi"
    pause
)
endlocal
