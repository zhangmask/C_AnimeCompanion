import subprocess, os
os.environ['JAVA_HOME'] = r'D:\Android\Android Studio\jbr'
os.chdir(r'C:\Users\72952\OneDrive\Desktop\ui\CompanionChat')
r = subprocess.run(['gradlew.bat', 'compileDebugKotlin', '--no-daemon'], capture_output=True, text=True, shell=True)
for l in (r.stdout or '').splitlines():
    if '.kt:' in l and 'error' in l.lower():
        print(l)
print('---')
for l in (r.stderr or '').splitlines()[-20:]:
    if 'error' in l.lower() or '.kt:' in l:
        print(l)
