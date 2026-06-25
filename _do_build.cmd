@echo off
set "JAVA_HOME=D:\Android\Android Studio\jbr"
set "PATH=%JAVA_HOME%\bin;%PATH%"
cd /d "C:\Users\72952\OneDrive\Desktop\ui\CompanionChat"
call gradlew.bat --no-daemon assembleDebug 2>&1
echo EXIT_CODE=%ERRORLEVEL%
