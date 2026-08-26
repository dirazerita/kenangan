@echo off
rem ============================================================
rem  Jalankan Kenang (build dogfooding / Stabilization)
rem  Klik dua kali file ini untuk membuka aplikasi.
rem  Kalau exe belum ada (mis. setelah "clean"), otomatis
rem  dibangun dulu lewat Gradle (butuh 1-2 menit, sekali saja).
rem ============================================================
setlocal
set "ROOT=%~dp0"
set "EXE=%ROOT%kenang-desktop\app\build\compose\binaries\main\app\Kenang\Kenang.exe"

if not exist "%EXE%" (
    echo Aplikasi belum ter-build. Membangun dulu, mohon tunggu 1-2 menit...
    set "JAVA_HOME=%ROOT%.tools\jdk21"
    pushd "%ROOT%kenang-desktop"
    call gradlew.bat :app:createDistributable
    popd
)

if not exist "%EXE%" (
    echo.
    echo GAGAL: build tidak menghasilkan %EXE%
    echo Coba jalankan manual: kenang-desktop\gradlew.bat :app:createDistributable
    pause
    exit /b 1
)

start "" "%EXE%"
endlocal
