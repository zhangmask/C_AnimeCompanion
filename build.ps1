$env:JAVA_HOME = 'D:\Android\Android Studio\jbr'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
Set-Location 'C:\Users\72952\OneDrive\Desktop\ui\CompanionChat'
& .\gradlew.bat --no-daemon assembleDebug 2>&1 | Select-Object -Last 30
