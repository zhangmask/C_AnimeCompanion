@echo off
set "ADB=C:\Users\72952\AppData\Local\Android\Sdk\platform-tools\adb.exe"
"%ADB%" kill-server
"%ADB%" start-server
ping -n 3 127.0.0.1 >nul
"%ADB%" devices
