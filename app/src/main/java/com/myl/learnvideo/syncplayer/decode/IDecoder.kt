package com.myl.learnvideo.syncplayer.decode

import android.media.MediaFormat

interface IDecoder {
    fun prepare(): Boolean
    fun setDataSource(path: String)
    fun addTrack(trackIndex: Int, format: MediaFormat): Boolean
    fun start()
    fun doSomeWork()
    fun pause()
    fun flush()
    fun release()
    fun isEnded(): Boolean
}