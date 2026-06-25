@echo off
taskkill /f /im adb.exe 2>nul
ping -n 2 127.0.0.1 >nul
set "ADB=C:\Users\72952\AppData\Local\Android\Sdk\platform-tools\adb.exe"
%ADB% start-server
ping -n 2 127.0.0.1 >nul
%ADB% devices
