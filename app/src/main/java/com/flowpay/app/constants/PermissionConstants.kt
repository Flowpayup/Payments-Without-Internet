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
    
    // Permission request codes - using unique values to avoid conflicts
    const val PERMISSIONS_REQUEST_CODE = 0x1000
    const val OVERLAY_PERMISSION_REQ_CODE = 0x1001
    const val CAMERA_PERMISSION_REQ_CODE = 0x1002
    const val SMS_PERMISSION_REQUEST_CODE = 0x1003
    const val CONTACTS_PERMISSION_REQUEST_CODE = 0x1004
}
