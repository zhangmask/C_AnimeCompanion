@echo off
set "PATH=C:\Users\72952\AppData\Local\Android\Sdk\platform-tools;%PATH%"
adb kill-server
adb start-server
timeout /t 3 /nobreak >nul
adb devices
