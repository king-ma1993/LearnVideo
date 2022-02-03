package com.myl.learnvideo.utils

import android.text.TextUtils
import java.io.File

object FileUtils {
    /**
     * 判断是否文件夹
     * @param directory 文件夹路径
     */
    fun isDirectory(directory: String?): Boolean {
        if (directory.isNullOrEmpty()) {
            return false
        }
        val file = File(directory)
        if (!file.exists() || !file.isDirectory) {
            return false
        }
        return true
    }

    /**
     * 剪切文件
     * @param oldPath
     * @param newPath
     * @return
     */
    fun moveFile(oldPath: String?, newPath: String?): Boolean {
        if (TextUtils.isEmpty(oldPath)) {
            return false
        }
        if (TextUtils.isEmpty(newPath)) {
            return false
        }
        val file = File(oldPath)
        return file.renameTo(File(newPath))
    }
}