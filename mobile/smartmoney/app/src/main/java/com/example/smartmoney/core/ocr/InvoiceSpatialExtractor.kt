package com.example.smartmoney.core.ocr

import com.google.mlkit.vision.text.Text
import java.util.Locale
import kotlin.math.abs

/**
 * Parsed entity results from invoice/receipt OCR.
 */
data class ExtractedInvoiceData(
    val vendor: String? = null,
    val invoiceNumber: String? = null,
    val amount: String? = null,
    val currency: String = "KES",
    val invoiceDate: String? = null,
    val dueDate: String? = null,
    val kraPin: String? = null,
    val rawText: String = "",
    val confidence: Map<String, Float> = emptyMap()
)

/**
 * Geometric bounding box representation for spatial grouping, decoupled from Android platform classes
 * to enable seamless JVM unit testing without stubbing errors.
 */
data class OcrBoundingBox(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Int get() = left + width / 2
    val centerY: Int get() = top + height / 2
}

/**
 * An individual OCR line with its text content and optional 2D spatial coordinate bounds.
 */
data class OcrLineItem(
    val text: String,
    val box: OcrBoundingBox? = null
)

/**
 * Advanced 2D Spatial OCR Extractor for invoices, receipts, and bills.
 *
 * Utilizes vertical coordinate clustering to reconstruct true horizontal rows
 * across disparate text blocks, pairing keys ("Total", "Invoice #") with their values
 * even when ML Kit flattens them into separate columns.
 */
object InvoiceSpatialExtractor {

    private val HEADER_BLACKLIST = setOf(
        "tax invoice", "tax receipt", "cash sale", "cash receipt", "invoice", "receipt",
        "original", "duplicate", "customer copy", "merchant copy", "client copy",
        "official receipt", "credit note", "debit note", "bill", "statement",
        "reprint", "proforma", "proforma invoice", "welcome", "welcome to",
        "thank you", "page", "order confirmation", "sales order", "delivery note"
    )

    private val METADATA_PREFIXES = listOf(
        "pin:", "pin :", "vat:", "vat :", "vat no", "tel:", "tel :", "phone:", "phone :",
        "mobile:", "fax:", "email:", "e-mail:", "website:", "web:", "www.", "http",
        "p.o. box", "po box", "p.o box", "box:", "cashier:", "till:", "till no",
        "served by:", "station:", "date:", "time:", "acc no:", "account:"
    )

    private val BUSINESS_KEYWORDS = listOf(
        "ltd", "limited", "inc", "corp", "llc", "plc", "co.", "company",
        "supermarket", "mart", "store", "stores", "shop", "enterprise", "enterprises",
        "ventures", "services", "solutions", "systems", "technologies", "agency",
        "supplies", "pharmacy", "chemist", "hospital", "clinic", "hotel", "restaurant",
        "cafe", "café", "bakery", "bar", "grill", "lounge", "station", "hardware",
        "garage", "petroleum", "holdings", "investments", "logistics", "motors",
        "distributors", "wholesalers", "bookshop"
    )

    private val ADDRESS_KEYWORDS = listOf(
        "road", "street", "avenue", "way", "highway", "building", "house", "plaza",
        "tower", "center", "centre", "floor", "suite", "room", "nairobi", "mombasa",
        "kisumu", "nakuru", "eldoret", "kenya"
    )

    // Priority 1: Explicit Grand Total labels
    private val GRAND_TOTAL_REGEX = """(?i)\b(?:grand\s+total|total\s+payable|balance\s+due|net\s+amount|amount\s+due|total\s+amount|to\s+pay|total\s+paid)\b""".toRegex()

    // Priority 2: Generic Total labels
    private val GENERIC_TOTAL_REGEX = """(?i)\b(?:total)\b""".toRegex()

    // Words that disqualify a line from being the Grand Total
    private val EXCLUDED_TOTAL_WORDS = listOf(
        "subtotal", "sub-total", "sub total", "tax", "vat", "discount", "change",
        "tendered", "tender", "cash", "mpesa", "m-pesa", "card", "items", "qty",
        "rounding", "savings", "points", "rate", "exempt"
    )

    // Amount extraction regex: matches numbers with optional commas and 2 decimals, optional currency symbol prefix
    private val AMOUNT_PATTERN = """(?:[Kk][Ee][Ss]|[Kk][Ss][Hh]s?|[$€£])?\s*([0-9]{1,3}(?:,[0-9]{3})+(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)""".toRegex()

    // Invoice Number keywords
    private val INVOICE_NO_KEYWORD_REGEX = """(?i)\b(?:tax\s+invoice\s+no\.?|invoice\s*no\.?|invoice\s*#|invoice\s*num(?:ber)?|inv\s*no\.?|inv\s*#|inv\s*num(?:ber)?|receipt\s*no\.?|receipt\s*#|receipt\s*num(?:ber)?|rec\s*no\.?|bill\s*no\.?|bill\s*#|order\s*no\.?|order\s*#|doc\s*no\.?)\b""".toRegex()

    // Date keywords
    private val DUE_DATE_KEYWORD_REGEX = """(?i)\b(?:due\s*date|payment\s*due|pay\s*by|terms\s*date|due\s*on|expiry\s*date)\b""".toRegex()
    private val INVOICE_DATE_KEYWORD_REGEX = """(?i)\b(?:invoice\s*date|bill\s*date|date\s*of\s*issue|issue\s*date|txn\s*date|trans\s*date|dated?)\b""".toRegex()

    // Supported date formats
    private val DATE_PATTERNS = listOf(
        """\b(\d{1,2}[-/\.]\d{1,2}[-/\.]\d{2,4})\b""".toRegex(),
        """\b(\d{4}[-/\.]\d{1,2}[-/\.]\d{1,2})\b""".toRegex(),
        """\b(\d{1,2}\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\s+\d{2,4})\b""".toRegex(RegexOption.IGNORE_CASE),
        """\b((?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\s+\d{1,2},?\s+\d{2,4})\b""".toRegex(RegexOption.IGNORE_CASE)
    )

    // Kenyan KRA PIN format: 1 letter, 9 digits, 1 letter
    private val KRA_PIN_REGEX = """\b([A-Z]\d{9}[A-Z])\b""".toRegex()

    /**
     * Primary entry point from ML Kit's Text object.
     */
    fun extract(visionText: Text): ExtractedInvoiceData {
        val lines = mutableListOf<OcrLineItem>()
        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val rect = line.boundingBox
                val box = if (rect != null) {
                    OcrBoundingBox(rect.left, rect.top, rect.right, rect.bottom)
                } else null
                lines.add(OcrLineItem(line.text.trim(), box))
            }
        }
        return extractFromLines(lines, fallbackRawText = visionText.text)
    }

    /**
     * Testable core method that operates directly on OcrLineItem lists.
     */
    fun extractFromLines(lines: List<OcrLineItem>, fallbackRawText: String = ""): ExtractedInvoiceData {
        val nonBlankLines = if (lines.isNotEmpty()) {
            lines.filter { it.text.isNotBlank() }
        } else {
            fallbackRawText.lines().filter { it.isNotBlank() }.map { OcrLineItem(it) }
        }

        val rawFullText = if (fallbackRawText.isNotBlank()) {
            fallbackRawText
        } else {
            nonBlankLines.joinToString("\n") { it.text }
        }

        if (nonBlankLines.isEmpty()) {
            return ExtractedInvoiceData(rawText = rawFullText)
        }

        // 1. Group lines into 2D horizontal rows based on vertical alignment
        val rows = groupLinesIntoRows(nonBlankLines)

        // 2. Metadata: KRA PIN & Currency
        val kraPin = findKraPin(rawFullText)
        val currency = detectCurrency(rawFullText)

        // 3. Dates: Invoice Date & Due Date
        val (invoiceDate, dueDate) = extractDates(rows, nonBlankLines)

        // 4. Financial: Grand Total / Total Amount
        val amount = extractAmount(rows)

        // 5. Document identifier: Invoice / Receipt Number
        val invoiceNumber = extractInvoiceNumber(rows, nonBlankLines)

        // 6. Business Entity: Vendor
        val vendor = extractVendor(rows, nonBlankLines)

        val confidence = mutableMapOf<String, Float>()
        if (vendor != null) confidence["vendor"] = 0.9f
        if (amount != null) confidence["amount"] = 0.95f
        if (invoiceNumber != null) confidence["invoiceNumber"] = 0.85f
        if (invoiceDate != null) confidence["invoiceDate"] = 0.9f
        if (dueDate != null) confidence["dueDate"] = 0.85f

        return ExtractedInvoiceData(
            vendor = vendor,
            invoiceNumber = invoiceNumber,
            amount = amount,
            currency = currency,
            invoiceDate = invoiceDate,
            dueDate = dueDate,
            kraPin = kraPin,
            rawText = rawFullText,
            confidence = confidence
        )
    }

    /**
     * Groups OCR lines into horizontal visual rows by testing vertical coordinate overlap.
     */
    fun groupLinesIntoRows(lines: List<OcrLineItem>): List<List<OcrLineItem>> {
        val nonBlank = lines.filter { it.text.isNotBlank() }
        if (nonBlank.isEmpty()) return emptyList()

        val withBox = nonBlank.filter { it.box != null }
        val withoutBox = nonBlank.filter { it.box == null }

        if (withBox.isEmpty()) {
            return withoutBox.map { listOf(it) }
        }

        // Sort primarily by vertical position, then left
        val sortedWithBox = withBox.sortedWith(
            compareBy({ it.box!!.centerY }, { it.box!!.left })
        )

        val rows = mutableListOf<MutableList<OcrLineItem>>()

        for (line in sortedWithBox) {
            val box = line.box!!
            // Find an existing row where center Y matches within tolerance
            val matchingRow = rows.find { row ->
                val rowAvgY = row.mapNotNull { it.box?.centerY }.average()
                val rowAvgH = row.mapNotNull { it.box?.height }.average().takeIf { it > 0 } ?: 30.0
                val tolerance = rowAvgH * 0.55
                abs(box.centerY - rowAvgY) <= tolerance
            }

            if (matchingRow != null) {
                matchingRow.add(line)
            } else {
                rows.add(mutableListOf(line))
            }
        }

        // Sort items inside each row left to right
        for (row in rows) {
            row.sortBy { it.box?.left ?: 0 }
        }

        // Sort rows top to bottom
        rows.sortBy { row -> row.firstOrNull()?.box?.centerY ?: 0 }

        val result = rows.map { it.toList() }.toMutableList()
        for (item in withoutBox) {
            result.add(listOf(item))
        }

        return result
    }

    private fun findKraPin(text: String): String? {
        return KRA_PIN_REGEX.find(text)?.groupValues?.get(1)
    }

    private fun detectCurrency(text: String): String {
        return when {
            text.contains("""\b(USD)\b|\$""".toRegex(RegexOption.IGNORE_CASE)) -> "USD"
            text.contains("""\b(EUR)\b|€""".toRegex(RegexOption.IGNORE_CASE)) -> "EUR"
            text.contains("""\b(GBP)\b|£""".toRegex(RegexOption.IGNORE_CASE)) -> "GBP"
            else -> "KES"
        }
    }

    private fun extractDates(
        rows: List<List<OcrLineItem>>,
        lines: List<OcrLineItem>
    ): Pair<String?, String?> {
        var foundInvoiceDate: String? = null
        var foundDueDate: String? = null

        val rowTexts = rows.map { row -> row.joinToString(" ") { it.text } }

        // First pass: scan for explicit keywords
        for (i in rowTexts.indices) {
            val rowText = rowTexts[i]

            // Check Due Date
            if (foundDueDate == null && DUE_DATE_KEYWORD_REGEX.containsMatchIn(rowText)) {
                val d = findDateInString(rowText) ?: (if (i + 1 < rowTexts.size) findDateInString(rowTexts[i + 1]) else null)
                if (d != null) foundDueDate = d
            }

            // Check Invoice Date
            if (foundInvoiceDate == null && INVOICE_DATE_KEYWORD_REGEX.containsMatchIn(rowText)) {
                val d = findDateInString(rowText) ?: (if (i + 1 < rowTexts.size) findDateInString(rowTexts[i + 1]) else null)
                if (d != null) foundInvoiceDate = d
            }
        }

        // Second pass: if invoice date is still null, search first 15 lines for any valid date that is not due date
        if (foundInvoiceDate == null) {
            for (line in lines.take(15)) {
                val d = findDateInString(line.text)
                if (d != null && d != foundDueDate) {
                    foundInvoiceDate = d
                    break
                }
            }
        }

        return Pair(foundInvoiceDate, foundDueDate)
    }

    private fun findDateInString(text: String): String? {
        for (pattern in DATE_PATTERNS) {
            val match = pattern.find(text)
            if (match != null) {
                return match.groupValues[1].trim()
            }
        }
        return null
    }

    private fun extractAmount(rows: List<List<OcrLineItem>>): String? {
        val rowTexts = rows.map { row -> row.joinToString(" ") { it.text } }

        var candidateGrandTotal: String? = null
        var candidateGenericTotal: String? = null

        for (i in rowTexts.indices) {
            val rowText = rowTexts[i]
            val lower = rowText.lowercase(Locale.ROOT)

            // Check Priority 1: Grand Total
            if (GRAND_TOTAL_REGEX.containsMatchIn(rowText)) {
                val amt = extractRowNumber(rowText) ?: (if (i + 1 < rowTexts.size) extractRowNumber(rowTexts[i + 1]) else null)
                if (amt != null) {
                    candidateGrandTotal = amt
                    break // Priority 1 found, stop search
                }
            }

            // Check Priority 2: Generic Total (must not contain excluded words like Subtotal, Tax, Change, Cash)
            if (GENERIC_TOTAL_REGEX.containsMatchIn(rowText)) {
                val hasExcludedWord = EXCLUDED_TOTAL_WORDS.any { lower.contains(it) }
                if (!hasExcludedWord) {
                    val amt = extractRowNumber(rowText) ?: (if (i + 1 < rowTexts.size) extractRowNumber(rowTexts[i + 1]) else null)
                    if (amt != null) {
                        candidateGenericTotal = amt
                    }
                }
            }
        }

        return candidateGrandTotal ?: candidateGenericTotal
    }

    private fun extractRowNumber(text: String): String? {
        val matches = AMOUNT_PATTERN.findAll(text).toList()
        if (matches.isEmpty()) return null

        // Take the last number in the row (typically totals are positioned rightmost)
        for (m in matches.reversed()) {
            val numStr = m.groupValues[1].replace(",", "").trim()
            val numVal = numStr.toDoubleOrNull()
            if (numVal != null && numVal > 0.0) {
                return numStr
            }
        }
        return null
    }

    private fun extractInvoiceNumber(
        rows: List<List<OcrLineItem>>,
        lines: List<OcrLineItem>
    ): String? {
        val rowTexts = rows.map { row -> row.joinToString(" ") { it.text } }

        for (i in rowTexts.indices) {
            val rowText = rowTexts[i]
            val match = INVOICE_NO_KEYWORD_REGEX.find(rowText)
            if (match != null) {
                // Look after the keyword on the same row
                val afterKeyword = rowText.substring(match.range.last + 1).trim()
                val candidate = cleanInvoiceNumberToken(afterKeyword)
                if (candidate != null) {
                    return candidate
                }

                // If nothing after keyword on this row, check the next row
                if (i + 1 < rowTexts.size) {
                    val nextCandidate = cleanInvoiceNumberToken(rowTexts[i + 1].trim())
                    if (nextCandidate != null) {
                        return nextCandidate
                    }
                }
            }
        }

        // Secondary pass: look for standalone pattern like INV-1234 or #12345 in top 15 lines
        val standalonePattern = """\b((?:INV|REC|BILL)[-_#]?[A-Z0-9]{3,15})\b""".toRegex(RegexOption.IGNORE_CASE)
        for (line in lines.take(15)) {
            val m = standalonePattern.find(line.text)
            if (m != null) {
                return m.groupValues[1]
            }
        }

        return null
    }

    private fun cleanInvoiceNumberToken(raw: String): String? {
        // Strip leading punctuation: colons, hashes, dashes
        val stripped = raw.trimStart(':', '#', '-', ' ', '\t')
        val firstToken = stripped.split("""\s+""".toRegex()).firstOrNull()?.trim() ?: return null

        // Validate token
        if (firstToken.length < 2 || firstToken.length > 35) return null
        if (firstToken.equals("NO", ignoreCase = true) || firstToken.equals("DATE", ignoreCase = true)) return null

        // Must not be a pure date
        if (DATE_PATTERNS.any { it.matches(firstToken) }) return null

        // Must contain at least one digit or hyphen
        if (firstToken.any { it.isDigit() || it == '-' || it == '/' }) {
            return firstToken
        }

        return null
    }

    private fun extractVendor(
        rows: List<List<OcrLineItem>>,
        lines: List<OcrLineItem>
    ): String? {
        // Evaluate candidate lines in top 40% of the document (up to first 10 lines)
        val candidateLines = lines.take(10).filter { it.text.isNotBlank() }

        var bestCandidate: String? = null
        var highestScore = -100

        for ((index, item) in candidateLines.withIndex()) {
            val text = item.text.trim()
            val lower = text.lowercase(Locale.ROOT)

            // Skip lines that match document header blacklist
            if (HEADER_BLACKLIST.any { lower == it || lower.startsWith("$it ") || lower.endsWith(" $it") }) {
                continue
            }

            // Skip lines with metadata prefixes (PIN, TEL, PO BOX, etc.)
            if (METADATA_PREFIXES.any { lower.startsWith(it) }) {
                continue
            }

            // Skip if line is just a date, phone number, or KRA PIN
            if (findDateInString(text) != null && text.length <= 15) continue
            if (KRA_PIN_REGEX.matches(text)) continue
            if (text.matches("""^\+?[0-9\s\-\(\)]{8,}$""".toRegex())) continue
            if (text.length < 3) continue

            // Scoring system
            var score = 100 - (index * 12) // Earlier lines get higher base score

            // Bonus for larger font (box height)
            item.box?.height?.let { h ->
                score += (h / 2).coerceIn(0, 30)
            }

            // Bonus for business identifiers (LTD, Supermarket, Cafe, etc.)
            if (BUSINESS_KEYWORDS.any { lower.contains(it) }) {
                score += 45
            }

            // Penalty for physical address words (Road, Avenue, Building, Floor)
            if (ADDRESS_KEYWORDS.any { lower.contains(it) }) {
                score -= 35
            }

            // Length preference: 4 to 35 chars
            if (text.length in 4..35) {
                score += 15
            }

            // Letters ratio
            val lettersCount = text.count { it.isLetter() }
            if (lettersCount.toFloat() / text.length > 0.7f) {
                score += 15
            }

            if (score > highestScore) {
                highestScore = score
                bestCandidate = text
            }
        }

        if (highestScore >= 35 && bestCandidate != null) {
            return bestCandidate
        }

        // Fallback: first non-empty line that isn't blacklisted and isn't metadata
        return candidateLines.firstOrNull { item ->
            val text = item.text.trim()
            val lower = text.lowercase(Locale.ROOT)
            !HEADER_BLACKLIST.contains(lower) &&
                    !METADATA_PREFIXES.any { lower.startsWith(it) } &&
                    text.length >= 3 &&
                    findDateInString(text) == null &&
                    !KRA_PIN_REGEX.matches(text)
        }?.text?.trim()
    }
}
