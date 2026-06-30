package com.flowpay.app.managers

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.flowpay.app.constants.PermissionConstants

class PermissionManager(private val activity: Activity) {
    
    companion object {
        private const val TAG = "PermissionManager"
        
        fun canDrawOverlays(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true
            }
        }
    }
    
    /**
     * Checks if all required permissions are granted
     */
    fun checkAllPermissions(): Boolean {
        return PermissionConstants.REQUIRED_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Convenience helpers for feature-based permission groups
     */

    /**
     * Phone permissions: CALL_PHONE + READ_PHONE_STATE (and any other critical phone-related ones)
     */
    fun hasPhonePermissions(): Boolean {
        return isPermissionGranted(Manifest.permission.CALL_PHONE) &&
                isPermissionGranted(Manifest.permission.READ_PHONE_STATE)
    }

    fun requestPhonePermissions() {
        val phonePermissions = listOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            // Optional: powers the overlay's "End call" button. Same PHONE
            // permission group, so it does not add an extra consent dialog.
            Manifest.permission.ANSWER_PHONE_CALLS
        )

        val permissionsNeeded = phonePermissions.filterNot { isPermissionGranted(it) }

        if (permissionsNeeded.isNotEmpty()) {
            Log.d(TAG, "Requesting phone permissions: ${permissionsNeeded.joinToString()}")
            ActivityCompat.requestPermissions(
                activity,
                permissionsNeeded.toTypedArray(),
                PermissionConstants.PERMISSIONS_REQUEST_CODE
            )
        } else {
            Log.d(TAG, "Phone permissions already granted")
        }
    }

    /**
     * Camera permission: CAMERA
     */
    fun hasCameraPermission(): Boolean {
        return isPermissionGranted(Manifest.permission.CAMERA)
    }

    fun requestCameraPermission() {
        if (!hasCameraPermission()) {
            Log.d(TAG, "Requesting camera permission")
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.CAMERA),
                PermissionConstants.CAMERA_PERMISSION_REQ_CODE
            )
        } else {
            Log.d(TAG, "Camera permission already granted")
        }
    }
    
    /**
     * Checks if overlay permission is granted
     */
    fun checkOverlayPermission(): Boolean {
        return canDrawOverlays(activity)
    }

    /**
     * Alias for readability in some call sites
     */
    fun hasOverlayPermission(): Boolean = checkOverlayPermission()
    
    /**
     * Requests all required permissions using native Android dialogs
     */
    fun requestRequiredPermissions() {
        val permissionsNeeded = mutableListOf<String>()
        
        PermissionConstants.REQUIRED_PERMISSIONS.forEach { permission ->
            if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(permission)
            }
        }
        
        if (permissionsNeeded.isNotEmpty()) {
            Log.d(TAG, "Requesting permissions: ${permissionsNeeded.joinToString()}")
            // Use native Android permission request - no custom dialog
            ActivityCompat.requestPermissions(
                activity,
                permissionsNeeded.toTypedArray(),
                PermissionConstants.PERMISSIONS_REQUEST_CODE
            )
        } else {
            Log.d(TAG, "All permissions already granted")
        }
    }
    
    
    /**
     * Requests overlay permission
     */
    fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !canDrawOverlays(activity)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${activity.packageName}")
            )
            activity.startActivityForResult(intent, PermissionConstants.OVERLAY_PERMISSION_REQ_CODE)
        }
    }
    
    /**
     * Checks if a specific permission is granted
     */
    fun isPermissionGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
    }
    
    
    /**
     * Gets the list of missing permissions
     */
    fun getMissingPermissions(): List<String> {
        return PermissionConstants.REQUIRED_PERMISSIONS.filter { permission ->
            ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED
        }
    }
    
    
    /**
     * Handles permission request results
     */
    fun handlePermissionResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ): Boolean {
        when (requestCode) {
            PermissionConstants.PERMISSIONS_REQUEST_CODE,
            PermissionConstants.SMS_PERMISSION_REQUEST_CODE,
            PermissionConstants.CAMERA_PERMISSION_REQ_CODE,
            PermissionConstants.CONTACTS_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isEmpty()) {
                    Log.w(TAG, "Empty grantResults for requestCode=$requestCode (likely cancelled)")
                    return false
                }
                val allPermissionsGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
                if (allPermissionsGranted) {
                    Log.d(TAG, "All permissions granted for requestCode=$requestCode")
                    return true
                } else {
                    val deniedPermissions = permissions.filterIndexed { index, _ ->
                        grantResults[index] != PackageManager.PERMISSION_GRANTED
                    }
                    Log.w(TAG, "Denied permissions: ${deniedPermissions.joinToString()}")
                    return false
                }
            }
            PermissionConstants.OVERLAY_PERMISSION_REQ_CODE -> {
                val overlayGranted = checkOverlayPermission()
                if (overlayGranted) {
                    Log.d(TAG, "Overlay permission granted")
                    return true
                } else {
                    Log.w(TAG, "Overlay permission denied")
                    return false
                }
            }
        }
        return false
    }
    
    /**
     * Gets permission status summary
     */
    fun getPermissionStatusSummary(): String {
        val granted = PermissionConstants.REQUIRED_PERMISSIONS.count { isPermissionGranted(it) }
        val total = PermissionConstants.REQUIRED_PERMISSIONS.size
        val overlayGranted = checkOverlayPermission()
        
        return "Permissions: $granted/$total granted, Overlay: ${if (overlayGranted) "Yes" else "No"}"
    }
    
    
    
    /**
     * Checks if overlay permission is available for services
     * This replaces inline checks in USSDOverlayService and UssdSetupOverlayService
     */
    fun canDrawOverlays(): Boolean {
        return canDrawOverlays(activity)
    }
    
    /**
     * Checks if RECEIVE_SMS runtime permission is granted.
     */
    fun checkSMSPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            activity, Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** Alias with consistent naming */
    fun hasSmsPermissions(): Boolean = checkSMSPermissions()

    /**
     * Requests RECEIVE_SMS permission at runtime.
     */
    fun requestSMSPermissions() {
        Log.d(TAG, "Requesting RECEIVE_SMS permission")
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.RECEIVE_SMS),
            PermissionConstants.SMS_PERMISSION_REQUEST_CODE
        )
    }

    /** Returns SMS permission status */
    fun getSMSPermissionStatus(): String {
        val enabled = checkSMSPermissions()
        return "SMS: ${if (enabled) "✅ Granted" else "❌ Denied"}"
    }

    /** SMS permission is required for payment detection */
    fun areSMSPermissionsCritical(): Boolean = true
    
    /**
     * Checks if contact permission is granted
     */
    fun hasContactPermission(): Boolean {
        return isPermissionGranted(Manifest.permission.READ_CONTACTS)
    }
    
    /**
     * Requests contact permission
     */
    fun requestContactPermission() {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.READ_CONTACTS),
            PermissionConstants.CONTACTS_PERMISSION_REQUEST_CODE
        )
    }

}
