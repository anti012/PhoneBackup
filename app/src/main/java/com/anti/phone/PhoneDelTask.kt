package com.anti.phone

import com.anti.phonebackup.MainActivity
import com.anti.phonebackup.R
import com.anti.tools.CommHelper
import com.anti.tools.ProgressTask


class PhoneDelTask(val m: MainActivity) : ProgressTask(m) {
    override fun doInBackground(vararg s: String?): String {
        result += m.getString(R.string.phone_all_backup_delete_success)
        CommHelper.deleFiles(CommHelper.SMS_DIR, CommHelper.SMS_PREFIX)
        CommHelper.deleFiles(CommHelper.CAT_DIR, CommHelper.CAT_PREFIX)
        CommHelper.deleFiles(CommHelper.CALL_DIR, CommHelper.CALL_PREFIX)
        CommHelper.log("i", "所有备份文件删除完成")
        return m.getString(R.string.phone_all_delete_done)
    }

    override fun onPostExecute(results: String?) {
        super.onPostExecute(results)
        CommHelper.makeDialog(main, results ?: "", result, main.getString(R.string.dialog_got_it)).show()
        m.initView()
    }
}