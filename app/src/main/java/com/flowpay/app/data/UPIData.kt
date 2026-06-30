package com.flowpay.app.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class UPIData(
    var vpa: String,
    val payeeName: String,
    var amount: String,
    val transactionNote: String,
    val currency: String
) : Parcelable

@Parcelize
data class TransactionData(
    val amount: String,
    val upiId: String,
    val balance: String,
    val recipient: String,
    val status: String,
    val rawMessage: String,
    val timestamp: Long = System.currentTimeMillis()
) : Parcelable
