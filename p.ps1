$adb = 'C:\Users\72952\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$result = & $adb devices 2>&1 | Out-String
Write-Output $result
if ($result -match 'device\b') {
    & $adb install -r 'C:\Users\72952\OneDrive\Desktop\ui\CompanionChat\app\build\outputs\apk\debug\app-debug.apk' 2>&1
    Write-Output "EXIT=$LASTEXITCODE"
} else {
    Write-Output 'DEVICE_NOT_CONNECTED'
}
