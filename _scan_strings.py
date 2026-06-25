import re
p = r"C:\Users\72952\OneDrive\Desktop\ui\CompanionChat\app\src\main\java\com\companion\chat\locale\Strings.kt"
with open(p, 'r', encoding='utf-8') as f:
    lines = f.readlines()
# find lines with unescaped " inside a "..." string literal for `to "..."` patterns
# pattern: `to "...."....."`  — i.e. a ` to "..."` where after closing quote there's non-quote garbage
issues = []
for i, line in enumerate(lines, 1):
    s = line.strip()
    if ' to ' not in s:
        continue
    # find ` to "` then match string literal
    m = re.search(r'\bto\s+"', s)
    if not m:
        # maybe ` to '...'` char literal (too long)
        m2 = re.search(r"\bto\s+'([^']{2,})'", s)
        if m2:
            issues.append((i, line.rstrip(), "char-literal-too-long"))
        continue
    start = m.end()  # position after opening "
    # walk to find closing unescaped "
    j = start
    while j < len(s):
        if s[j] == '\\':
            j += 2
            continue
        if s[j] == '"':
            break
        j += 1
    if j >= len(s):
        issues.append((i, line.rstrip(), "no-closing-quote"))
        continue
    rest = s[j+1:].strip()  # should be `,` or `, //comment` or end
    # if rest starts with non-comma non-comment, likely extra quote
    if rest and not rest.startswith(',') and not rest.startswith('//') and rest != '':
        # could be `, //` etc. — flag if it contains another `"` before comma
        # actually simpler: check if there's a `"` between opening and the closing we found that wasn't escaped
        # instead: count unescaped quotes
        seg = s[m.end():j]
        # fine
        pass
    # detect: there's a `"` in the segment that is followed by more text then another `"` — already handled by j walk
    # Detect char literal issue separately
print(f"Scanned {len(lines)} lines")
# Also: look for any ` to '...'` (single-quote string) which is invalid for multi-char
for i, line in enumerate(lines, 1):
    s = line.strip()
    m = re.search(r"\bto\s+'([^'\n]{2,})'", s)
    if m:
        issues.append((i, line.rstrip(), "single-quote-string"))
print(f"Found {len(issues)} potential issues:")
for i, line, kind in issues:
    print(f"  L{i} [{kind}]: {line[:160]}")
