import re, os, sys

UI_ROOT = r"C:\Users\72952\OneDrive\Desktop\ui\CompanionChat\app\src\main\java\com\companion\chat\ui"

# 中文字符范围
CN = re.compile(r'[\u4e00-\u9fff]')

results = {}
for root, dirs, files in os.walk(UI_ROOT):
    for fn in files:
        if not fn.endswith('.kt'):
            continue
        path = os.path.join(root, fn)
        rel = os.path.relpath(path, UI_ROOT)
        try:
            with open(path, 'r', encoding='utf-8') as f:
                lines = f.readlines()
        except Exception:
            # 可能是 \u 转义形式
            with open(path, 'r', encoding='latin-1') as f:
                lines = f.readlines()
        hits = []
        for i, line in enumerate(lines, 1):
            # 跳过注释行
            s = line.lstrip()
            if s.startswith('//') or s.startswith('/*') or s.startswith('*'):
                continue
            # 跳过 logToFile 调试日志（开发者可见）
            if 'logToFile' in line or 'Log.' in line:
                continue
            if CN.search(line):
                hits.append((i, line.rstrip()))
        if hits:
            results[rel] = hits

for rel, hits in sorted(results.items()):
    print(f"=== {rel}  ({len(hits)} hits)")
    for i, line in hits[:5]:
        print(f"  L{i}: {line[:120]}")
    if len(hits) > 5:
        print(f"  ... and {len(hits)-5} more")
print()
print("SUMMARY:")
for rel, hits in sorted(results.items()):
    print(f"  {rel}: {len(hits)}")
