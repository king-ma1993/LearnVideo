package com.myl.learnvideo.utils

import java.io.Closeable
import java.io.IOException

object CloseUtils {
    fun close(vararg closeables: Closeable?) {
        if (closeables != null) {
            for (closeable in closeables) {
                if (closeable != null) {
                    try {
                        closeable.close()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }
}