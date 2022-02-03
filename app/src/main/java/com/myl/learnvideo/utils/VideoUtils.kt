package com.myl.learnvideo.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import java.io.File

object PhotoUtils {

    fun saveFile2Gallery(context: Context, fromPath: String) {
        val fromFile = File(fromPath)
        try {
            MediaStore.Images.Media.insertImage(
                context.contentResolver,
                fromFile.absolutePath, fromFile.name, null
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
        context.sendBroadcast(
            Intent(
                Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                Uri.fromFile(File(fromFile.getParent()))
            )
        )
    }
}
