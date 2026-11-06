sed -i 's/private fun FeaturePlaceholderScreen(/@OptIn(ExperimentalMaterial3Api::class)\nprivate fun FeaturePlaceholderScreen(/' /home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/dashboard/DashboardScreen.kt

sed -i '/fun FeaturePlaceholderScreen(/,/modifier = Modifier/c\
fun FeaturePlaceholderScreen(\
    screen: Screen,\
    onBack: () -> Unit = {}\
) {\
    val isDark = LocalDarkTheme.current\
    Scaffold(\
        topBar = {\
            androidx.compose.material3.TopAppBar(\
                title = {\
                    Row(verticalAlignment = Alignment.CenterVertically) {\
                        Text(\
                            text = screen.title,\
                            fontSize = 18.sp,\
                            fontWeight = FontWeight.Bold,\
                            color = if (isDark) Color.White else SmartMoneyColors.DarkSlateGreen\
                        )\
                    }\
                },\
                navigationIcon = {\
                    IconButton(onClick = onBack) {\
                        Icon(\
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,\
                            contentDescription = "Back",\
                            tint = if (isDark) Color.White else SmartMoneyColors.DarkSlateGreen\
                        )\
                    }\
                },\
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)\
            )\
        }\
    ) { paddingValues ->\
        Box(\
            modifier = Modifier\
                .fillMaxSize()\
                .padding(paddingValues)\
                .padding(24.dp),\
            contentAlignment = Alignment.Center\
        ) {\
            Card(\
                colors = CardDefaults.cardColors(\
                    containerColor = MaterialTheme.colorScheme.surface\
                ),\
                shape = RoundedCornerShape(16.dp),\
                modifier = Modifier\
' /home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/dashboard/DashboardScreen.kt

# Close the trailing bracket for scaffold that we added
sed -i 's/                }//g' /tmp/remove_dummy
