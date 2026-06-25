@echo off
"C:\Users\72952\AppData\Local\Android\Sdk\platform-tools\adb.exe" install -r -t "C:\Users\72952\OneDrive\Desktop\ui\CompanionChat\app\build\outputs\apk\debug\app-debug.apk" 2>&1
echo EXIT=%ERRORLEVEL%
