import re
p = r"C:\Users\72952\OneDrive\Desktop\ui\CompanionChat\_build_full.txt"
with open(p, 'r', encoding='utf-8', errors='replace') as f:
    txt = f.read()
# look for errors and sectionLabel
for kw in ['sectionLabel', 'error:', 'e: file:', 'BUILD SUCCESS', 'BUILD FAIL', 'FAILED']:
    for m in re.finditer(re.escape(kw), txt, re.IGNORECASE):
        start = max(0, m.start()-80)
        end = min(len(txt), m.end()+150)
        print(f"[{kw}] ...{txt[start:end]}...")
        break
print("log size:", len(txt))
print("last 400 chars:")
print(txt[-400:])
