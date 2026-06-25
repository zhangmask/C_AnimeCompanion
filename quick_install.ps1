$adb = 'C:\Users\72952\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$dev = & $adb devices 2>&1
Write-Output $dev
if ($dev -match 'device$') {
    & $adb install -r 'C:\Users\72952\OneDrive\Desktop\ui\CompanionChat\app\build\outputs\apk\debug\app-debug.apk' 2>&1
    Write-Output "EXIT=$LASTEXITCODE"
} else {
    Write-Output 'DEVICE_NOT_CONNECTED'
}
