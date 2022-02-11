package com.myl.learnvideo.syncplayer.decode

import android.media.MediaFormat
import android.net.Uri

interface IDecoder {
    fun prepare(): Boolean
    fun setDataSource(uri: Uri)
    fun addTrack(trackIndex: Int, format: MediaFormat): Boolean
}