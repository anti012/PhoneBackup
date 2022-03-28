package com.anti.phonebackup

import android.Manifest
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.anti.phone.PhoneBackupTask
import com.anti.phone.PhoneDelTask
import com.anti.phone.PhoneHelper
import com.anti.phone.PhoneRestoreTask
import com.anti.tools.CommHelper
import permissions.dispatcher.NeedsPermission
import permissions.dispatcher.RuntimePermissions
import java.io.File


@RuntimePermissions
class MainActivity : AppCompatActivity(), View.OnClickListener {
    private lateinit var bnBackup: Button
    private lateinit var bnDelete: Button
    private lateinit var bnRestore: Button

    private lateinit var tvCatBackup: TextView
    private lateinit var tvSMSBackup: TextView
    private lateinit var tvCallBackup: TextView

    private lateinit var spCatList: Spinner
    private lateinit var spSMSList: Spinner
    private lateinit var spCallList: Spinner

    lateinit var progressDialog: AlertDialog
    lateinit var progressBar: ProgressBar
    lateinit var progressText: TextView
    lateinit var progressStatus: TextView

    private var exitTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindEvent()
        loadProgressDialog()
        initView()
        initAppFileWithPermissionCheck()
        CommHelper.enableFileURL()
    }

    private fun bindEvent() {
        bnBackup = findViewById(R.id.phone_bn_backup)
        bnDelete = findViewById(R.id.phone_bn_backup_delete)
        bnRestore = findViewById(R.id.phone_bn_restore)

        tvSMSBackup = findViewById(R.id.phone_tv_backup_sms)
        tvCatBackup = findViewById(R.id.phone_tv_backup_cat)
        tvCallBackup = findViewById(R.id.phone_tv_backup_call)

        spSMSList = findViewById(R.id.spSmsFileList)
        spCatList = findViewById(R.id.spCatFileList)
        spCallList = findViewById(R.id.spCallFileList)

        bnBackup.setOnClickListener(this)
        bnDelete.setOnClickListener(this)
        bnRestore.setOnClickListener(this)
    }

    override fun onClick(v: View) {
        when (v.id) {

            R.id.phone_bn_backup -> {
                doBackupWithPermissionCheck()
            }

            R.id.phone_bn_backup_delete -> {
                doBackupDeleteWithPermissionCheck()
            }

            R.id.phone_bn_restore -> {
                doRestoreWithPermissionCheck()
            }
        }
    }

    fun initView() {
        // 显示最后一次备份的时间
        val noBackupTip = getString(R.string.phone_no_backup)
        val noChoice = getString(R.string.phone_no_choice)
        tvSMSBackup.text = getString(R.string.phone_tv_backup_sms_text, CommHelper.backupFileDate(CommHelper.SMS_DIR, CommHelper.SMS_PREFIX)
            ?: noBackupTip)
        tvCatBackup.text = getString(R.string.phone_tv_backup_cat_text, CommHelper.backupFileDate(CommHelper.CAT_DIR, CommHelper.CAT_PREFIX)
            ?: noBackupTip)
        tvCallBackup.text = getString(R.string.phone_tv_backup_call_text, CommHelper.backupFileDate(CommHelper.CALL_DIR, CommHelper.CALL_PREFIX)
            ?: noBackupTip)

        // 分类获取备份文件列表
        var smsList = CommHelper.listFiles(CommHelper.SMS_DIR, CommHelper.SMS_PREFIX)
        var catList = CommHelper.listFiles(CommHelper.CAT_DIR, CommHelper.CAT_PREFIX)
        var callList = CommHelper.listFiles(CommHelper.CALL_DIR, CommHelper.CALL_PREFIX)

        // 如果发现了对应类型的备份文件，则选中其最近的一次备份
        if (smsList.isNotEmpty()) spSMSList.setSelection(0)
        if (catList.isNotEmpty()) spCatList.setSelection(0)
        if (callList.isNotEmpty()) spCallList.setSelection(0)

        // 如果没有发现任何备份文件，则禁用删除、恢复按钮
        val haveBackup = smsList.isNotEmpty() || catList.isNotEmpty() || callList.isNotEmpty()
        bnDelete.isEnabled = haveBackup
        bnRestore.isEnabled = haveBackup

        // 添加不选择项，并绑定控件的adapter
        smsList = smsList.toMutableList()
        catList = catList.toMutableList()
        callList = callList.toMutableList()
        smsList.add(noChoice)
        catList.add(noChoice)
        callList.add(noChoice)

        // 将备份文件列表填充到对应的Spinner选择框中恢复。若无备份，则填充“没有备份”
        spSMSList.adapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1,
            if (smsList.size >= 2) smsList else listOf(noBackupTip))
        spCatList.adapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1,
            if (catList.size >= 2) catList else listOf(noBackupTip))
        spCallList.adapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1,
            if (callList.size >= 2) callList else listOf(noBackupTip))
    }

    fun loadProgressDialog() {
        val view = layoutInflater.inflate(R.layout.progress_dialog, null)
        val builder = AlertDialog.Builder(this).setView(view)
        progressBar = view.findViewById(R.id.progressBar)
        progressText = view.findViewById(R.id.progress_tv_text)
        progressStatus = view.findViewById(R.id.progress_tv_status)
        progressDialog = builder
            .setTitle(getString(R.string.progress_dialog_title))
            .setCancelable(false)
            .create()
    }

    @NeedsPermission(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    fun initAppFile() {
        val log = File(CommHelper.LOG_PATH)
        val contacts = File(CommHelper.CAT_DIR)
        val sms = File(CommHelper.SMS_DIR)
        val call = File(CommHelper.CALL_DIR)
        try {
            if (log.exists()) log.delete()
            if (!contacts.exists()) contacts.mkdirs()
            if (!sms.exists()) sms.mkdirs()
            if (!call.exists()) call.mkdirs()
            CommHelper.log("i", "初始化应用")
        } catch (e: Exception) {
            CommHelper.log("e", "初始化应用创建、删除文件时发生错误：", e)
        }
    }

    @NeedsPermission(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_SMS,
        Manifest.permission.READ_CONTACTS, Manifest.permission.READ_CALL_LOG)
    fun doBackup() {
        PhoneBackupTask(this).execute()
    }

    @NeedsPermission(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    fun doBackupDelete() {
        CommHelper.makeDialog(this,
            getString(R.string.phone_backup_delete_dialog_title),
            getString(R.string.phone_backup_delete_dialog_message),
            getString(R.string.phone_backup_delete_dialog_ok_title),
            { _, _ ->
                PhoneDelTask(this).execute()
            },
            getString(R.string.phone_backup_dialog_cancel_title))
            .show()
    }

    @NeedsPermission(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.WRITE_CALL_LOG,
        Manifest.permission.WRITE_CONTACTS)
    fun doRestore() {
        if(!CommHelper.cheackDefaultSms(this)) CommHelper.changeSmsApp(this, this.packageName)
        else{
            CommHelper.makeDialog(this,
                getString(R.string.phone_restore_ok_title),
                getString(R.string.phone_restore_ok_msg),
                getString(R.string.phone_restore_ok_text),
                { _, _ ->
                    val filesMap = mutableMapOf<String, String>()
                    if (spSMSList.selectedItem.toString().startsWith(CommHelper.SMS_PREFIX))
                        filesMap.put(PhoneHelper.sms, "${CommHelper.SMS_DIR}/${spSMSList.selectedItem}")
                    if (spCatList.selectedItem.toString().startsWith(CommHelper.CAT_PREFIX))
                        filesMap.put(PhoneHelper.cat, "${CommHelper.CAT_DIR}/${spCatList.selectedItem}")
                    if (spCallList.selectedItem.toString().startsWith(CommHelper.CALL_PREFIX))
                        filesMap.put(PhoneHelper.call, "${CommHelper.CALL_DIR}/${spCallList.selectedItem}")
                    PhoneRestoreTask(this, filesMap).execute()
                },
                getString(R.string.phone_backup_dialog_cancel_title)).show()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                return exitConfirm()
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun exitConfirm(): Boolean {
        if (System.currentTimeMillis() - exitTime <= 1500) {
            this.finish()
        } else {
            Toast.makeText(this, "再次返回退出应用", Toast.LENGTH_SHORT).show()
            exitTime = System.currentTimeMillis()
        }
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        onRequestPermissionsResult(requestCode, grantResults)
    }

}