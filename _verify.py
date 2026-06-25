import os, re

root = r'CompanionChat/app/src/main/java/com/companion/chat/ui'
count = 0
files_with_cn = []

for dirpath, _, fnames in os.walk(root):
    for fn in fnames:
        if not fn.endswith('.kt'):
            continue
        fp = os.path.join(dirpath, fn)
        with open(fp, 'r', encoding='utf-8') as f:
            content = f.read()
        
        # Find literal strings containing Chinese characters (within quotes)
        matches = re.findall(r'"[^"]*[\u4e00-\u9fff][^"]*"', content)
        
        # Filter out strings that are not UI-facing (user data, etc.)
        real_matches = []
        for m in matches:
            # Skip strings that contain only delimiters or are user-content references
            if '$' in m or '\\u' in m:
                continue
            # Skip if it's a code pattern like split(",", "，")
            if len(m) < 6:  # too short to be a meaningful UI string
                continue
            real_matches.append(m)
        
        if real_matches:
            files_with_cn.append((fp, len(real_matches), real_matches[:5]))
            count += len(real_matches)

if files_with_cn:
    print(f"FOUND {count} remaining Chinese strings in {len(files_with_cn)} files:")
    for fp, n, samples in sorted(files_with_cn, key=lambda x: -x[1]):
        print(f"\n  {os.path.basename(fp)} ({n}):")
        for s in samples:
            print(f"    {s}")
else:
    print("ALL CLEAN: No hardcoded Chinese UI strings found!")
