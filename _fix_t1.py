path = r'C:\Users\72952\OneDrive\Desktop\ui\CompanionChat\app\src\main\java\com\companion\chat\AppContainer.kt'
with open(path, 'r', encoding='utf-8') as f:
    c = f.read()
c = c.replace('T1BatchProcessor(memoryRepository, memoryGraphRepository)', 'T1BatchProcessor(memoryRepository)')
with open(path, 'w', encoding='utf-8') as f:
    f.write(c)
print('Fixed')
