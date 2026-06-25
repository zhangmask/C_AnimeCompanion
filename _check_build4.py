import re
p = r"C:\Users\72952\OneDrive\Desktop\ui\_chk_out.txt"
with open(p, 'r', encoding='utf-8', errors='replace') as f:
    txt = f.read()
print("log size:", len(txt))
for kw in ['e: file:', 'BUILD_EXIT', 'BUILD SUCCESS', 'BUILD FAIL', 'FAILED']:
    cnt = txt.lower().count(kw.lower())
    print(f"  '{kw}' count: {cnt}")
errs = re.findall(r'e: file:///[^\r\n]+', txt)
print(f"--- {len(errs)} compile errors ---")
for e in errs[:25]:
    print(e[:240])
m = re.search(r'BUILD_EXIT=(\d+)', txt)
print("BUILD_EXIT =", m.group(1) if m else "?")
print("---last 300 chars---")
print(txt[-300:])
