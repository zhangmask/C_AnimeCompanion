import winreg, subprocess, os

# 1. Verify registry values are persisted
print("=== Registry (HKCU\Environment) ===")
with winreg.ConnectRegistry(None, winreg.HKEY_CURRENT_USER) as root:
    with winreg.OpenKey(root, r'Environment') as key:
        for name in ['JAVA_HOME','ANDROID_HOME','ANDROID_SDK_ROOT','Path']:
            try:
                val, typ = winreg.QueryValueEx(key, name)
                print(f"  {name} = {val[:200]}")
            except FileNotFoundError:
                print(f"  {name} = <not set>")

# 2. Spawn a brand-new process that reads env fresh from registry
#    Using cmd /c with a fresh environment via `start` won't help in same session.
#    Instead, broadcast WM_SETTINGCHANGE so new processes pick up changes.
import ctypes
HWND_BROADCAST = 0xFFFF
WM_SETTINGCHANGE = 0x001A
res = ctypes.windll.user32.SendMessageTimeoutW(
    HWND_BROADCAST, WM_SETTINGCHANGE, 0, "Environment", 0x2, 1000, None)
print(f"\nWM_SETTINGCHANGE broadcast result: {res}")

# 3. Launch a NEW cmd process (it should read merged env = system+user from registry)
print("\n=== New process env (freshly spawned) ===")
# subprocess inherits parent env by default. To get registry-merged env, use shell=True
# which spawns via explorer? No. The reliable way: read registry and inject.
# Instead, manually merge: take current process env, override JAVA_HOME/ANDROID_HOME from registry.
env = dict(os.environ)
with winreg.ConnectRegistry(None, winreg.HKEY_CURRENT_USER) as root:
    with winreg.OpenKey(root, r'Environment') as key:
        for name in ['JAVA_HOME','ANDROID_HOME','ANDROID_SDK_ROOT']:
            try:
                val, _ = winreg.QueryValueEx(key, name)
                env[name] = val
            except FileNotFoundError:
                pass
        # also update PATH with user PATH from registry
        try:
            user_path, _ = winreg.QueryValueEx(key, 'Path')
            env['PATH'] = user_path + ';' + os.environ.get('PATH','')
        except FileNotFoundError:
            pass

r = subprocess.run([r"D:\Android\Android Studio\jbr\bin\java.exe", '-version'],
                   capture_output=True, text=True, env=env)
print("java -version exit:", r.returncode)
print(r.stdout)
print(r.stderr)

# 4. Test that gradlew would find java now if JAVA_HOME is set in env
env2 = dict(env)
env2['JAVA_HOME'] = r"D:\Android\Android Studio\jbr"
env2['PATH'] = env2['JAVA_HOME'] + r"\bin;" + env2.get('PATH','')
r2 = subprocess.run([r"where","java"], capture_output=True, text=True, env=env2, shell=True)
print("\nwhere java (with JAVA_HOME\\bin in PATH):")
print(r2.stdout)
print(r2.stderr)
