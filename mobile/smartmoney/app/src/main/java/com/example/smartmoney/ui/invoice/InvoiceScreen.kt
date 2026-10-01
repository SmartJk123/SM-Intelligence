package com.example.smartmoney.ui.invoice

import androidx.compose.material.icons.outlined.Delete
import kotlinx.coroutines.launch
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.smartmoney.domain.model.Transaction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceScreen(
    viewModel: InvoiceViewModel = viewModel(),
    onBack: () -> Unit,
    onSubmitSuccess: (Transaction) -> Unit
) {
    val context = LocalContext.current
    val vendor by viewModel.vendor.collectAsState()
    val amount by viewModel.amount.collectAsState()
    val currency by viewModel.currency.collectAsState()
    val invoiceDate by viewModel.invoiceDate.collectAsState()
    val dueDate by viewModel.dueDate.collectAsState()
    val invoiceNumber by viewModel.invoiceNumber.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val extractedText by viewModel.extractedText.collectAsState()

    var imageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }

    val coroutineScope = rememberCoroutineScope()
    
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                val bitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, it))
                    } else {
                        MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                    }
                }
                imageBitmap = bitmap
                viewModel.processImageForOcr(bitmap)
            }
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && photoUri != null) {
            coroutineScope.launch {
                val bitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, photoUri!!))
                    } else {
                        MediaStore.Images.Media.getBitmap(context.contentResolver, photoUri!!)
                    }
                }
                imageBitmap = bitmap
                viewModel.processImageForOcr(bitmap)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val tempFile = java.io.File.createTempFile("invoice_", ".jpg", context.cacheDir)
            photoUri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
            takePictureLauncher.launch(photoUri!!)
        }
    }

    Scaffold(

    )
    { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (imageBitmap == null && !isScanning) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    OutlinedButton(onClick = { permissionLauncher.launch(android.Manifest.permission.CAMERA) }) {
                        Icon(Icons.Outlined.CameraAlt, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Take Photo")
                    }
                    OutlinedButton(onClick = { pickImageLauncher.launch(arrayOf("image/*")) }) {
                        Icon(Icons.Outlined.Image, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Upload File")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isScanning) {
                CircularProgressIndicator()
                Text("Scanning document...", modifier = Modifier.padding(top = 8.dp))
                Spacer(modifier = Modifier.height(16.dp))
            } else if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap!!.asImageBitmap(),
                    contentDescription = "Selected Invoice",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(bottom = 16.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(onClick = { permissionLauncher.launch(android.Manifest.permission.CAMERA) }) {
                        Icon(Icons.Outlined.CameraAlt, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Retake")
                    }
                    TextButton(onClick = { pickImageLauncher.launch(arrayOf("image/*")) }) {
                        Icon(Icons.Outlined.Image, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Replace")
                    }
                    TextButton(
                        onClick = {
                            imageBitmap = null
                            photoUri = null
                            viewModel.clearData()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete")
                    }
                }
            }

            OutlinedTextField(
                value = vendor,
                onValueChange = { viewModel.onVendorChange(it) },
                label = { Text("Vendor / Supplier") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )

            OutlinedTextField(
                value = invoiceNumber,
                onValueChange = { viewModel.onInvoiceNumberChange(it) },
                label = { Text("Invoice Number (Optional)") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )

            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { viewModel.onAmountChange(it) },
                    label = { Text("Total Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                OutlinedTextField(
                    value = currency,
                    onValueChange = { viewModel.onCurrencyChange(it) },
                    label = { Text("Currency") },
                    modifier = Modifier.weight(1f)
                )
            }

            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                OutlinedTextField(
                    value = invoiceDate,
                    onValueChange = { viewModel.onInvoiceDateChange(it) },
                    label = { Text("Invoice Date") },
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                OutlinedTextField(
                    value = dueDate,
                    onValueChange = { viewModel.onDueDateChange(it) },
                    label = { Text("Due Date (Optional)") },
                    modifier = Modifier.weight(1f)
                )
            }

            Button(
                onClick = {
                    val pendingTx = viewModel.getPendingTransaction()
                    if (pendingTx != null) {
                        onSubmitSuccess(pendingTx)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = vendor.isNotBlank() && amount.isNotBlank() && invoiceDate.isNotBlank()
            ) {
                Text("Save Pending Transaction")
            }
            
            if (extractedText.isNotBlank()) {
                Spacer(modifier = Modifier.height(24.dp))
                Text("Extracted Text Preview:", style = MaterialTheme.typography.titleMedium)
                Text(extractedText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
            
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
