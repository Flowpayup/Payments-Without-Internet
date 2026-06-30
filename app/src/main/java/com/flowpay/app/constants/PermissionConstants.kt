package com.flowpay.app.constants

import android.Manifest

/**
 * Centralized permission constants to avoid duplication and ensure consistency
 */
object PermissionConstants {
    
    // Standard Android permissions required by the app
    val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.MODIFY_AUDIO_SETTINGS,
        Manifest.permission.CAMERA,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.VIBRATE
    )
    
    // Critical permissions that are absolutely necessary for core functionality
    val CRITICAL_PERMISSIONS = arrayOf(
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_PHONE_STATE
    )
    
    // Optional permissions that enhance functionality but aren't critical
    val OPTIONAL_PERMISSIONS = arrayOf(
        Manifest.permission.MODIFY_AUDIO_SETTINGS,
        Manifest.permission.CAMERA,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.VIBRATE
    )
    
    // Permission request codes - using unique values to avoid conflicts
    const val PERMISSIONS_REQUEST_CODE = 0x1000
    const val OVERLAY_PERMISSION_REQ_CODE = 0x1001
    const val CAMERA_PERMISSION_REQ_CODE = 0x1002
    const val SMS_PERMISSION_REQUEST_CODE = 0x1003
    const val CONTACTS_PERMISSION_REQUEST_CODE = 0x1004

    // Permission descriptions for user-friendly explanations
    val PERMISSION_DESCRIPTIONS = mapOf(
        Manifest.permission.CALL_PHONE to "Make phone calls for USSD payments",
        Manifest.permission.READ_PHONE_STATE to "Monitor call states for payment protection",
        Manifest.permission.MODIFY_AUDIO_SETTINGS to "Control audio during calls for better experience",
        Manifest.permission.CAMERA to "Scan QR codes",
        "notification_listener" to "Notification access for bank payment confirmations",
        Manifest.permission.READ_CONTACTS to "Select contacts for easy transfers",
        Manifest.permission.VIBRATE to "Provide haptic feedback for notifications"
    )

    // Permission categories for better organization
    enum class PermissionCategory {
        PHONE,
        AUDIO,
        CAMERA,
        MESSAGING,
        CONTACTS,
        SYSTEM
    }
    
    val PERMISSION_CATEGORIES = mapOf(
        Manifest.permission.CALL_PHONE to PermissionCategory.PHONE,
        Manifest.permission.READ_PHONE_STATE to PermissionCategory.PHONE,
        Manifest.permission.MODIFY_AUDIO_SETTINGS to PermissionCategory.AUDIO,
        Manifest.permission.CAMERA to PermissionCategory.CAMERA,
        Manifest.permission.READ_CONTACTS to PermissionCategory.CONTACTS,
        Manifest.permission.VIBRATE to PermissionCategory.SYSTEM
    )
}
