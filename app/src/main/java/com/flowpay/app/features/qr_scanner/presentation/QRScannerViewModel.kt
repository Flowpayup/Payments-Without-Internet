package com.flowpay.app.features.qr_scanner.presentation

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowpay.app.features.qr_scanner.domain.QRCodeParser
import com.flowpay.app.managers.PermissionManager
import com.flowpay.app.data.UPIData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class QRScannerState(
    val isScanning: Boolean = true,
    val showInstructions: Boolean = false,
    val showSuccess: Boolean = false,
    val vpaAddress: String = "",
    val error: String? = null,
    val missingPermissions: List<String> = emptyList(),
    val needsOverlayPermission: Boolean = false
)

class QRScannerViewModel : ViewModel() {
    
    private val _state = MutableStateFlow(QRScannerState())
    val state: StateFlow<QRScannerState> = _state.asStateFlow()
    
    fun processQRCode(qrCode: String, context: Context) {
        viewModelScope.launch {
            try {
                // Parse UPI QR code (strict: valid upi:// URI or bare VPA only)
                val parseResult = QRCodeParser.parse(qrCode)
                if (parseResult is QRCodeParser.ParseResult.Invalid) {
                    _state.value = _state.value.copy(
                        error = parseResult.reason
                    )
                    return@launch
                }
                val upiData = (parseResult as QRCodeParser.ParseResult.Valid).data
                
                // Check all required permissions before proceeding using PermissionManager
                val permissionManager = PermissionManager(context as android.app.Activity)
                if (!permissionManager.checkAllPermissions()) {
                    _state.value = _state.value.copy(
                        missingPermissions = permissionManager.getMissingPermissions(),
                        needsOverlayPermission = !permissionManager.canDrawOverlays()
                    )
                    return@launch
                }
                
                // Copy VPA to clipboard
                copyToClipboard(context, upiData.vpa)
                
                // Update state
                _state.value = _state.value.copy(
                    isScanning = false,
                    showInstructions = false, // We'll use overlay instead
                    vpaAddress = upiData.vpa
                )
                
                // Dial USSD code
                dialUSSD(context)
                
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = "Failed to process QR code: ${e.message}"
                )
            }
        }
    }
    
    
    private fun copyToClipboard(context: Context, vpa: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("UPI VPA", vpa)
        clipboard.setPrimaryClip(clip)
        
        // Show toast
        Toast.makeText(context, "VPA copied to clipboard", Toast.LENGTH_SHORT).show()
    }
    
    private fun dialUSSD(context: Context) {
        try {
            val ussdCode = "*99*1*3#"
            val encodedHash = Uri.encode("#")
            val ussd = ussdCode.replace("#", encodedHash)
            
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$ussd")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            context.startActivity(intent)
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                error = "Failed to initiate USSD: ${e.message}"
            )
        }
    }
    
    fun onPaymentComplete() {
        _state.value = _state.value.copy(
            showInstructions = false,
            showSuccess = true
        )
    }
    
    fun cancelPayment() {
        _state.value = QRScannerState()
    }
    
    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
    
    fun clearPermissionErrors() {
        _state.value = _state.value.copy(
            missingPermissions = emptyList(),
            needsOverlayPermission = false
        )
    }
    
    fun resetScanningState() {
        _state.value = _state.value.copy(
            isScanning = true,
            showInstructions = false,
            showSuccess = false,
            vpaAddress = "",
            error = null
        )
    }
    
    
    fun requestOverlayPermission(context: Context) {
        val permissionManager = PermissionManager(context as android.app.Activity)
        permissionManager.requestOverlayPermission()
    }
}
