@echo off
set A=C:\Users\72952\AppData\Local\Android\Sdk\platform-tools\adb.exe
echo Checking device...
%A% wait-for-device
echo Device connected!
%A% install -r -t C:\Users\72952\OneDrive\Desktop\ui\CompanionChat\app\build\outputs\apk\debug\app-debug.apk
echo DONE=%ERRORLEVEL%
