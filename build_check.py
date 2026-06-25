import subprocess, os
os.environ['JAVA_HOME'] = r'D:\Android\Android Studio\jbr'
os.chdir(r'C:\Users\72952\OneDrive\Desktop\ui\CompanionChat')
r = subprocess.run(['gradlew.bat', 'compileDebugKotlin', '--no-daemon'], capture_output=True, text=True, shell=True)
lines = (r.stdout or '').splitlines()
for l in lines[-30:]:
    print(l)
print('RC:', r.returncode)
print('SUCCESS' if r.returncode == 0 else 'FAILED')
