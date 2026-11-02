sed -i '/val isDark =/d' /home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/analytics/AnalyticsScreen.kt

sed -i '/TopAppBar(/,/colors = TopAppBarDefaults.topAppBarColors/c\
            TopAppBar(\
                title = { Text("Financial Analytics") },\
                navigationIcon = {\
                    IconButton(onClick = onBack) {\
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")\
                    }\
                }\
            )' /home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/analytics/AnalyticsScreen.kt
