@echo off
taskkill /f /im adb.exe 2>nul
timeout /t 2 /nobreak >nul
set "PATH=C:\Users\72952\AppData\Local\Android\Sdk\platform-tools;%PATH%"
adb devices
