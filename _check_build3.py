import re
p = r"C:\Users\72952\OneDrive\Desktop\ui\_chk_out.txt"
with open(p, 'r', encoding='utf-8', errors='replace') as f:
    txt = f.read()
print("log size:", len(txt))
for kw in ['sectionLabel', 'BUILD SUCCESS', 'BUILD FAIL', 'FAILED', 'e: file:', 'BUILD_EXIT']:
    cnt = txt.lower().count(kw.lower())
    print(f"  '{kw}' count: {cnt}")
errs = re.findall(r'e: file:.*?\.kt:\d+:[^\r\n]*', txt)
print(f"--- {len(errs)} compile errors ---")
for e in errs[:20]:
    print(e[:220])
print("---last 500 chars---")
print(txt[-500:])
