package com.anti.phone

import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony

class PhoneHelper {
    companion object {

        val sms = "sms"
        val cat = "cat"
        val call = "call"

        //val smsUri = Uri.parse("content://sms")
        val smsUri = Telephony.Sms.Inbox.CONTENT_URI

        val SMS_COLS = arrayOf(
                Telephony.TextBasedSmsColumns.ADDRESS,
                Telephony.TextBasedSmsColumns.BODY,
                Telephony.TextBasedSmsColumns.DATE,
                Telephony.TextBasedSmsColumns.DATE_SENT,
                Telephony.TextBasedSmsColumns.LOCKED,
                Telephony.TextBasedSmsColumns.READ,
                Telephony.TextBasedSmsColumns.SERVICE_CENTER,
                Telephony.TextBasedSmsColumns.STATUS,
                Telephony.TextBasedSmsColumns.TYPE
        )

        val catUri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        //val catUri = Uri.parse("content://com.android.contacts/contacts")

        val callLogUri = CallLog.Calls.CONTENT_URI
        //val callLogUri = Uri.parse("content://call_log/calls")
        val CALLLOG_COL = arrayOf(
                CallLog.Calls.NEW,
                CallLog.Calls.NUMBER,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
                CallLog.Calls.TYPE,
                CallLog.Calls.IS_READ,
                CallLog.Calls.CACHED_NAME
        )
    }

    data class MySMS(
            val address: String,
            val body: String,
            val date: Long = 0,
            val date_sent: Long = 0,
            val locked: Int = -1,
            val read: Int = -1,
            val service_center: String = "",
            val status: Int = -1,
            val type: Int = -1
    )

    data class MyCallLog(
            val new: Int,
            val number: String,
            val date: Long = -1,
            val duration: Long = -1,
            val type: Int,
            val is_read: Int = -1,
            val name: String = ""
    )
}