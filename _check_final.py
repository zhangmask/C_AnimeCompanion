import re
p = r"C:\Users\72952\OneDrive\Desktop\ui\_chk_final.txt"
with open(p, 'r', encoding='utf-8', errors='replace') as f:
    txt = f.read()
print("log size:", len(txt))
errs = re.findall(r'e: file:///[^\r\n]+', txt)
print(f"compile errors: {len(errs)}")
for e in errs[:20]:
    print(f"  {e[:250]}")
wrns = re.findall(r'w: file:///[^\r\n]+', txt)
print(f"warnings: {len(wrns)}")
for w in wrns[:20]:
    print(f"  {w[:250]}")
for kw in ['BUILD_SUCCESS', 'BUILD_FAIL', 'BUILD_EXIT']:
    m = re.search(rf'{kw}=(\d+)', txt)
    if m:
        print(f"{kw} = {m.group(1)}")
if 'BUILD_SUCCESS' not in txt and 'BUILD_FAIL' not in txt:
    print("---last 600 chars---")
    print(txt[-600:])
