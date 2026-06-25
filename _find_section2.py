import os, re

ROOT = r"C:\Users\72952\OneDrive\Desktop\ui\CompanionChat\app\src\main\java"
pat = re.compile(r'\bsectionLabel\b')
for root, dirs, files in os.walk(ROOT):
    for fn in files:
        if not fn.endswith('.kt'):
            continue
        path = os.path.join(root, fn)
        try:
            with open(path, 'r', encoding='utf-8') as f:
                lines = f.readlines()
        except Exception:
            with open(path, 'r', encoding='latin-1') as f:
                lines = f.readlines()
        for i, line in enumerate(lines, 1):
            if pat.search(line):
                rel = os.path.relpath(path, ROOT)
                print(f"{rel}:{i}: {line.rstrip()[:140]}")
