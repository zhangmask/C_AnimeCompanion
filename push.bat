@echo off
set "PATH=C:\Users\72952\AppData\Local\Android\Sdk\platform-tools;%PATH%"
adb devices
adb install -r "C:\Users\72952\OneDrive\Desktop\ui\CompanionChat\app\build\outputs\apk\debug\app-debug.apk"
echo DONE=%ERRORLEVEL%
