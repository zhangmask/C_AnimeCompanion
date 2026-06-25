import winreg

JAVA_HOME = r"D:\Android\Android Studio\jbr"
ANDROID_HOME = r"C:\Users\72952\AppData\Local\Android\Sdk"

with winreg.ConnectRegistry(None, winreg.HKEY_CURRENT_USER) as root:
    with winreg.OpenKey(root, r'Environment', 0, winreg.KEY_SET_VALUE) as key:
        winreg.SetValueEx(key, 'JAVA_HOME', 0, winreg.REG_SZ, JAVA_HOME)
        winreg.SetValueEx(key, 'ANDROID_HOME', 0, winreg.REG_SZ, ANDROID_HOME)
        winreg.SetValueEx(key, 'ANDROID_SDK_ROOT', 0, winreg.REG_SZ, ANDROID_HOME)
    print("Registry written.")

# Verify
with winreg.ConnectRegistry(None, winreg.HKEY_CURRENT_USER) as root:
    with winreg.OpenKey(root, r'Environment') as key:
        for name in ['JAVA_HOME','ANDROID_HOME','ANDROID_SDK_ROOT']:
            val, _ = winreg.QueryValueEx(key, name)
            print(f"  {name} = {val}")

# Broadcast WM_SETTINGCHANGE so new processes pick up the change
import ctypes
HWND_BROADCAST = 0xFFFF
WM_SETTINGCHANGE = 0x001A
res = ctypes.windll.user32.SendMessageTimeoutW(
    HWND_BROADCAST, WM_SETTINGCHANGE, 0, "Environment", 0x2, 1000, None)
print(f"WM_SETTINGCHANGE broadcast: {res}")
print("NOTE: open a NEW terminal/CMD window to see the new env vars.")
