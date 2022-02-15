package com.myl.learnvideo.utils

import android.content.Context
import com.myl.learnvideo.utils.FileUtils.unzip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File

object ResCopyHelper {
    private const val ZIP_SUFFIX = ".zip"

    const val TEST_RES = "test_video"

    fun getTestDirPath(context: Context): String {
        return context.getExternalFilesDir(null)?.absolutePath ?: "/sdcard/learnVideo"
    }

    /**
     * 从asset目录拷贝LightSDK基础库和模型以及测试模板，后续会通过后台下发
     */
    fun copyAssets(context: Context) {
        if (File(getTestDirPath(context)).exists().not()) {
            File(getTestDirPath(context)).mkdirs()
        }
        copyAssetFiles(context, TEST_RES, getTestDirPath(context))
    }

    /**
     * 拷贝asset目录下zip文件到file私有目录并解压
     */
    private fun copyAssetAndUnzip(context: Context, srcFile: String, destDir: String) {
        val destFile = "$destDir${File.separator}$srcFile"
        GlobalScope.launch(Dispatchers.IO) {
            FileUtils.copyAssetFile(context, srcFile, destFile)
            if (srcFile.endsWith(ZIP_SUFFIX)) {
                File(destFile).unzip()
                FileUtils.delete(destFile)
            }
        }
    }

    /**
     * 拷贝asset目录下zip文件到file私有目录并解压
     */
    private fun copyAssetFiles(context: Context, srcFile: String, destDir: String) {
        GlobalScope.launch(Dispatchers.IO) {
            copyAssetFilesCore(context, srcFile, destDir)
        }
    }

    /**
     * 递归复制文件夹下所有文件
     */
    private fun copyAssetFilesCore(ctx: Context, srcFile: String, destDir: String) {
        val fileNames: Array<out String>? = ctx.assets.list(srcFile)
        if (fileNames?.isNotEmpty() == true) {
            // 拷贝目录
            val file = File(destDir)
            // 如果文件夹不存在，则递归创建
            file.mkdirs()

            fileNames.forEach {
                copyAssetFilesCore(ctx, "$srcFile${File.separator}$it", destDir)
            }
        } else {
            // 拷贝文件
            FileUtils.copyAssetFile(ctx, srcFile, "$destDir${File.separator}$srcFile")
        }
    }
}