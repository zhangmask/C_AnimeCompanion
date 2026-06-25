@echo off
set "A=C:\Users\72952\AppData\Local\Android\Sdk\platform-tools\adb.exe"
%A% devices 2>&1
%A% install -r -t "C:\Users\72952\OneDrive\Desktop\ui\CompanionChat\app\build\outputs\apk\debug\app-debug.apk" 2>&1
echo EXIT=%ERRORLEVEL%
