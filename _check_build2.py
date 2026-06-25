import re
p = r"C:\Users\72952\OneDrive\Desktop\ui\_build_real.txt"
with open(p, 'r', encoding='utf-8', errors='replace') as f:
    txt = f.read()
print("log size:", len(txt))
# find errors
errs = []
for m in re.finditer(r'e: file:.*?\.kt:\d+:.*', txt):
    errs.append(m.group(0)[:200])
for m in re.finditer(r'error:.*', txt):
    errs.append(m.group(0)[:200])
for kw in ['sectionLabel', 'BUILD SUCCESS', 'BUILD FAIL', 'FAILED', 'error:']:
    cnt = txt.lower().count(kw.lower())
    print(f"  '{kw}' count: {cnt}")
print("---first 5 errors---")
for e in errs[:5]:
    print(e)
print("---last 600 chars---")
print(txt[-600:])
