package com.anti.phone

import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.webkit.MimeTypeMap
import com.anti.phone.PhoneHelper.Companion.CALLLOG_COL
import com.anti.phone.PhoneHelper.Companion.SMS_COLS
import com.anti.phone.PhoneHelper.Companion.call
import com.anti.phone.PhoneHelper.Companion.callLogUri
import com.anti.phone.PhoneHelper.Companion.cat
import com.anti.phone.PhoneHelper.Companion.sms
import com.anti.phone.PhoneHelper.Companion.smsUri
import com.anti.phonebackup.MainActivity
import com.anti.phonebackup.R
import com.anti.tools.CommHelper
import com.anti.tools.ProgressTask
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File


class PhoneRestoreTask(val m: MainActivity, val filesMap: Map<String, String>) : ProgressTask(m) {
    override fun doInBackground(vararg s: String?): String {
        doRestore(sms, filesMap[sms] ?: "")
        doRestore(cat, filesMap[cat] ?: "")
        doRestore(call, filesMap[call] ?: "")
        return m.getString(R.string.phone_all_restore_done)
    }

    override fun onPostExecute(results: String?) {
        super.onPostExecute(results)
        CommHelper.makeDialog(m, results?: "", result, m.getString(R.string.dialog_got_it)).show()
        m.initView()
    }

    private fun doRestore(type: String, filePath: String): Boolean {
        result += when (type) {
            sms -> "1: "
            cat -> "2: "
            call -> "3: "
            else -> "unKnown: "
        }
        var label = "unKnown"
        var uri = Uri.EMPTY
        var cols: Array<String>? = null
        when (type) {
            sms -> {
                label = m.getString(R.string.phone_sms_label)
                uri = PhoneHelper.smsUri
                cols = PhoneHelper.SMS_COLS
            }
            cat -> {
                label = m.getString(R.string.phone_cat_label)
                uri = PhoneHelper.catUri
                cols = null
            }
            call -> {
                label = m.getString(R.string.phone_call_label)
                uri = PhoneHelper.callLogUri
                cols = PhoneHelper.CALLLOG_COL
            }
        }
        if(filePath.equals("")){
            result += m.getString(R.string.file_not_selected, label)
            return false
        }
        val file = File(filePath)
        if (!file.exists()) {
            CommHelper.log("e", "指定的$label 备份文件不存在：${file.absolutePath}")
            result += m.getString(R.string.file_not_exist, label, file.absolutePath)
            return false
        }

        //读取已存在文件防止重复添加
        val cursor: Cursor
        try {
            cursor = m.contentResolver.query(uri, cols, null, null, null)!!
        } catch (e: Exception) {
            CommHelper.log("e", "读取$label 出错：", e)
            return false
        }
        if (cursor.count == 0) {
            CommHelper.log("i", "跳过备份：$label 数量为 0")
        }
        val lists: List<Any> = when (type) {
            sms -> smsBackup(cursor)
            call -> callBackup(cursor)
            else -> listOf()
        }
        cursor.close()

        // 读取、解析备份文件
        updatePText(m.getString(R.string.phone_restore_reading_text, label))
        resetProgress(100)
        try {
            when (type) {
                sms -> {
                    val smsListType = object : TypeToken<List<PhoneHelper.MySMS>>() {}.type
                    smsRestore(Gson().fromJson(file.readText(), smsListType), lists)
//                    CommHelper.changeSmsApp(m, "com.android.messaging")
                }
                cat -> {
                    catRestore(file)
                }
                call -> {
                    val callListType = object : TypeToken<List<PhoneHelper.MyCallLog>>() {}.type
                    callRestore(Gson().fromJson(file.readText(), callListType), lists)
                }
            }
        } catch (e: Exception) {
            CommHelper.log("e", "读取、解析备份文件时出错", e)
            result += m.getString(R.string.phone_restore_parse_file_error, file.absolutePath, e.message)
            return false
        }
        return true
    }

    /**
     * 恢复短信
     */
    private fun smsRestore(smsList: List<PhoneHelper.MySMS>, oldList: List<Any>): Boolean {
        var restoreCount = 0
        var smsItem: ContentValues
        val multiItem = mutableListOf<ContentValues>()

        updatePText(m.getString(R.string.phone_restore_insert, m.getString(R.string.phone_sms_label)) +"  ")
        resetProgress(smsList.size)
        smsList.forEach continuing@{
            if (it.body.isEmpty()) return@continuing
            if (oldList.contains(it)) return@continuing
            smsItem = ContentValues()
            smsItem.put(SMS_COLS[0], it.address)
            smsItem.put(SMS_COLS[1], it.body)
            smsItem.put(SMS_COLS[2], it.date)
            smsItem.put(SMS_COLS[3], it.date_sent)
            smsItem.put(SMS_COLS[4], it.locked)
            smsItem.put(SMS_COLS[5], it.read)
            smsItem.put(SMS_COLS[6], it.service_center)
            smsItem.put(SMS_COLS[7], it.status)
            smsItem.put(SMS_COLS[8], it.type)
            multiItem.add(smsItem)

            // 此处需要判断是否是申请权限的那次插入，因为在插入短信时才弹出权限界面，而此时无论是否授予，之前的插入请求都会丢失
            if (restoreCount == 0) {
                try {
                    val newUri = m.contentResolver.insert(smsUri, multiItem[0])
                    if (newUri.toString() != "content://sms/0") {
                        CommHelper.log("i", "第一条短信insert成功")
                        multiItem.removeAt(0)
                    } else {
                        CommHelper.log("i", "第一条短信insert失败")
                    }
                } catch (e: Exception) {
                    CommHelper.log("e", "短信插入出错", e)
                    result += m.getString(R.string.phone_restore_insert_error, m.getString(R.string.phone_sms_label), e.message)
                    return false
                }
            }
            restoreCount++
            updateProgress(restoreCount)
            updatePStatus(m.getString(R.string.phone_progress_status, restoreCount, smsList.size))
            if (multiItem.size >= 100) {
                try {
                    m.contentResolver.bulkInsert(smsUri, multiItem.toTypedArray())
                    multiItem.clear()
                } catch (e: Exception) {
                    CommHelper.log("e", "短信插入出错", e)
                    result += m.getString(R.string.phone_restore_insert_error, m.getString(R.string.phone_sms_label), e.message)
                    return false
                }
            }
        }
        try {
            m.contentResolver.bulkInsert(smsUri, multiItem.toTypedArray())
            multiItem.clear()
        } catch (e: Exception) {
            CommHelper.log("e", "短信插入出错", e)
            result += m.getString(R.string.phone_restore_insert_error, m.getString(R.string.phone_sms_label), e.message)
            return false
        }

        CommHelper.log("i", "短信恢复完成：$restoreCount/${smsList.size}")
        result += m.getString(R.string.phone_restore_done, m.getString(R.string.phone_sms_label), restoreCount, smsList.size)
        return true
    }

    private fun catRestore(file: File): Boolean {
        // 调用系统应用，恢复联系人
        // 自动调用联系人APP导入联系人：https://stackoverflow.com/questions/22205378/open-vcf-vcard-file-directly-with-contacts-app
        val vcfMimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension("vcf")
        val i = Intent()
        i.action = Intent.ACTION_VIEW
        i.setDataAndType(Uri.fromFile(file), vcfMimeType)
        try {
            val resolveInfos = m.applicationContext.packageManager.queryIntentActivities(i, 0)
            for (resolveInfo in resolveInfos) {
                val activityInfo = resolveInfo.activityInfo ?: continue
                val packageName = activityInfo.packageName
                val name = activityInfo.name
                CommHelper.log("i", "可打开.vcf文件的组件信息：$packageName, $name")
                // Find the needed Activity based on Android source files: http://grepcode.com/search?query=ImportVCardActivity&start=0&entity=type&n=
                if (packageName != null && packageName.contains("contacts") && name != null && name.contains("ImportVCardActivity")) {
                    i.`package` = packageName
                    break
                }
            }
            m.startActivityForResult(i, 100)
        } catch (e: Exception) {
            CommHelper.log("e", "联系人恢复失败：", e)
            result += m.getString(R.string.phone_restore_cat_error, e.message)
            return false
        }

        CommHelper.log("i", "联系人恢复完成")
        result += m.getString(R.string.phone_restore_done_no_result, m.getString(R.string.phone_cat_label))
        return true
    }

    private fun callRestore(callList: List<PhoneHelper.MyCallLog>, oldList: List<Any>): Boolean {
        var restoreCount = 0
        var callItem: ContentValues
        val multiItem = mutableListOf<ContentValues>()
        updatePText(m.getString(R.string.phone_restore_insert, m.getString(R.string.phone_call_label)))
        resetProgress(callList.size)
        callList.forEach continuing@{
            if (oldList.contains(it)) return@continuing
            callItem = ContentValues()
            callItem.put(CALLLOG_COL[0], it.new)
            callItem.put(CALLLOG_COL[1], it.number)
            callItem.put(CALLLOG_COL[2], it.date)
            callItem.put(CALLLOG_COL[3], it.duration)
            callItem.put(CALLLOG_COL[4], it.type)
            callItem.put(CALLLOG_COL[5], it.is_read)
            callItem.put(CALLLOG_COL[6], it.name)
            multiItem.add(callItem)
            restoreCount++

            updateProgress(restoreCount)
            updatePStatus(m.getString(R.string.phone_progress_status, restoreCount, callList.size))
            if (multiItem.size >= 300) {
                try {
                    m.contentResolver.bulkInsert(callLogUri, multiItem.toTypedArray())
                    multiItem.clear()
                } catch (e: Exception) {
                    CommHelper.log("e", "通话记录恢复失败", e)
                    result += m.getString(R.string.phone_restore_insert_error, m.getString(R.string.phone_call_label), e.message)
                    return false
                }
            }
        }
        try {
            m.contentResolver.bulkInsert(callLogUri, multiItem.toTypedArray())
            multiItem.clear()
        } catch (e: Exception) {
            CommHelper.log("e", "通话记录恢复失败", e)
            result += m.getString(R.string.phone_restore_insert_error, m.getString(R.string.phone_call_label), e.message)
            return false
        }
        CommHelper.log("i", "通话记录恢复完成：$restoreCount/${callList.size}")
        result += m.getString(R.string.phone_restore_done, m.getString(R.string.phone_call_label), restoreCount, callList.size)
        return true
    }

    private fun smsBackup(cursor: Cursor): List<PhoneHelper.MySMS> {
        val smsList = arrayListOf<PhoneHelper.MySMS>()
        var sms: PhoneHelper.MySMS
        if (cursor.count == 0) return smsList
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
        }
        return smsList.toSet().toList()
    }

    private fun callBackup(cursor: Cursor): List<PhoneHelper.MyCallLog> {
        val callLogList = arrayListOf<PhoneHelper.MyCallLog>()
        var callLog: PhoneHelper.MyCallLog
        if (cursor.count == 0) return callLogList
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
        }
        return callLogList.toSet().toList()
    }

}