package com.anti.tools

import android.os.AsyncTask
import com.anti.phonebackup.MainActivity
import com.anti.phonebackup.R


abstract class ProgressTask(val main: MainActivity) : AsyncTask<String, Int, String>() {
    var result = ""
    private var pText = ""
    private var pStatus = ""

    // 更新进度条的信号指示值
    private val pUpdateProgress = 100
    private val pUpdateText = 101
    private val pUpdateStatus = 102
    private val pReset = 103

    override fun onPreExecute() {
        main.progressDialog.show()
    }

    override fun doInBackground(vararg s: String?): String {
        return ""
    }

    override fun onProgressUpdate(vararg values: Int?) {
        if (values.isNotEmpty()) {
            when (values[0]) {
                pUpdateProgress -> main.progressBar.progress = values[1] ?: main.progressBar.progress
                pUpdateText -> main.progressText.text = pText
                pUpdateStatus -> main.progressStatus.text = pStatus
                pReset -> {
                    main.progressBar.progress = 0
                    main.progressBar.max = values[1] ?: main.progressBar.max
                    main.progressStatus.text = main.getString(R.string.progress_tv_status)
                }
            }
        }
    }

    override fun onPostExecute(results: String?) {
        main.progressDialog.dismiss()
    }

    // 以下4项的意义已在onProgressUpdate(vararg values: Int?) 里说明
    val updatePText = { text: String ->
        pText = text
        publishProgress(pUpdateText)
    }
    val updatePStatus = { text: String ->
        pStatus = text
        publishProgress(pUpdateStatus)
    }
    val updateProgress = { progress: Int ->
        publishProgress(pUpdateProgress, progress)
    }
    val resetProgress = { max: Int ->
        publishProgress(pReset, max)
    }

}