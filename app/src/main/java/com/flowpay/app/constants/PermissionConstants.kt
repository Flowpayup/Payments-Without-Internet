// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

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

    // Phone-call permission group requested together before dialing. ANSWER_PHONE_CALLS
    // powers the overlay's "End call" button and is in the same group, so it adds no
    // extra consent dialog. Launched via ActivityResultContracts.RequestMultiplePermissions.
    val PHONE_PERMISSIONS = arrayOf(
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.ANSWER_PHONE_CALLS
    )
}
