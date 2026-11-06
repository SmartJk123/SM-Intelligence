import re

with open('/home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/menu/MenuScreen.kt', 'r') as f:
    content = f.read()

# Match from the start of "3. ACCOUNT Group" up to (but not including) "4. User Profile"
pattern = re.compile(
    r'        // 3\. ACCOUNT Group\n'
    r'.*?'
    r'(        // 4\. User Profile)',
    re.DOTALL
)

content = pattern.sub(r'\1', content)

with open('/home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/menu/MenuScreen.kt', 'w') as f:
    f.write(content)
