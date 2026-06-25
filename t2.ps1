$env:Path = 'C:\Users\72952\AppData\Local\Android\Sdk\platform-tools;' + $env:Path
adb connect aa972376 2>&1
Start-Sleep -Seconds 2
adb devices
