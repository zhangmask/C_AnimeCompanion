@echo off
set "PATH=C:\Users\72952\AppData\Local\Android\Sdk\platform-tools;%PATH%"
adb kill-server
adb start-server
ping -n 4 127.0.0.1 >nul
adb devices
