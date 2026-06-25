import subprocess, os
jhome = r'D:\Android\Android Studio\jbr'
java = os.path.join(jhome, 'bin', 'java.exe')
os.environ['JAVA_HOME'] = jhome
os.environ['PATH'] = os.path.join(jhome, 'bin') + ';' + os.environ.get('PATH', '')
r = subprocess.run([java, '-version'], capture_output=True, text=True)
print('STDOUT:', r.stdout)
print('STDERR:', r.stderr)
print('RC:', r.returncode)
