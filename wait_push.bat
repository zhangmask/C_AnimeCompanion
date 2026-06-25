@echo off
set "ADB=C:\Users\72952\AppData\Local\Android\Sdk\platform-tools\adb.exe"
:RETRY
%ADB% devices 2>nul | findstr "device" >nul
if %ERRORLEVEL%==0 goto INSTALL
echo Waiting for device...
ping -n 2 127.0.0.1 >nul
goto RETRY
:INSTALL
echo Device found, installing...
%ADB% install -r -t "C:\Users\72952\OneDrive\Desktop\ui\CompanionChat\app\build\outputs\apk\debug\app-debug.apk"
echo EXIT=%ERRORLEVEL%
