package com.myl.learnvideo.utils

import android.os.Build
import android.os.Environment
import java.io.File

object AlbumUtils {

    private const val PHONE_TYPE_VIVO = "vivo"
    private const val DIR_CAMERA_VIVO = "相机/"
    // Camera目录
    private const val DIR_CAMERA = "/Camera/"
    // vivo 手机存储/相机目录
    private val CAMERA_PATH_VIVO =
        Environment.getExternalStorageDirectory().absolutePath + File.separator + DIR_CAMERA_VIVO

    fun getAlbumPath(): String {
        val manufacturer = Build.MANUFACTURER
        val phoneType = manufacturer.toLowerCase()
        if (PHONE_TYPE_VIVO == phoneType && FileUtils.isDirectory(CAMERA_PATH_VIVO)) {
            return Environment.getExternalStorageDirectory().absolutePath + File.separator + DIR_CAMERA_VIVO
        }
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).absolutePath + DIR_CAMERA
    }
}