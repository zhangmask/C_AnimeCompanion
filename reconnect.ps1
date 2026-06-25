$env:ANDROID_HOME = 'C:\Users\72952\AppData\Local\Android\Sdk'
$env:Path = "$env:ANDROID_HOME\platform-tools;$env:Path"
& adb reconnect 2>&1
Start-Sleep -Seconds 2
& adb devices 2>&1
