package com.myl.learnvideo.syncplayer.decode

interface IDecoder {
    fun prepare(): Boolean
    fun setDataSource()

}