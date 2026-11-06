# Module 06: Hardware & Advanced Mobile Capabilities

Welcome to Module 06! Building modern mobile applications often requires interacting directly with phone hardware and native operating system features.

In this module, we explore how SmartMoney integrates **On-Device Machine Learning (ML Kit OCR for receipts/invoices)**, **System Notifications**, and **Android Runtime Permissions**.

---

## 1. On-Device OCR with Google ML Kit

In SmartMoney, users can scan receipts or invoices using their phone camera and automatically parse the vendor name, total amount, and date.

### How it Works in [`InvoiceViewModel.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/ui/invoice/InvoiceViewModel.kt):
1. **Initialize the On-Device Recognizer**:
   ```kotlin
   private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
   ```
   Because processing happens on-device, it runs in milliseconds, works offline, and keeps sensitive financial data private.

2. **Bridging Google Tasks with Coroutines (`.await()`)**:
   Google Play Services traditionally uses callback-based `Task<T>` objects.
   By importing `kotlinx-coroutines-play-services`, we turn asynchronous callbacks into clean, sequential coroutine code:
   ```kotlin
   val image = InputImage.fromBitmap(bitmap, 0)
   // .await() suspends until ML Kit finishes, without blocking the thread!
   val visionText = recognizer.process(image).await()
   val fullText = visionText.text
   ```

3. **Kotlin Regex for Smart Text Extraction**:
   Using Kotlin's raw multiline string regex (`"""...""".toRegex()`):
   ```kotlin
   // Regex to capture amounts after keywords like Total:
   val amountRegex = """(?i)Total[^\d]*?([\d,\.]+)""".toRegex()
   val matchAmount = amountRegex.find(fullText)
   if (matchAmount != null) {
       val cleanAmount = matchAmount.groupValues[1].replace(",", "")
       _amount.value = cleanAmount
   }
   ```

---

## 2. System Notifications & Android 13+ Permissions

Delivering push or local notifications requires respecting Android OS restrictions and lifecycle policies.

See [`NotificationHelper.kt`](file:///home/frank/AndroidStudioProjects/smartmoney/app/src/main/java/com/example/smartmoney/core/notification/NotificationHelper.kt).

### 1. Notification Channels (Android 8.0 / API 26+)
All notifications on modern Android must belong to a **Notification Channel**, allowing users to customize sound, vibration, and importance per category in system settings:
```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    val importance = NotificationManager.IMPORTANCE_HIGH
    val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
        description = CHANNEL_DESC
        enableVibration(true)
    }
    notificationManager?.createNotificationChannel(channel)
}
```

### 2. Runtime Permissions (Android 13 / Tiramisu / API 33+)
Starting in Android 13, posting notifications requires the user's explicit consent at runtime:
```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    val hasPermission = ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
    if (!hasPermission) return // Gracefully skip if user denied permission
}
```

### 3. PendingIntents & Immutability
A `PendingIntent` is a token given to the Android system allowing it to execute code on your app's behalf when a user taps the notification banner:
```kotlin
val intent = Intent(context, MainActivity::class.java).apply {
    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
}
val pendingIntent = PendingIntent.getActivity(
    context,
    0,
    intent,
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
)
```
> [!IMPORTANT]
> Always specify `PendingIntent.FLAG_IMMUTABLE` on Android 12+ (API 31+) to prevent security vulnerabilities where malicious apps could tamper with the intent's payload.

---

## 3. Dual Persistence: Local In-App Feed + OS Notification

When a transaction alert occurs, SmartMoney performs a dual update:
```kotlin
// 1. Insert notification record into Room DB for the in-app notification center
notificationDao.insertNotification(entity)

// 2. Alert the user immediately in their system notification shade
postSystemNotification(context, title, message)
```
This guarantees that even if the user swipes away the status bar alert, the transaction record remains visible inside the in-app **Notifications** screen.

---

## 🏋️ Practice Exercises

1. **Regex Testing**:
   Write a Kotlin regex that matches phone numbers in standard format (e.g. `+254 712 345678` or `0712345678`).
2. **Notification Importance**:
   What happens if you change `NotificationManager.IMPORTANCE_HIGH` to `IMPORTANCE_LOW` in `NotificationHelper`? (Hint: check if sound or heads-up popups appear).
3. **Receipt Parsing Enhancement**:
   Look at `InvoiceViewModel.processImageForOcr()`. How could you improve the vendor name extraction beyond just taking the first line?
