@echo off
set "ADB=C:\Users\72952\AppData\Local\Android\Sdk\platform-tools\adb.exe"
"%ADB%" kill-server 2>nul
"%ADB%" start-server
ping -n 3 127.0.0.1 >nul
"%ADB%" devices
echo ---INSTALL---
"%ADB%" install -r -t "C:\Users\72952\OneDrive\Desktop\ui\CompanionChat\app\build\outputs\apk\debug\app-debug.apk"
echo EXIT=%ERRORLEVEL%
