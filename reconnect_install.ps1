$adb = 'C:\Users\72952\AppData\Local\Android\Sdk\platform-tools\adb.exe'
& $adb kill-server 2>&1 | Out-Null
Start-Sleep -Seconds 1
& $adb start-server 2>&1 | Out-Null
Start-Sleep -Seconds 3
$dev = & $adb devices 2>&1
Write-Output $dev
if ($dev -match 'device$') {
    & $adb install -r 'C:\Users\72952\OneDrive\Desktop\ui\CompanionChat\app\build\outputs\apk\debug\app-debug.apk' 2>&1
    Write-Output "EXIT=$LASTEXITCODE"
} else {
    Write-Output 'DEVICE_NOT_CONNECTED'
}
