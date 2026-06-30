package com.flowpay.app.utils

import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

/**
 * Centralized logging utility for the CallOverlay system
 * Provides structured logging with consistent formatting and categorization
 */
object OverlayLogger {
    
    private const val TAG = "CallOverlaySystem"
    private const val DATE_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS"
    private val dateFormatter = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
    
    /**
     * Log service-related events
     */
    fun logServiceEvent(event: String, data: Map<String, Any> = emptyMap()) {
        val timestamp = dateFormatter.format(Date())
        val dataString = if (data.isNotEmpty()) " | Data: $data" else ""
        Log.d(TAG, "[$timestamp] SERVICE_EVENT: $event$dataString")
    }
    
    /**
     * Log call detection events
     */
    fun logCallDetection(phoneNumber: String?, state: String, additionalInfo: String = "") {
        val timestamp = dateFormatter.format(Date())
        val phoneInfo = if (phoneNumber != null) " | Phone: $phoneNumber" else " | Phone: null"
        val extraInfo = if (additionalInfo.isNotEmpty()) " | $additionalInfo" else ""
        Log.d(TAG, "[$timestamp] CALL_DETECTION: $state$phoneInfo$extraInfo")
    }
    
    /**
     * Log overlay state changes
     */
    fun logOverlayState(action: String, success: Boolean, error: String? = null, details: Map<String, Any> = emptyMap()) {
        val timestamp = dateFormatter.format(Date())
        val status = if (success) "SUCCESS" else "FAILED"
        val errorInfo = if (error != null) " | Error: $error" else ""
        val detailsInfo = if (details.isNotEmpty()) " | Details: $details" else ""
        Log.d(TAG, "[$timestamp] OVERLAY_$action: $status$errorInfo$detailsInfo")
    }
    
    /**
     * Log permission-related events
     */
    fun logPermissionEvent(permission: String, granted: Boolean, context: String = "") {
        val timestamp = dateFormatter.format(Date())
        val status = if (granted) "GRANTED" else "DENIED"
        val contextInfo = if (context.isNotEmpty()) " | Context: $context" else ""
        Log.d(TAG, "[$timestamp] PERMISSION: $permission = $status$contextInfo")
    }
    
    /**
     * Log performance metrics
     */
    fun logPerformance(operation: String, duration: Long, details: Map<String, Any> = emptyMap()) {
        val timestamp = dateFormatter.format(Date())
        val detailsInfo = if (details.isNotEmpty()) " | Details: $details" else ""
        Log.d(TAG, "[$timestamp] PERFORMANCE: $operation took ${duration}ms$detailsInfo")
    }
    
    /**
     * Log error events with stack trace
     */
    fun logError(operation: String, error: Throwable, context: Map<String, Any> = emptyMap()) {
        val timestamp = dateFormatter.format(Date())
        val contextInfo = if (context.isNotEmpty()) " | Context: $context" else ""
        Log.e(TAG, "[$timestamp] ERROR in $operation$contextInfo", error)
    }
    
    /**
     * Log warning events
     */
    fun logWarning(operation: String, message: String, context: Map<String, Any> = emptyMap()) {
        val timestamp = dateFormatter.format(Date())
        val contextInfo = if (context.isNotEmpty()) " | Context: $context" else ""
        Log.w(TAG, "[$timestamp] WARNING in $operation: $message$contextInfo")
    }
    
    /**
     * Log debug information
     */
    fun logDebug(operation: String, message: String, data: Map<String, Any> = emptyMap()) {
        val timestamp = dateFormatter.format(Date())
        val dataInfo = if (data.isNotEmpty()) " | Data: $data" else ""
        Log.d(TAG, "[$timestamp] DEBUG in $operation: $message$dataInfo")
    }
    
    /**
     * Log system health status
     */
    fun logHealthStatus(component: String, status: String, metrics: Map<String, Any> = emptyMap()) {
        val timestamp = dateFormatter.format(Date())
        val metricsInfo = if (metrics.isNotEmpty()) " | Metrics: $metrics" else ""
        Log.i(TAG, "[$timestamp] HEALTH: $component = $status$metricsInfo")
    }
    
    /**
     * Log user interaction events
     */
    fun logUserInteraction(action: String, details: Map<String, Any> = emptyMap()) {
        val timestamp = dateFormatter.format(Date())
        val detailsInfo = if (details.isNotEmpty()) " | Details: $details" else ""
        Log.i(TAG, "[$timestamp] USER_ACTION: $action$detailsInfo")
    }
    
    /**
     * Log system state changes
     */
    fun logStateChange(from: String, to: String, reason: String = "", data: Map<String, Any> = emptyMap()) {
        val timestamp = dateFormatter.format(Date())
        val reasonInfo = if (reason.isNotEmpty()) " | Reason: $reason" else ""
        val dataInfo = if (data.isNotEmpty()) " | Data: $data" else ""
        Log.i(TAG, "[$timestamp] STATE_CHANGE: $from -> $to$reasonInfo$dataInfo")
    }
}

