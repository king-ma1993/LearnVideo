package com.myl.learnvideo

import android.content.Context

object Constants {
    //    val CAMERA_PATH = Environment.get().absolutePath +
    fun getCachePath(context: Context): String {
        return context.getExternalFilesDir("/LearnVideo")?.absolutePath?.orEmpty().toString()
    }

    const val MIME_TYPE_AUDIO = "audio/"
    const val MIME_TYPE_VIDEO = "video/"


}