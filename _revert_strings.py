path = r'C:\Users\72952\OneDrive\Desktop\ui\CompanionChat'
import subprocess, os
os.chdir(path)
r = subprocess.run(['git', 'checkout', '--', 'app/src/main/java/com/companion/chat/locale/Strings.kt'], 
    capture_output=True, text=True, shell=True)
print(r.stdout[:500] if r.stdout else r.stderr[:500])
print('RC:', r.returncode)
