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
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import com.example.smartmoney.core.ocr.BitmapOcrOptimizer
import com.example.smartmoney.core.ocr.InvoiceSpatialExtractor
import com.example.smartmoney.data.remote.RetrofitClient
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
                // Downscale to bounded OCR dimensions off the main thread to eliminate GC churn and accelerate inference
                val optimizedBitmap = BitmapOcrOptimizer.prepareForOcr(bitmap)
                val image = InputImage.fromBitmap(optimizedBitmap, 0)

                // Use coroutines to await the result from the Play Services Task API off the main thread
                val visionText = recognizer.process(image).await()

                // Extract structured fields via 2D spatial geometry analysis
                val extracted = InvoiceSpatialExtractor.extract(visionText)
                _extractedText.value = extracted.rawText

                extracted.vendor?.takeIf { it.isNotBlank() }?.let { _vendor.value = it }
                extracted.invoiceNumber?.takeIf { it.isNotBlank() }?.let { _invoiceNumber.value = it }
                extracted.amount?.takeIf { it.isNotBlank() }?.let { _amount.value = it }
                extracted.currency.takeIf { it.isNotBlank() }?.let { _currency.value = it }
                extracted.invoiceDate?.takeIf { it.isNotBlank() }?.let { _invoiceDate.value = it }
                extracted.dueDate?.takeIf { it.isNotBlank() }?.let { _dueDate.value = it }

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isScanning.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            recognizer.close()
        } catch (e: Exception) {
            // Ignore failure during recognizer cleanup
        }
    }
    
    // Returns the parsed transaction so DashboardScreen can pass it to TransactionViewModel
    fun getInvoiceTransaction(): Transaction? {
        if (_vendor.value.isBlank() || _amount.value.isBlank() || _invoiceDate.value.isBlank()) return null
        
        val parsedAmount = try { BigDecimal(_amount.value) } catch (e: Exception) { BigDecimal.ZERO }
        
        return Transaction(
            id = "invoice_${System.currentTimeMillis()}",
            accountId = "INVOICE",
            amount = parsedAmount,
            type = "DEBIT",
            timestamp = "${_invoiceDate.value}T00:00:00Z",
            description = _vendor.value,
            status = "COMPLETED",
            source = "LOCAL_INVOICE",
            syncStatus = "LOCAL_ONLY"
        )
    }

    fun getPendingTransaction(): Transaction? = getInvoiceTransaction()

    fun clearData() {
        _vendor.value = ""
        _amount.value = ""
        _invoiceDate.value = ""
        _dueDate.value = ""
        _invoiceNumber.value = ""
        _extractedText.value = ""
    }

    suspend fun uploadInvoiceToBackend(fileBytes: ByteArray, filename: String = "invoice.jpg"): Result<com.example.smartmoney.domain.model.Invoice> {
        val mediaType = "image/jpeg".toMediaTypeOrNull()
        val requestFile = fileBytes.toRequestBody(mediaType)
        val filePart = MultipartBody.Part.createFormData("file", filename, requestFile)
        val textType = "text/plain".toMediaTypeOrNull()

        val vendorBody = _vendor.value.toRequestBody(textType)
        val invNumBody = _invoiceNumber.value.takeIf { it.isNotBlank() }?.toRequestBody(textType)
        val amountBody = _amount.value.toRequestBody(textType)
        val currencyBody = _currency.value.toRequestBody(textType)
        val dateBody = _invoiceDate.value.toRequestBody(textType)
        val dueBody = _dueDate.value.takeIf { it.isNotBlank() }?.toRequestBody(textType)

        return try {
            val response = RetrofitClient.invoiceApi.uploadInvoice(
                file = filePart,
                vendor = vendorBody,
                invoiceNumber = invNumBody,
                amount = amountBody,
                currency = currencyBody,
                invoiceDate = dateBody,
                dueDate = dueBody
            )
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val err = response.errorBody()?.string() ?: "Failed to upload invoice (HTTP ${response.code()})"
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
