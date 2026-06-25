@echo off
set "ANDROID_HOME=C:\Users\72952\AppData\Local\Android\Sdk"
set "PATH=%ANDROID_HOME%\platform-tools;%PATH%"
adb devices
echo ---
adb install -r "C:\Users\72952\OneDrive\Desktop\ui\CompanionChat\app\build\outputs\apk\debug\app-debug.apk"
echo INSTALL_EXIT=%ERRORLEVEL%
