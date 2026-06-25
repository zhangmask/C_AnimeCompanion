import sys
filepath = sys.argv[1]
keyword = sys.argv[2]
with open(filepath, 'r', encoding='utf-8') as f:
    c = f.read()
if keyword in c:
    print(f"FOUND: '{keyword}' still present in {filepath}")
else:
    print(f"OK: '{keyword}' removed from {filepath}")
