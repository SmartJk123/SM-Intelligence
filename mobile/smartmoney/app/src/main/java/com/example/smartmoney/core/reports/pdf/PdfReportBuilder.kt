package com.example.smartmoney.core.reports.pdf

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.smartmoney.core.reports.model.ReportSummaryData
import com.example.smartmoney.core.reports.model.StatementRowData
import java.io.OutputStream
import java.math.BigDecimal
import java.text.DecimalFormat

/**
 * Offline native PDF generator creating formatted, printable A4 financial statements
 * using Android's built-in [android.graphics.pdf.PdfDocument] and vector Canvas.
 */
object PdfReportBuilder {

    private const val PAGE_WIDTH = 595 // A4 standard width in points
    private const val PAGE_HEIGHT = 842 // A4 standard height in points
    private const val MARGIN = 36f // 0.5 inch margins
    private const val BOTTOM_LIMIT = 790f

    private val currencyFormat = DecimalFormat("#,##0.00")

    fun build(data: ReportSummaryData, outputStream: OutputStream) {
        val pdfDocument = PdfDocument()
        var pageNumber = 1

        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        var currentY = MARGIN

        // 1. Draw First Page Header & Summary Card
        currentY = drawReportHeader(canvas, data, currentY)
        currentY = drawSummaryMetrics(canvas, data, currentY)

        // 2. Table Column Header
        currentY = drawTableHeader(canvas, currentY)

        // 3. Draw Rows with Automatic Multi-Page Pagination
        val rowHeight = 20f

        for (row in data.rows) {
            if (currentY + rowHeight > BOTTOM_LIMIT) {
                drawPageFooter(canvas, pageNumber)
                pdfDocument.finishPage(page)

                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas

                currentY = MARGIN + 10f
                currentY = drawRunningHeader(canvas, data, currentY)
                currentY = drawTableHeader(canvas, currentY)
            }

            drawTableRow(canvas, row, currentY)
            currentY += rowHeight
        }

        // Final page footer
        drawPageFooter(canvas, pageNumber)
        pdfDocument.finishPage(page)

        pdfDocument.writeTo(outputStream)
        pdfDocument.close()
    }

    private fun drawReportHeader(canvas: Canvas, data: ReportSummaryData, startY: Float): Float {
        var y = startY

        val brandPaint = Paint().apply {
            color = Color.parseColor("#0F172A") // Deep slate
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val titlePaint = Paint().apply {
            color = Color.parseColor("#1E293B")
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val metaPaint = Paint().apply {
            color = Color.parseColor("#64748B") // Cool gray
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        canvas.drawText("SMARTMONEY INTELLIGENCE", MARGIN, y + 14f, brandPaint)
        y += 18f

        canvas.drawText(data.title.uppercase(), MARGIN, y + 12f, titlePaint)
        y += 16f

        canvas.drawText("Account: ${data.accountName}   •   Period: ${data.periodLabel}", MARGIN, y + 10f, metaPaint)
        y += 12f

        val userInfo = buildString {
            append("Generated for: ").append(data.userName)
            if (!data.userEmail.isNullOrBlank()) append(" (").append(data.userEmail).append(")")
            append("   •   Date: ").append(data.generatedAt)
        }
        canvas.drawText(userInfo, MARGIN, y + 9f, metaPaint)
        y += 18f

        // Separator line
        val linePaint = Paint().apply {
            color = Color.parseColor("#E2E8F0")
            strokeWidth = 1f
        }
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
        y += 12f

        return y
    }

    private fun drawSummaryMetrics(canvas: Canvas, data: ReportSummaryData, startY: Float): Float {
        val y = startY
        val cardHeight = 44f
        val cardWidth = PAGE_WIDTH - (MARGIN * 2)

        // Card background
        val bgPaint = Paint().apply {
            color = Color.parseColor("#F8FAFC")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val borderPaint = Paint().apply {
            color = Color.parseColor("#CBD5E1")
            style = Paint.Style.STROKE
            strokeWidth = 1f
            isAntiAlias = true
        }
        val rect = RectF(MARGIN, y, MARGIN + cardWidth, y + cardHeight)
        canvas.drawRoundRect(rect, 6f, 6f, bgPaint)
        canvas.drawRoundRect(rect, 6f, 6f, borderPaint)

        val colWidth = cardWidth / 4f

        val labelPaint = Paint().apply {
            color = Color.parseColor("#64748B")
            textSize = 7.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        val valuePaint = Paint().apply {
            color = Color.parseColor("#0F172A")
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val greenValuePaint = Paint().apply {
            color = Color.parseColor("#16A34A") // Success Green
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        // Col 1: Opening
        canvas.drawText("OPENING BALANCE", MARGIN + 8f, y + 16f, labelPaint)
        canvas.drawText("KES ${currencyFormat.format(data.openingBalance)}", MARGIN + 8f, y + 32f, valuePaint)

        // Col 2: Inflow
        canvas.drawText("TOTAL INFLOW (+)", MARGIN + colWidth + 8f, y + 16f, labelPaint)
        canvas.drawText("KES ${currencyFormat.format(data.totalInflow)}", MARGIN + colWidth + 8f, y + 32f, greenValuePaint)

        // Col 3: Outflow
        canvas.drawText("TOTAL OUTFLOW (-)", MARGIN + (colWidth * 2) + 8f, y + 16f, labelPaint)
        canvas.drawText("KES ${currencyFormat.format(data.totalOutflow)}", MARGIN + (colWidth * 2) + 8f, y + 32f, valuePaint)

        // Col 4: Closing
        canvas.drawText("CLOSING BALANCE", MARGIN + (colWidth * 3) + 8f, y + 16f, labelPaint)
        canvas.drawText("KES ${currencyFormat.format(data.closingBalance)}", MARGIN + (colWidth * 3) + 8f, y + 32f, valuePaint)

        return y + cardHeight + 14f
    }

    private fun drawRunningHeader(canvas: Canvas, data: ReportSummaryData, startY: Float): Float {
        var y = startY
        val paint = Paint().apply {
            color = Color.parseColor("#64748B")
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("SMARTMONEY STATEMENT   •   ${data.accountName}   •   ${data.periodLabel}", MARGIN, y + 9f, paint)
        y += 14f
        return y
    }

    private fun drawTableHeader(canvas: Canvas, startY: Float): Float {
        val y = startY
        val tableWidth = PAGE_WIDTH - (MARGIN * 2)
        val headerHeight = 18f

        // Header Background
        val bgPaint = Paint().apply {
            color = Color.parseColor("#E2E8F0")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRect(MARGIN, y, MARGIN + tableWidth, y + headerHeight, bgPaint)

        val textPaint = Paint().apply {
            color = Color.parseColor("#334155")
            textSize = 8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        canvas.drawText("DATE", MARGIN + 4f, y + 12f, textPaint)
        canvas.drawText("REF CODE", MARGIN + 68f, y + 12f, textPaint)
        canvas.drawText("COUNTERPARTY / DESCRIPTION", MARGIN + 145f, y + 12f, textPaint)
        canvas.drawText("CATEGORY", MARGIN + 330f, y + 12f, textPaint)
        canvas.drawText("AMOUNT (KES)", PAGE_WIDTH - MARGIN - 65f, y + 12f, textPaint)

        return y + headerHeight + 4f
    }

    private fun drawTableRow(canvas: Canvas, row: StatementRowData, y: Float) {
        val textPaint = Paint().apply {
            color = Color.parseColor("#1E293B")
            textSize = 8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        val isCredit = row.direction.equals("CREDIT", ignoreCase = true)
        val amountPaint = Paint().apply {
            color = if (isCredit) Color.parseColor("#16A34A") else Color.parseColor("#0F172A")
            textSize = 8f
            typeface = Typeface.create(Typeface.DEFAULT, if (isCredit) Typeface.BOLD else Typeface.NORMAL)
            isAntiAlias = true
        }

        // Subdued border line
        val linePaint = Paint().apply {
            color = Color.parseColor("#F1F5F9")
            strokeWidth = 0.5f
        }
        canvas.drawLine(MARGIN, y + 15f, PAGE_WIDTH - MARGIN, y + 15f, linePaint)

        // Date (DD/MM/YY or YYYY-MM-DD trimmed)
        val cleanDate = row.date.take(10)
        canvas.drawText(cleanDate, MARGIN + 4f, y + 10f, textPaint)

        // Ref Code
        canvas.drawText(truncate(row.reference, 12), MARGIN + 68f, y + 10f, textPaint)

        // Counterparty
        canvas.drawText(truncate(row.counterparty, 34), MARGIN + 145f, y + 10f, textPaint)

        // Category
        canvas.drawText(truncate(row.category, 16), MARGIN + 330f, y + 10f, textPaint)

        // Amount
        val sign = if (isCredit) "+" else "-"
        val formattedAmt = "$sign${currencyFormat.format(row.amount)}"
        canvas.drawText(formattedAmt, PAGE_WIDTH - MARGIN - 65f, y + 10f, amountPaint)
    }

    private fun drawPageFooter(canvas: Canvas, pageNumber: Int) {
        val footerPaint = Paint().apply {
            color = Color.parseColor("#94A3B8")
            textSize = 7.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }
        val linePaint = Paint().apply {
            color = Color.parseColor("#E2E8F0")
            strokeWidth = 0.5f
        }
        canvas.drawLine(MARGIN, PAGE_HEIGHT - 30f, PAGE_WIDTH - MARGIN, PAGE_HEIGHT - 30f, linePaint)

        val footerText = "SmartMoney Intelligence System Generated • Verified Offline Record • Page $pageNumber"
        canvas.drawText(footerText, MARGIN, PAGE_HEIGHT - 18f, footerPaint)
    }

    private fun truncate(text: String, maxChars: Int): String {
        return if (text.length > maxChars) text.take(maxChars - 2) + ".." else text
    }
}
