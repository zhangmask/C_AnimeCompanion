import re
p = r"C:\Users\72952\OneDrive\Desktop\ui\_build_apk.txt"
with open(p, 'r', encoding='utf-8', errors='replace') as f:
    txt = f.read()
print("log size:", len(txt))
for kw in ['e: file:', 'BUILD SUCCESS', 'BUILD FAIL', 'FAILED', 'error:']:
    cnt = txt.lower().count(kw.lower())
    print(f"  '{kw}' count: {cnt}")
errs = re.findall(r'e: file:///[^\r\n]+', txt)
print(f"--- {len(errs)} compile errors ---")
for e in errs[:10]:
    print(e[:240])
print("---last 400 chars---")
print(txt[-400:])
