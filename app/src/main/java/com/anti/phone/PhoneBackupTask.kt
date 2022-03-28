package com.anti.phone

import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import com.anti.phone.PhoneHelper.Companion.call
import com.anti.phone.PhoneHelper.Companion.cat
import com.anti.phone.PhoneHelper.Companion.sms
import com.anti.phonebackup.MainActivity
import com.anti.phonebackup.R
import com.anti.tools.CommHelper
import com.anti.tools.ProgressTask
import com.google.gson.Gson
import java.io.File


class PhoneBackupTask(val m: MainActivity) : ProgressTask(m) {
    override fun doInBackground(vararg s: String?): String {
        result += m.getString(R.string.phone_backup_save_dir, CommHelper.APP_DIR_NAME)
        doBackup(sms)
        doBackup(cat)
        doBackup(call)
        return m.getString(R.string.phone_all_backup_done)
    }

    override fun onPostExecute(results: String?) {
        super.onPostExecute(results)
        CommHelper.makeDialog(m, results ?: "", result, m.getString(R.string.dialog_got_it)).show()
        m.initView()
    }

    private fun doBackup(type: String): Boolean {
        result += when (type) {
            sms -> "1: "
            cat -> "2: "
            call -> "3: "
            else -> "unKnown: "
        }
        var label = "unKnown"
        var uri = Uri.EMPTY
        var cols: Array<String>? = null
        var filePath = ""
        when (type) {
            sms -> {
                label = m.getString(R.string.phone_sms_label)
                uri = PhoneHelper.smsUri
                cols = PhoneHelper.SMS_COLS
                filePath = "${CommHelper.SMS_DIR}/${CommHelper.SMS_PREFIX}_${CommHelper.date(CommHelper.FILE_DATE_FORMAT)}.json"
            }
            cat -> {
                label = m.getString(R.string.phone_cat_label)
                uri = PhoneHelper.catUri
                cols = null
                filePath = "${CommHelper.CAT_DIR}/${CommHelper.CAT_PREFIX}_${CommHelper.date(CommHelper.FILE_DATE_FORMAT)}.vcf"
            }
            call -> {
                label = m.getString(R.string.phone_call_label)
                uri = PhoneHelper.callLogUri
                cols = PhoneHelper.CALLLOG_COL
                filePath = "${CommHelper.CALL_DIR}/${CommHelper.CALL_PREFIX}_${CommHelper.date(CommHelper.FILE_DATE_FORMAT)}.json"
            }
        }

        // 读取信息
        updatePText(m.getString(R.string.phone_backup_reading_text, label))
        resetProgress(100)
        val cursor: Cursor
        try {
            cursor = m.contentResolver.query(uri, cols, null, null, null)!!
        } catch (e: Exception) {
            CommHelper.log("e", "读取$label 出错：", e)
            result += m.getString(R.string.phone_backup_read_error, label, e.message)
            return false
        }
        if (cursor.count == 0) {
            CommHelper.log("i", "跳过备份：$label 数量为 0")
            result += m.getString(R.string.phone_backup_read_zero, label)
            return false
        }

        updatePText(m.getString(R.string.phone_backup_getting_text, label))
        resetProgress(cursor.count)
        // 保存信息到数组
        val totalCount = cursor.count
        val lists: List<Any> = when (type) {
            sms -> smsBackup(cursor)
            cat -> catBackup(cursor)
            call -> callBackup(cursor)
            else -> listOf()
        }
        cursor.close()

        updatePText(m.getString(R.string.phone_backup_save_file_text, label))
        resetProgress(1)
        // 保存到文件
        val file = File(filePath)
        try {
            if (type == sms || type == call) {
                file.writeText(Gson().toJson(lists))
            } else if (type == cat) {
                lists.forEach { file.appendText(it.toString()) }
            }
        } catch (e: Exception) {
            CommHelper.log("e", "将$label 保存到文件时出错", e)
            result += m.getString(R.string.phone_backup_save_file_error, label, e.message)
            return false
        }
        CommHelper.log("i", "$label 备份完成（${lists.size}）：${file.absolutePath}")
        result += m.getString(R.string.phone_backup_done, label, lists.size, totalCount)
        updatePText(m.getString(R.string.phone_backup_done, label, lists.size, totalCount))
        return true
    }

    private fun smsBackup(cursor: Cursor): List<Any> {
        val smsList = arrayListOf<PhoneHelper.MySMS>()
        var sms: PhoneHelper.MySMS
        while (cursor.moveToNext()) {
            if (cursor.getString(1).isNullOrEmpty()) continue
            sms = PhoneHelper.MySMS(
                cursor.getString(0) ?: "",
                (cursor.getString(1) ?: "").replace("\u0000", ""),  //部分10010发的短信结尾包含不可见字符：\u0000，此处去除
                cursor.getLong(2),
                cursor.getLong(3),
                cursor.getInt(4),
                cursor.getInt(5),
                cursor.getString(6) ?: "",
                cursor.getInt(7),
                cursor.getInt(8)
            )
            smsList.add(sms)
            updatePStatus(m.getString(R.string.phone_progress_status, cursor.position + 1, cursor.count))
            updateProgress(cursor.position + 1)
        }
        return smsList.toSet().toList()
    }

    private fun catBackup(cursor: Cursor): List<Any> {
        val catList = mutableListOf<String>()
        var lookupKey: String
        var uri: Uri
        var content: String
        try {
            while (cursor.moveToNext()) {
                lookupKey = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY))
                uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_VCARD_URI, lookupKey)
                content = m.contentResolver.openAssetFileDescriptor(uri, "r")?.createInputStream()?.bufferedReader()?.readText().toString()
                catList.add(content)
                updatePStatus(m.getString(R.string.phone_progress_status, cursor.position + 1, cursor.count))
                updateProgress(cursor.position + 1)
            }
        } catch (e: Exception) {
            CommHelper.log("e", "备份联系人时发生异常：", e)
            result += m.getString(R.string.phone_backup_save_list_error, m.getString(R.string.phone_cat_label), e.message)
        }
        return catList.toSet().toList()
    }

    private fun callBackup(cursor: Cursor): List<Any> {
        val callLogList = arrayListOf<PhoneHelper.MyCallLog>()
        var callLog: PhoneHelper.MyCallLog
        while (cursor.moveToNext()) {
            callLog = PhoneHelper.MyCallLog(
                cursor.getInt(0),
                cursor.getString(1) ?: "",
                cursor.getLong(2),
                cursor.getLong(3),
                cursor.getInt(4),
                cursor.getInt(5),
                cursor.getString(6) ?: ""
            )
            callLogList.add(callLog)
            updatePStatus(m.getString(R.string.phone_progress_status, cursor.position + 1, cursor.count))
            updateProgress(cursor.position + 1)
        }
        return callLogList.toSet().toList()
    }
}