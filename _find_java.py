import os, subprocess

# 1. List all drives
print("=== Drives ===")
try:
    import ctypes
    bitmask = ctypes.windll.kernel32.GetLogicalDrives()
    for i in range(26):
        if bitmask & (1 << i):
            print(f"  {chr(65+i)}:\\")
except Exception as e:
    print("drive enum failed:", e)

# 2. Check current env
print("=== Current env ===")
for k in ['JAVA_HOME','ANDROID_HOME','ANDROID_SDK_ROOT','PATH']:
    v = os.environ.get(k,'')
    print(f"  {k}={(v[:120]+'...') if len(v)>120 else v}")

# 3. Search for java.exe in likely locations
import glob
candidates = [
    r"D:\Android",
    r"C:\Android",
    r"D:\Program Files\Android",
    r"C:\Program Files\Android",
    r"D:\Program Files\Java",
    r"C:\Program Files\Java",
    r"D:\Java",
    r"C:\Java",
    r"D:\jdk*",
    r"C:\jdk*",
    os.path.expanduser(r"~\AppData\Local\Android\Sdk"),
    os.path.expanduser(r"~\.jdks"),
    os.path.expanduser(r"~\AppData\Local\Programs"),
]
print("=== Likely dirs ===")
for c in candidates:
    # support wildcard
    for base in glob.glob(c):
        if os.path.exists(base):
            print(f"  EXISTS: {base}")

# 4. find java.exe under any existing candidate
print("=== java.exe search ===")
found = []
def walk_limit(path, depth=0, maxdepth=4):
    if depth > maxdepth: return
    try:
        for e in os.scandir(path):
            if e.is_dir(follow_symlinks=False):
                yield from walk_limit(e.path, depth+1, maxdepth)
            elif e.name.lower() == 'java.exe':
                yield e.path
    except (PermissionError, OSError):
        pass

for c in candidates:
    for base in glob.glob(c):
        if not os.path.exists(base): continue
        for j in walk_limit(base, 0, 4):
            found.append(j)
            print(f"  FOUND: {j}")
            if len(found) >= 20:
                break
        if len(found) >= 20: break
    if len(found) >= 20: break

# 5. Look at Android Studio launcher in Start Menu / desktop shortcuts
print("=== Android Studio shortcuts ===")
locs = [
    os.path.expanduser(r"~\AppData\Roaming\Microsoft\Windows\Start Menu\Programs"),
    os.path.expanduser(r"~\Desktop"),
    r"C:\ProgramData\Microsoft\Windows\Start Menu\Programs",
]
for loc in locs:
    if not os.path.exists(loc): continue
    for root, dirs, files in os.walk(loc):
        for f in files:
            if 'android' in f.lower() or 'studio' in f.lower():
                print(f"  {os.path.join(root,f)}")
