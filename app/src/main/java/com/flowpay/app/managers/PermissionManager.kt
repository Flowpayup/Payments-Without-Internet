// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.flowpay.app.managers

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.flowpay.app.constants.PermissionConstants

class PermissionManager(private val activity: Activity) {

    companion object {
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
     * Phone permissions: CALL_PHONE + READ_PHONE_STATE (and any other critical phone-related ones).
     * Requesting them is done by the owning Activity via an
     * ActivityResultContracts.RequestMultiplePermissions launcher over
     * [PermissionConstants.PHONE_PERMISSIONS] — a launcher must be registered
     * at Activity-creation time, which this on-demand helper can't do.
     */
    fun hasPhonePermissions(): Boolean {
        return isPermissionGranted(Manifest.permission.CALL_PHONE) &&
            isPermissionGranted(Manifest.permission.READ_PHONE_STATE)
    }

    /**
     * Camera permission: CAMERA
     */
    fun hasCameraPermission(): Boolean {
        return isPermissionGranted(Manifest.permission.CAMERA)
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
     * The system "draw over other apps" settings intent, or null if the
     * permission is already granted (or unnecessary below API M). Callers
     * launch it via their own ActivityResultLauncher — a launcher must be
     * registered at composition/creation time, not launch time, so this
     * class (constructed on demand, often from a composable) can't safely
     * own one itself.
     */
    fun overlayPermissionSettingsIntent(): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || canDrawOverlays(activity)) return null
        return Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${activity.packageName}")
        )
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

    /**
     * Checks if contact permission is granted
     */
    fun hasContactPermission(): Boolean {
        return isPermissionGranted(Manifest.permission.READ_CONTACTS)
    }
}
