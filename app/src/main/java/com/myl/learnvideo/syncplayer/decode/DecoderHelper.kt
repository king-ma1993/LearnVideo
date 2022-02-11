package com.myl.learnvideo.syncplayer.decode

import android.media.MediaFormat

/**
 *
 * @description
 * @author king-ma1993
 * @date 2022/2/10 8:13 下午
 */
class DecoderHelper {

    fun getMediaFormatInteger(format: MediaFormat, key: String?): Int {
        key?.apply {
            return if (format.containsKey(key)) format.getInteger(key) else 0
        }
        return 0
    }


}