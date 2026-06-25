import subprocess
ADB = r'C:\Users\72952\AppData\Local\Android\Sdk\platform-tools\adb.exe'
r = subprocess.run([ADB, 'shell', 'logcat', '-d', '-t', '500', 'DrawerDiag:E', '*:S'], capture_output=True, text=True, timeout=20)
print(r.stdout if r.stdout.strip() else '(EMPTY - no DrawerDiag logs)')
print('rc:', r.returncode, 'err:', r.stderr[:100] if r.stderr else '')
