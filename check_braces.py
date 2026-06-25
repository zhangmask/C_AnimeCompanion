import re
t = open('C:/Users/72952/OneDrive/Desktop/ui/CompanionChat/app/src/main/java/com/companion/chat/ui/chat/components/ConversationDrawerSheet.kt', encoding='utf-8').read()
lines = t.split('\n')
# Find footer block start (line with '// Footer as LAST LazyColumn item')
footer_start = None
for i, l in enumerate(lines):
    if 'Footer as LAST LazyColumn item' in l:
        footer_start = i
        break
print('footer_start line:', footer_start+1 if footer_start else None)
# Find footer block end - it's the '                    }' that closes the item {} lambda
# The item {} opens at line with 'item {'. Track depth from there to find matching close.
item_open = None
for i in range(footer_start, len(lines)):
    if 'item {' in lines[i]:
        item_open = i
        break
print('item_open line:', item_open+1)
depth = 0
item_close = None
for i in range(item_open, len(lines)):
    line = lines[i]
    opens = line.count('{')
    closes = line.count('}')
    depth += opens - closes
    if depth == 0 and i > item_open:
        item_close = i
        break
print('item_close line:', item_close+1, repr(lines[item_close][:40]))
# Find itemsIndexed lambda close - it's the '                    }' before footer_start
# Actually footer was inserted INSIDE itemsIndexed lambda. The itemsIndexed lambda body is:
# { index, session -> ... } — its close is the '                    }' that was originally closing it.
# After insertion, the itemsIndexed lambda close is now AFTER the footer item (wrong).
# We need: extract footer block (footer_start to item_close), delete it from current position,
# and insert it AFTER the itemsIndexed lambda close (which is the '                    }' followed by '                }' LazyColumn close).
# Find LazyColumn body close - it's '                }' after itemsIndexed lambda close.
# After deletion of footer, itemsIndexed lambda close will be followed by LazyColumn body close.
# Strategy: 
# 1. Extract footer block text (lines[footer_start:item_close+1])
# 2. Delete those lines from current position
# 3. Find itemsIndexed lambda close in the reduced text and insert footer after it (before LazyColumn body close)
footer_block = '\n'.join(lines[footer_start:item_close+1])
print('footer block lines:', item_close - footer_start + 1)
# Delete footer block
reduced = lines[:footer_start] + lines[item_close+1:]
# Now find itemsIndexed lambda close. itemsIndexed opens with 'itemsIndexed(' then '{ index, session ->'
# Its lambda close is the '                    }' that matches. Then '                }' is LazyColumn body close.
# Find LazyColumn body close (the '                }' after itemsIndexed lambda close)
# LazyColumn body opened at '                ) {' — find its close by tracking depth from LazyColumn(
lazy_open = None
for i, l in enumerate(reduced):
    if 'LazyColumn(' in l:
        lazy_open = i
        break
depth = 0
in_body = False
lc_body_close = None
for i in range(lazy_open, len(reduced)):
    line = reduced[i]
    opens = line.count('{')
    closes = line.count('}')
    if opens > 0 and not in_body:
        in_body = True
    depth += opens - closes
    if in_body and depth == 0:
        lc_body_close = i
        break
print('LazyColumn body close (reduced) line:', lc_body_close+1, repr(reduced[lc_body_close][:40]))
print('Line before:', repr(reduced[lc_body_close-1][:40]))
# Insert footer block before LazyColumn body close (lc_body_close)
# Indent footer to match LazyColumn body indentation (20 spaces, like itemsIndexed)
new_lines = reduced[:lc_body_close] + [footer_block] + reduced[lc_body_close:]
new_t = '\n'.join(new_lines)
open('C:/Users/72952/OneDrive/Desktop/ui/CompanionChat/app/src/main/java/com/companion/chat/ui/chat/components/ConversationDrawerSheet.kt', 'w', encoding='utf-8').write(new_t)
print('OK. New total lines:', len(new_lines))
