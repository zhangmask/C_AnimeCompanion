path = r'C:\Users\72952\OneDrive\Desktop\ui\CompanionChat\app\src\main\java\com\companion\chat\ui\memory\MemoryScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    c = f.read()

# Find the second when(selectedTab) - MemoryEditorDialog
idx1 = c.find('when (selectedTab)')
idx2 = c.find('when (selectedTab)', idx1 + 20)
if idx2 < 0:
    # Maybe there's only one
    idx2 = c.rfind('when (selectedTab)')

print(f'First occurrence: {idx1}')
print(f'Second occurrence: {idx2}')

if idx2 >= 0:
    # Show the content around it
    snippet = c[idx2:idx2+800]
    print(repr(snippet))
