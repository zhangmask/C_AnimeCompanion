$adb = 'C:\Users\72952\AppData\Local\Android\Sdk\platform-tools\adb.exe'
& $adb kill-server 2>&1 | Out-Null
Start-Sleep -Seconds 1
& $adb start-server 2>&1 | Out-Null
Start-Sleep -Seconds 3
& $adb devices
