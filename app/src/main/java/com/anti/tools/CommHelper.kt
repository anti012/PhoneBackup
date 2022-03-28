package com.anti.tools

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.StrictMode
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat
import com.anti.phonebackup.MyApplication.Companion.context
import java.io.File
import java.text.SimpleDateFormat
import java.util.*


class CommHelper {
    companion object {

        private val DEBUG_TAG = "[PhoneBackup]"
        val APP_DIR_NAME = "PhoneBackup"

        // 应用目录、备份目录、日志文件路径
        private val APP_ROOT_DIR = Environment.getExternalStorageDirectory().canonicalPath + "/$APP_DIR_NAME"
        val CAT_DIR = "$APP_ROOT_DIR/cats"
        val SMS_DIR = "$APP_ROOT_DIR/sms"
        val CALL_DIR = "$APP_ROOT_DIR/calls"

        val LOG_PATH = "${context.filesDir}/$APP_DIR_NAME.log"
        // 备份文件前缀
        val CAT_PREFIX = "cat"
        val SMS_PREFIX = "sms"
        val CALL_PREFIX = "call"

        val FILE_DATE_FORMAT = "yyyy_MM_dd__HH_mm_ss"
        private val DATE_FORMAT = "yyyy-MM-dd HH:mm:ss"

        /**
         * 记录日志
         * @param type 分4个等级：i,w,e,v
         * @param text 说明信息
         * @param ex 异常（默认为null）
         */
        fun log(type: String, text: String, ex: Exception? = null, toFile: Boolean = true) {
            when (type.toLowerCase(Locale.ROOT)) {
                "i" -> Log.i(DEBUG_TAG, text)
                "w" -> Log.w(DEBUG_TAG, text)
                "e" -> Log.e(DEBUG_TAG, text, ex)
                else -> Log.v(DEBUG_TAG, text)
            }
            if (toFile) {
                File(LOG_PATH).appendText("${date()} ${type.toUpperCase(Locale.ROOT)} -> $text\n${ex?.stackTrace?.toList()?: ""}\n")
            }
        }

        fun date(format: String = DATE_FORMAT): String {
            return SimpleDateFormat(format, Locale.getDefault()).format(Date())
        }

        /**
         * 列出指定目录下的指定文件
         */
        fun listFiles(dir: String, startWith: String = ""): List<String> {
            val file = File(dir)
            try {
                if (!file.exists()) {
                    log("i", "指定目录不存在：$dir")
                    return listOf()
                }
                val files = file.listFiles()
                files ?: return listOf()
                return files.filter {
                    it.isFile && it.name.startsWith(startWith)
                }.map {
                    it.name
                }.sortedByDescending {
                    it
                }
            } catch (e: Exception) {
                log("e", "获取目录下的文件出错：$dir", e)
            }
            return listOf()
        }

        /**
         * 列出指定目录下的指定文件Path
         */
        fun listFilesPath(dir: String, startWith: String = ""): List<String> {
            val file = File(dir)
            try {
                if (!file.exists()) {
                    log("i", "指定目录不存在：$dir")
                    return listOf()
                }
                val files = file.listFiles()
                files ?: return listOf()
                return files.filter {
                    it.isFile && it.name.startsWith(startWith)
                }.map {
                    it.canonicalPath
                }.sortedByDescending {
                    it
                }
            } catch (e: Exception) {
                log("e", "获取目录下的文件出错：$dir", e)
            }
            return listOf()
        }

        fun listLatestFilePath(dir: String, startWith: String = ""): String {
            val t = listFilesPath(dir, startWith)
            if (!t.isEmpty()) return t[0]
            else return ""
        }

        /**
         * 删除指定目录下指定文件
         */
        fun deleFiles(dir: String, startWith: String): Boolean {
            val file = File(dir)
            try {
                file.listFiles().filter {
                    it.isFile && it.name.startsWith(startWith)
                }.forEach {
                    it.delete()
                }
                return true
            } catch (e: Exception) {
                log("e", "文件删除失败：", e)
            }
            return false
        }

        /**
         * 获取目录下最近一次备份（根据该文件名转为）的时间
         */
        fun backupFileDate(dir: String, startWith: String): String? {
            var date: String? = null
            val files = listFiles(dir, startWith)
            if (files.isEmpty()) return date
            val fileName = files.first()
            try {
                val dateString = fileName.substring(fileName.indexOf("_") + 1, fileName.lastIndexOf("."))
                val fileDate = SimpleDateFormat(FILE_DATE_FORMAT, Locale.getDefault()).parse(dateString)
                date = SimpleDateFormat(DATE_FORMAT, Locale.getDefault()).format(fileDate)
            } catch (e: Exception) {
                log("e", "转换文件日期为可读时出错：", e)
            }
            return date
        }

        /**
         * 生成对话框
         */
        fun makeDialog(activity: Activity, title: String, msg: String, positiveText: String? = null, positive: DialogInterface.OnClickListener? = null,
                       negativeText: String? = null, negative: DialogInterface.OnClickListener? = null, cancelAble: Boolean = true): AlertDialog {
            return AlertDialog.Builder(activity)
                    .setTitle(title)
                    .setMessage(msg)
                    .setPositiveButton(positiveText, positive)
                    .setNegativeButton(negativeText, negative)
                    .setCancelable(cancelAble)
                    .create()
        }

        /**
         * Android N以上禁止向您的应用外公开 file://URI。这里是准许公开
         */
        fun enableFileURL() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    val m = StrictMode::class.java.getMethod("disableDeathOnFileUriExposure")
                    m.invoke(null)
                } catch (e: Exception) {
                    log("e", "准许公开'file:///URI'出错：${e.message}", e)
                }
            }
        }

        /**
         * 检查默认短信应用
         */
        fun cheackDefaultSms(activity: Activity): Boolean {
            return Telephony.Sms.getDefaultSmsPackage(activity).equals(activity.packageName)
        }

        /**
         * 改变默认短信应用
         */
        fun changeSmsApp(activity: Activity, smsApp: String) {
            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, smsApp)
            ContextCompat.startActivity(activity, intent, null)
        }

        /**
         * 判断包是否已安装
         */
        fun hadInstalled(activity: Activity, pkname: String): Boolean {
            var packageInfo: PackageInfo? = null
            try {
                packageInfo = activity.packageManager.getPackageInfo(pkname, 0)
            } catch (notFound: PackageManager.NameNotFoundException) {
                log("w", "应用包未安装：$pkname")
            } catch (e: Exception) {
                log("e", "获取包是否已安装时出错：${e.message}", e)
            }
            return packageInfo != null
        }
    }
}