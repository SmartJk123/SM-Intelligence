package com.example.smartmoney.ui.invoice

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartmoney.domain.model.Transaction
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.math.BigDecimal

class InvoiceViewModel : ViewModel() {

    private val _vendor = MutableStateFlow("")
    val vendor = _vendor.asStateFlow()

    private val _amount = MutableStateFlow("")
    val amount = _amount.asStateFlow()

    private val _currency = MutableStateFlow("KES")
    val currency = _currency.asStateFlow()

    private val _invoiceDate = MutableStateFlow("")
    val invoiceDate = _invoiceDate.asStateFlow()

    private val _dueDate = MutableStateFlow("")
    val dueDate = _dueDate.asStateFlow()

    private val _invoiceNumber = MutableStateFlow("")
    val invoiceNumber = _invoiceNumber.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private val _extractedText = MutableStateFlow("")
    val extractedText = _extractedText.asStateFlow()

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    fun onVendorChange(v: String) { _vendor.value = v }
    fun onAmountChange(a: String) { _amount.value = a }
    fun onCurrencyChange(c: String) { _currency.value = c }
    fun onInvoiceDateChange(d: String) { _invoiceDate.value = d }
    fun onDueDateChange(d: String) { _dueDate.value = d }
    fun onInvoiceNumberChange(n: String) { _invoiceNumber.value = n }

    fun processImageForOcr(bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.Default) {
            _isScanning.value = true
            try {
                val image = InputImage.fromBitmap(bitmap, 0)
                // Use coroutines to await the result from the Play Services Task API off the main thread
                val visionText = recognizer.process(image).await()
                val fullText = visionText.text
                _extractedText.value = fullText
                
                // Very basic regex to find a date
                val dateRegex = """\d{2,4}[-/]\d{1,2}[-/]\d{1,4}""".toRegex()
                val matchDate = dateRegex.find(fullText)
                if (matchDate != null && _invoiceDate.value.isEmpty()) {
                    _invoiceDate.value = matchDate.value
                }
                
                // Very basic regex to find total amount (look for Total followed by a number)
                val amountRegex = """(?i)Total[^\d]*?([\d,\.]+)""".toRegex()
                val matchAmount = amountRegex.find(fullText)
                if (matchAmount != null && matchAmount.groupValues.size > 1 && _amount.value.isEmpty()) {
                    val a = matchAmount.groupValues[1].replace(",", "")
                    if (a.toDoubleOrNull() != null) {
                        _amount.value = a
                    }
                }

                // If vendor is empty, just take the first line
                if (fullText.isNotBlank() && _vendor.value.isEmpty()) {
                    _vendor.value = fullText.lines().firstOrNull { it.isNotBlank() } ?: ""
                }

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isScanning.value = false
            }
        }
    }
    
    // Instead of directly making API call, this returns the filled transaction
    // so DashboardScreen can pass it to TransactionViewModel
    fun getPendingTransaction(): Transaction? {
        if (_vendor.value.isBlank() || _amount.value.isBlank() || _invoiceDate.value.isBlank()) return null
        
        val parsedAmount = try { BigDecimal(_amount.value) } catch (e: Exception) { BigDecimal.ZERO }
        
        return Transaction(
            id = "pending_${System.currentTimeMillis()}",
            accountId = "INVOICE",
            amount = parsedAmount,
            type = "DEBIT",
            timestamp = "${_invoiceDate.value}T00:00:00Z",
            description = _vendor.value,
            status = "PENDING",
            source = "INVOICE"
        )
    }

    fun clearData() {
        _vendor.value = ""
        _amount.value = ""
        _invoiceDate.value = ""
        _dueDate.value = ""
        _invoiceNumber.value = ""
        _extractedText.value = ""
    }
}
