import subprocess, os
repo = r'C:\Users\72952\OneDrive\Desktop\ui\CompanionChat'
os.chdir(repo)

# Try to restore Strings.kt from git
r = subprocess.run(['git', 'checkout', '--', 'app/src/main/java/com/companion/chat/locale/Strings.kt'], 
    capture_output=True, text=True, shell=False)
print('stdout:', r.stdout)
print('stderr:', r.stderr[:500] if r.stderr else '')
print('RC:', r.returncode)

if r.returncode != 0:
    # Try with git show HEAD
    r2 = subprocess.run(['git', 'show', 'HEAD:app/src/main/java/com/companion/chat/locale/Strings.kt'], 
        capture_output=True, text=True, shell=False)
    print('HEAD version:', len(r2.stdout), 'bytes' if r2.stdout else 'NOT_FOUND')
