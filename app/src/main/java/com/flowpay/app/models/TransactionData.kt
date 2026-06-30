package com.flowpay.app.models

import android.os.Parcel
import android.os.Parcelable

data class TransactionData(
    val amount: String,
    val upiId: String,
    val balance: String,
    val recipient: String,
    val status: String,
    val rawMessage: String
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: ""
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(amount)
        parcel.writeString(upiId)
        parcel.writeString(balance)
        parcel.writeString(recipient)
        parcel.writeString(status)
        parcel.writeString(rawMessage)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<TransactionData> {
        override fun createFromParcel(parcel: Parcel): TransactionData {
            return TransactionData(parcel)
        }

        override fun newArray(size: Int): Array<TransactionData?> {
            return arrayOfNulls(size)
        }
    }
}
