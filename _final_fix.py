import os, subprocess

# 1. Fix ChatViewModel.kt - add import
path1 = r'C:\Users\72952\OneDrive\Desktop\ui\CompanionChat\app\src\main\java\com\companion\chat\ui\chat\ChatViewModel.kt'
with open(path1, 'r', encoding='utf-8', errors='ignore') as f:
    lines = f.readlines()
insert_idx = next(i for i, l in enumerate(lines) if 'PreferenceLearningAdapter' in l) + 1
lines.insert(insert_idx, 'import com.companion.chat.data.memory.MemoryExtractLoop\n')
with open(path1, 'w', encoding='utf-8') as f:
    f.writelines(lines)
print('1. Added MemoryExtractLoop import')

# 2. Fix MemoryScreen.kt - add else branch
path2 = r'C:\Users\72952\OneDrive\Desktop\ui\CompanionChat\app\src\main\java\com\companion\chat\ui\memory\MemoryScreen.kt'
with open(path2, 'r', encoding='utf-8', errors='ignore') as f:
    c = f.read()
c = c.replace(
    '        MemoryFilter.OTHER -> Strings.get(lang, StringsKey.memory_cat_other)\n    }',
    '        MemoryFilter.OTHER -> Strings.get(lang, StringsKey.memory_cat_other)\n        else -> ""\n    }', 1)
with open(path2, 'w', encoding='utf-8') as f:
    f.write(c)
print('2. Added else branch to when')

# 3. Build
os.environ['JAVA_HOME'] = r'D:\Android\Android Studio\jbr'
os.chdir(r'C:\Users\72952\OneDrive\Desktop\ui\CompanionChat')
r = subprocess.run(['gradlew.bat', 'compileDebugKotlin', '--no-daemon'], capture_output=True, text=True, shell=True)
out = (r.stdout or '') + (r.stderr or '')
if 'BUILD SUCCESSFUL' in out:
    print('\n*** BUILD SUCCESSFUL ***')
else:
    for l in out.splitlines():
        if '.kt:' in l and 'error' in l.lower():
            print(l)
print('RC:', r.returncode)
