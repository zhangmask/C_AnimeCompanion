@echo off
set A=C:\Users\72952\AppData\Local\Android\Sdk\platform-tools\adb.exe
%A% kill-server
timeout /t 2 /nobreak >nul
%A% start-server
timeout /t 3 /nobreak >nul
%A% devices
%A% install -r -t C:\Users\72952\OneDrive\Desktop\ui\CompanionChat\app\build\outputs\apk\debug\app-debug.apk
echo DONE=%ERRORLEVEL%
