package com.myl.learnvideo.syncplayer.decode

/**
 *
 * @description
 * @author jadenma
 * @date 2022/2/11 8:44 下午
 */
interface IMediaTimeProvider {
    fun getNowUs(): Long
    fun getRealTimeUsForMediaTime(mediaTimeUs: Long): Long
    fun getVsyncDurationNs(): Long
}