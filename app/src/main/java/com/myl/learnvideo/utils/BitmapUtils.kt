package com.myl.learnvideo.utils

import android.graphics.Bitmap
import android.graphics.Matrix

object BitmapUtils {
    /**
     * 镜像
     * @param bitmap
     * @return
     */
    fun mirror(bitmap: Bitmap): Bitmap {
        val matrix = Matrix()
        matrix.postScale(-1f, 1f)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * 旋转图片
     * @param bitmap
     * @param degress
     * @return
     */
    fun rotate(bitmap: Bitmap, degress: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degress)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}