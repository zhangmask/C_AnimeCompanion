@echo off
set A=C:\Users\72952\AppData\Local\Android\Sdk\platform-tools\adb.exe
%A% kill-server 2>nul
%A% start-server 2>nul
%A% devices
if %ERRORLEVEL%==0 (
  %A% install -r -t C:\Users\72952\OneDrive\Desktop\ui\CompanionChat\app\build\outputs\apk\debug\app-debug.apk
  echo DONE=%ERRORLEVEL%
) else (
  echo ADB_FAILED
)
