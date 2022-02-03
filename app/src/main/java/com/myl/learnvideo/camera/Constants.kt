package com.myl.learnvideo.camera

import android.content.Context
import android.os.Environment

object Constants {
    //    val CAMERA_PATH = Environment.get().absolutePath +
    fun getCachePath(context: Context): String {
        return context.getExternalFilesDir("/LearnVideo")?.absolutePath?.orEmpty().toString()
    }
}