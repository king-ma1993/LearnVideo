package com.myl.learnvideo.utils

import android.content.Context
import android.text.TextUtils
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.logging.Logger
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

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

    fun copyAssetFile(context: Context, assetFile: String, destFile: String): Boolean {
        val assetManager = context.assets
        try {
            assetManager.open(assetFile).use { input ->
                val outFile = File(destFile)
                val parent = outFile.parentFile
                if (!parent.exists()) {
                    val isMKDirs = parent.mkdirs()
                }
                FileOutputStream(outFile).use { out ->
                    copyFile(input, out)
                    out.flush()
                }
                return true
            }
        } catch (e: IOException) {
            return false
        }
    }

    private fun copyFile(inputStream: InputStream?, os: OutputStream?): Boolean {
        if (inputStream == null || os == null) {
            return false
        }
        try {
            val bs = ByteArray(2 * 1024 * 1024)
            var len: Int
            while (inputStream.read(bs).also { len = it } > 0) {
                os.write(bs, 0, len)
            }
            os.flush()
        } catch (e: Exception) {
        }
        return true
    }

    fun delete(path: String) {
        if (TextUtils.isEmpty(path)) {
            return
        }
        val f = File(path)
        delete(f)
    }

    /**
     * 递归删除文件及文件夹
     */
    fun delete(file: File?) {
        if (file == null) {
            return
        }
        if (file.exists().not()) {
            return
        }
        if (file.isFile) {
            val result = file.delete()
            return
        }
        if (file.isDirectory) {
            val childFiles = file.listFiles()
            if (childFiles == null || childFiles.isEmpty()) {
                val result = file.delete()
                return
            }
            for (i in childFiles.indices) {
                delete(childFiles[i])
            }
            file.delete()
        }
    }

    fun unzip(filePath: String, location: String): Boolean {
        var result = false
        try {
            result = File(filePath).run {
                if (exists()) {
                    unzip(File(location))
                    true
                } else {
                    false
                }
            }
        } catch (e: IOException) {
        }
        return result
    }

    fun File.unzip(unzipLocationRoot: File? = null) {

        val rootFolder = unzipLocationRoot ?: File(parentFile.absolutePath + File.separator + nameWithoutExtension)
        if (!rootFolder.exists()) {
            rootFolder.mkdirs()
        }

        ZipFile(this).use { zip ->
            zip
                .entries()
                .asSequence()
                .map {
                    val outputFile = File(rootFolder.absolutePath + File.separator + it.name)
                    ZipIO(it, outputFile)
                }
                .map {
                    it.output.parentFile?.run{
                        if (!exists()) mkdirs()
                    }
                    it
                }
                .filter { !it.entry.isDirectory }
                .forEach { (entry, output) ->
                    zip.getInputStream(entry).use { input ->
                        output.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
        }

    }
}

data class ZipIO (val entry: ZipEntry, val output: File)