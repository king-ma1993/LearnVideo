package com.myl.learnvideo.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.media.MediaScannerConnection.OnScanCompletedListener
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException


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
                Uri.fromFile(File(fromFile.parent))
            )
        )
    }

    /**
     *保存bitmap
     */
    fun saveBitmap2Gallery(context: Context, bitmap: Bitmap): Boolean {
        MediaStore.Images.Media.insertImage(context.contentResolver, bitmap, "title", "desc")
        return true
    }


}
