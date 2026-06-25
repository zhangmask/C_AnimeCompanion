t = open(r'C:/Users/72952/OneDrive/Desktop/ui/多语言改造清单.md', encoding='utf-8').read()
print('lines:', t.count(chr(10)) + 1)
print('TODO count:', t.count('TODO'))
print('--- first 8 index lines ---')
for l in t.split(chr(10)):
    if l[:2] in ('1.','2.','3.','4.','5.','6.','7.','8.') and '. `' in l:
        print(l)
