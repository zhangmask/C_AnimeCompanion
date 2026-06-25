import subprocess, os
# Spawn a brand-new cmd process (it should read merged env from registry = system + user)
# subprocess with shell=True spawns via cmd, which inherits OUR env (parent), not registry-merged.
# To truly simulate a freshly opened terminal, we build env from registry merge.
import winreg
env = dict(os.environ)
with winreg.ConnectRegistry(None, winreg.HKEY_CURRENT_USER) as root:
    with winreg.OpenKey(root, r'Environment') as key:
        for name in ['JAVA_HOME','ANDROID_HOME','ANDROID_SDK_ROOT']:
            try:
                val, _ = winreg.QueryValueEx(key, name)
                env[name] = val
            except FileNotFoundError:
                pass
        try:
            user_path, _ = winreg.QueryValueEx(key, 'Path')
            # PATH = system PATH + user PATH (Windows merges these for new processes)
            sys_path = ''
            try:
                with winreg.ConnectRegistry(None, winreg.HKEY_LOCAL_MACHINE) as m:
                    with winreg.OpenKey(m, r'SYSTEM\CurrentControlSet\Control\Session Manager\Environment') as sk:
                        sys_path, _ = winreg.QueryValueEx(sk, 'Path')
            except Exception:
                pass
            env['PATH'] = sys_path + ';' + user_path
        except FileNotFoundError:
            pass

# Run verify
r = subprocess.run(
    r'cmd /c "echo JAVA_HOME=%JAVA_HOME% && echo ANDROID_HOME=%ANDROID_HOME% && echo ANDROID_SDK_ROOT=%ANDROID_SDK_ROOT% && echo --- && where java && java -version && echo --- && where adb && adb version"',
    capture_output=True, text=True, env=env, shell=False)
print("STDOUT:")
print(r.stdout)
print("STDERR:")
print(r.stderr)
print("EXIT:", r.returncode)
