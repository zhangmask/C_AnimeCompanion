$adb = 'C:\Users\72952\AppData\Local\Android\Sdk\platform-tools\adb.exe'
Write-Output "=== Kill server ==="
& $adb kill-server 2>&1 | ForEach-Object { Write-Output $_ }
Start-Sleep -Seconds 2
Write-Output "=== Start server ==="
& $adb start-server 2>&1 | ForEach-Object { Write-Output $_ }
Start-Sleep -Seconds 3
Write-Output "=== Devices ==="
& $adb devices -l 2>&1 | ForEach-Object { Write-Output $_ }
