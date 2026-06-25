s = open(r"C:\Users\72952\OneDrive\Desktop\ui\_chk_clean_out2.txt", encoding='utf-8', errors='replace').read()
for line in s.split('\n'):
    if 'BUILD' in line:
        print(line)
