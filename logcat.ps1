$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb logcat -d -s DrawerDiag:E
Write-Host "--- Dumpsys for drawer activity ---"
& $adb shell dumpsys activity top | Select-String -Pattern "ChatScreen|Drawer|ConversationDrawer" -SimpleMatch | Select-Object -First 20
