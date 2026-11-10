package com.example.smartmoney.core.sms.service

import android.app.Notification
import android.service.notification.StatusBarNotification
import java.util.Locale

/**
 * Extracts and prepares potential financial SMS text from incoming [StatusBarNotification] objects.
 */
object NotificationContentExtractor {

    private val KNOWN_SMS_PACKAGES = setOf(
        "com.google.android.apps.messaging", // Google Messages
        "com.samsung.android.messaging",     // Samsung Messages
        "com.android.mms",                   // Default Android AOSP MMS
        "com.safaricom.mpesa.lifestyle",     // M-PESA App
        "com.safaricom.mpesa.consumer",      // MySafaricom App
        "com.truecaller"                     // Truecaller SMS
    )

    /**
     * Determines whether the notification might represent a financial transaction notification (M-PESA, KCB, or NCBA).
     * Performs fast pre-filtering before running full regex parsing.
     */
    fun isCandidateNotification(sbn: StatusBarNotification): Boolean {
        val packageName = sbn.packageName.lowercase(Locale.ROOT)
        val extras = sbn.notification?.extras ?: return false

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()

        val combined = "$title $text $bigText".uppercase(Locale.ROOT)

        // 1. Fast check: Discard obvious non-financial security messages (OTP, activation code)
        if (combined.contains("ACTIVATION CODE") || combined.contains("VALID FOR ONLY") || combined.contains("KAA CHONJO")) {
            return false
        }

        // 2. M-PESA / M-Shwari indicator check
        val hasMpesaIndicator = combined.contains("MPESA") ||
                combined.contains("M-PESA") ||
                combined.contains("SAFARICOM") ||
                combined.contains("MSHWARI") ||
                combined.contains("M-SHWARI")
        val hasConfirmedKeyword = combined.contains("CONFIRMED")

        if (hasMpesaIndicator || hasConfirmedKeyword) {
            return true
        }

        // 3. Bank indicators (KCB & NCBA)
        val hasBankIndicator = combined.contains("KCB") ||
                combined.contains("NCBA") ||
                combined.contains("MBN") ||
                title.contains("522") ||
                combined.contains("PAY TO M-PESA BILLER")

        val hasBankAction = combined.contains("DEBITED") ||
                combined.contains("CREDITED") ||
                combined.contains("SENT FROM") ||
                combined.contains("RECEIVED IN") ||
                combined.contains("HAS BEEN RECEIVED") ||
                combined.contains("HAS BEEN PROCESSED")

        val hasMoneyIndicator = combined.contains("KSH") || combined.contains("KES")
        val isKnownSmsApp = KNOWN_SMS_PACKAGES.contains(packageName)

        if ((hasBankIndicator || isKnownSmsApp) && hasMoneyIndicator && hasBankAction) {
            return true
        }

        // 4. Fallback for generic financial confirmation from known SMS app
        return isKnownSmsApp && hasMoneyIndicator && hasConfirmedKeyword
    }

    /**
     * Extracts all candidate message text strings from the notification.
     * Returns a list of candidate strings to be evaluated by the parser.
     */
    fun extractCandidateTexts(sbn: StatusBarNotification): List<String> {
        val extras = sbn.notification?.extras ?: return emptyList()
        val candidates = mutableListOf<String>()

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()

        // 1. BigText is usually the full expanded SMS body in Google Messages / Samsung Messages
        if (bigText.isNotBlank()) {
            candidates.add(bigText)
        }

        // 2. Standard Text
        if (text.isNotBlank() && text != bigText) {
            candidates.add(text)
        }

        // 3. Combined Title + Text / BigText in case the receipt reference was in the title
        if (title.isNotBlank()) {
            if (bigText.isNotBlank()) {
                candidates.add("$title $bigText")
            }
            if (text.isNotBlank() && text != bigText) {
                candidates.add("$title $text")
            }
        }

        // 4. Lines from InboxStyle if present
        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        if (textLines != null) {
            for (line in textLines) {
                val lineStr = line?.toString()?.trim().orEmpty()
                if (lineStr.isNotBlank()) {
                    candidates.add(lineStr)
                    if (title.isNotBlank()) {
                        candidates.add("$title $lineStr")
                    }
                }
            }
        }

        return candidates.distinct()
    }
}
