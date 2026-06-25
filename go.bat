@echo off
set ADB=C:\Users\72952\AppData\Local\Android\Sdk\platform-tools\adb.exe
%ADB% devices
%ADB% install -r -t C:\Users\72952\OneDrive\Desktop\ui\CompanionChat\app\build\outputs\apk\debug\app-debug.apk
echo DONE=%ERRORLEVEL%
