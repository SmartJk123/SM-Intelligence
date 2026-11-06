import re

with open('/home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/components/MainResponsiveShell.kt', 'r') as f:
    content = f.read()

pattern = r'if \(!isBottomBarScreen && currentRoute != Screen\.Notifications\.route && currentRoute != Screen\.Analytics\.route\) \{'

replacement = """val noTopBarRoutes = setOf(
                        Screen.Notifications.route,
                        Screen.Analytics.route,
                        Screen.Budgets.route,
                        Screen.CashFlow.route,
                        Screen.Reports.route,
                        Screen.Investments.route
                    )
                    if (!isBottomBarScreen && currentRoute not in noTopBarRoutes) {"""

content = re.sub(pattern, replacement, content, count=1)

with open('/home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/components/MainResponsiveShell.kt', 'w') as f:
    f.write(content)
