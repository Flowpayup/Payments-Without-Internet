package com.flowpay.app.ui.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.flowpay.app.R
import com.flowpay.app.managers.PermissionManager

/**
 * Visual permission guide activity that helps users understand and grant required permissions
 * Provides step-by-step guidance with visual aids
 */
class PermissionGuideActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "PermissionGuideActivity"
        private const val EXTRA_PERMISSION_TYPE = "permission_type"
        
        const val PERMISSION_OVERLAY = "overlay"
        const val PERMISSION_CALL = "call"
        const val PERMISSION_SMS = "sms"
        const val PERMISSION_ALL = "all"
        
        private const val REQUEST_OVERLAY_PERMISSION = 1001
        private const val REQUEST_CALL_PERMISSION = 1002
    }
    
    private lateinit var permissionManager: PermissionManager
    private lateinit var errorRecoveryManager: com.flowpay.app.managers.ErrorRecoveryManager
    
    private lateinit var ivPermissionIcon: ImageView
    private lateinit var tvPermissionTitle: TextView
    private lateinit var tvPermissionDescription: TextView
    private lateinit var tvPermissionSteps: TextView
    private lateinit var btnGrantPermission: Button
    private lateinit var btnSkip: Button
    private lateinit var btnNext: Button
    
    private var currentPermissionType: String = PERMISSION_ALL
    private var permissionIndex = 0
    private val permissionTypes = listOf(PERMISSION_OVERLAY, PERMISSION_CALL, PERMISSION_SMS)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission_guide)
        
        Log.d(TAG, "PermissionGuideActivity created")
        
        // Initialize managers
        permissionManager = PermissionManager(this)
        errorRecoveryManager = com.flowpay.app.managers.ErrorRecoveryManager(this)
        
        // Get permission type from intent
        currentPermissionType = intent.getStringExtra(EXTRA_PERMISSION_TYPE) ?: PERMISSION_ALL
        
        initializeViews()
        setupPermissionGuide()
    }
    
    private fun initializeViews() {
        ivPermissionIcon = findViewById(R.id.ivPermissionIcon)
        tvPermissionTitle = findViewById(R.id.tvPermissionTitle)
        tvPermissionDescription = findViewById(R.id.tvPermissionDescription)
        tvPermissionSteps = findViewById(R.id.tvPermissionSteps)
        btnGrantPermission = findViewById(R.id.btnGrantPermission)
        btnSkip = findViewById(R.id.btnSkip)
        btnNext = findViewById(R.id.btnNext)
        
        // Set up button listeners
        btnGrantPermission.setOnClickListener { grantCurrentPermission() }
        btnSkip.setOnClickListener { skipCurrentPermission() }
        btnNext.setOnClickListener { nextPermission() }
    }
    
    private fun setupPermissionGuide() {
        when (currentPermissionType) {
            PERMISSION_OVERLAY -> setupOverlayPermissionGuide()
            PERMISSION_CALL -> setupCallPermissionGuide()
            PERMISSION_SMS -> setupSMSPermissionGuide()
            PERMISSION_ALL -> setupAllPermissionsGuide()
        }
    }
    
    private fun setupOverlayPermissionGuide() {
        ivPermissionIcon.setImageResource(R.drawable.ic_shield)
        tvPermissionTitle.text = "Overlay Permission"
        tvPermissionDescription.text = "Flowpay needs overlay permission to show payment guidance during USSD calls. This helps protect you from fraud by displaying secure payment instructions."
        
        val steps = buildString {
            append("1. Tap 'Grant Permission' below\n")
            append("2. Find 'Flowpay' in the list\n")
            append("3. Toggle 'Allow display over other apps'\n")
            append("4. Return to Flowpay")
        }
        tvPermissionSteps.text = steps
        
        btnGrantPermission.text = "Open Settings"
        btnSkip.visibility = View.VISIBLE
        btnNext.visibility = View.GONE
    }
    
    private fun setupCallPermissionGuide() {
        ivPermissionIcon.setImageResource(R.drawable.ic_phone)
        tvPermissionTitle.text = "Call Permission"
        tvPermissionDescription.text = "Flowpay needs call permission to initiate USSD calls for payments. This is essential for the payment process to work."
        
        val steps = buildString {
            append("1. Tap 'Grant Permission' below\n")
            append("2. Select 'Allow' when prompted\n")
            append("3. Permission will be granted automatically")
        }
        tvPermissionSteps.text = steps
        
        btnGrantPermission.text = "Grant Permission"
        btnSkip.visibility = View.VISIBLE
        btnNext.visibility = View.GONE
    }
    
    private fun setupSMSPermissionGuide() {
        ivPermissionIcon.setImageResource(R.drawable.ic_sms)
        tvPermissionTitle.text = "Notification Access"
        tvPermissionDescription.text = "Flowpay needs notification access to detect bank payment confirmations. This allows automatic completion of payments when your bank sends an SMS notification."

        val steps = buildString {
            append("1. Tap 'Open Settings' below\n")
            append("2. Find 'Flowpay' in the list\n")
            append("3. Toggle the switch to enable\n")
            append("4. Return to Flowpay")
        }
        tvPermissionSteps.text = steps

        btnGrantPermission.text = "Open Settings"
        btnSkip.visibility = View.VISIBLE
        btnNext.visibility = View.GONE
    }
    
    private fun setupAllPermissionsGuide() {
        if (permissionIndex >= permissionTypes.size) {
            // All permissions shown, finish
            finishWithResult(true)
            return
        }
        
        val permissionType = permissionTypes[permissionIndex]
        when (permissionType) {
            PERMISSION_OVERLAY -> setupOverlayPermissionGuide()
            PERMISSION_CALL -> setupCallPermissionGuide()
            PERMISSION_SMS -> setupSMSPermissionGuide()
        }
        
        // Show next button if not last permission
        if (permissionIndex < permissionTypes.size - 1) {
            btnNext.visibility = View.VISIBLE
        } else {
            btnNext.visibility = View.GONE
        }
    }
    
    private fun grantCurrentPermission() {
        when (currentPermissionType) {
            PERMISSION_OVERLAY -> grantOverlayPermission()
            PERMISSION_CALL -> grantCallPermission()
            PERMISSION_SMS -> grantSMSPermission()
            PERMISSION_ALL -> grantCurrentPermissionInSequence()
        }
    }
    
    private fun grantOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION)
            } else {
                onPermissionGranted()
            }
        } else {
            onPermissionGranted()
        }
    }
    
    private fun grantCallPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) 
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), REQUEST_CALL_PERMISSION)
        } else {
            onPermissionGranted()
        }
    }
    
    private fun grantSMSPermission() {
        startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
    
    private fun grantCurrentPermissionInSequence() {
        val permissionType = permissionTypes[permissionIndex]
        when (permissionType) {
            PERMISSION_OVERLAY -> grantOverlayPermission()
            PERMISSION_CALL -> grantCallPermission()
            PERMISSION_SMS -> grantSMSPermission()
        }
    }
    
    private fun skipCurrentPermission() {
        if (currentPermissionType == PERMISSION_ALL) {
            nextPermission()
        } else {
            finishWithResult(false)
        }
    }
    
    private fun nextPermission() {
        permissionIndex++
        setupAllPermissionsGuide()
    }
    
    private fun onPermissionGranted() {
        errorRecoveryManager.showSuccessToast("Permission granted successfully!")
        
        if (currentPermissionType == PERMISSION_ALL) {
            nextPermission()
        } else {
            finishWithResult(true)
        }
    }
    
    private fun onPermissionDenied() {
        errorRecoveryManager.showErrorToast("Permission denied. Some features may not work properly.")
        
        if (currentPermissionType == PERMISSION_ALL) {
            nextPermission()
        } else {
            finishWithResult(false)
        }
    }
    
    private fun finishWithResult(success: Boolean) {
        val resultIntent = Intent().apply {
            putExtra("permission_granted", success)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }
    
    override fun onResume() {
        super.onResume()
        val isSmsSectionActive = currentPermissionType == PERMISSION_SMS ||
                (currentPermissionType == PERMISSION_ALL && permissionTypes.getOrNull(permissionIndex) == PERMISSION_SMS)
        if (isSmsSectionActive) {
            val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            if (flat?.contains(packageName) == true) {
                onPermissionGranted()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        when (requestCode) {
            REQUEST_OVERLAY_PERMISSION -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (Settings.canDrawOverlays(this)) {
                        onPermissionGranted()
                    } else {
                        onPermissionDenied()
                    }
                } else {
                    onPermissionGranted()
                }
            }
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            REQUEST_CALL_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    onPermissionGranted()
                } else {
                    onPermissionDenied()
                }
            }
        }
    }
}

