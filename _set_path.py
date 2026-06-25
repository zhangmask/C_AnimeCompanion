import os, subprocess

# Read current user PATH
import winreg
with winreg.ConnectRegistry(None, winreg.HKEY_CURRENT_USER) as root:
    with winreg.OpenKey(root, r'Environment') as key:
        val, typ = winreg.QueryValueEx(key, 'Path')
print("CURRENT USER PATH:")
print(repr(val))
print()

# Paths to add (in order)
add = [
    r"D:\Android\Android Studio\jbr\bin",
    r"C:\Users\72952\AppData\Local\Android\Sdk\platform-tools",
    r"C:\Users\72952\AppData\Local\Android\Sdk\cmdline-tools\latest\bin",
]

# Normalize existing entries
existing = [p.strip() for p in val.split(';') if p.strip()]
print("EXISTING entries:")
for p in existing:
    print(f"  {p}")
print()

# Filter out ones already present (case-insensitive on Windows)
to_add = [p for p in add if p.lower() not in [e.lower() for e in existing]]
print("TO ADD:")
for p in to_add:
    print(f"  {p}")

if not to_add:
    print("Nothing to add. Already set.")
else:
    new_path = ';'.join(existing + to_add)
    print()
    print("NEW PATH will be:")
    print(new_path)
    # Set via setx (truncates at 1024 chars in cmd, but registry has no limit; use winreg directly)
    with winreg.ConnectRegistry(None, winreg.HKEY_CURRENT_USER) as root:
        with winreg.OpenKey(root, r'Environment', 0, winreg.KEY_SET_VALUE) as key:
            winreg.SetValueEx(key, 'Path', 0, winreg.REG_EXPAND_SZ, new_path)
    print()
    print("SUCCESS: User PATH updated via registry.")
