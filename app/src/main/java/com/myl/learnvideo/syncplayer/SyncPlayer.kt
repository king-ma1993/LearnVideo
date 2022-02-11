package com.myl.learnvideo.syncplayer

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.SurfaceHolder
import com.myl.learnvideo.syncplayer.decode.AudioDecoder
import com.myl.learnvideo.syncplayer.decode.IMediaTimeProvider
import com.myl.learnvideo.syncplayer.decode.VideoDecoder
import com.myl.learnvideo.syncplayer.decode.VideoFrameReleaseTimeHelper

/**
 *
 * @description 使用mediaCodec+audioTrack实现的视频播放器
 * @author kingma-1993
 * @date 2022/2/11 3:45 下午
 */
class SyncPlayer(
    private val mSurfaceHolder: SurfaceHolder,
    private val context: Context
) : IMediaTimeProvider {

    private var mThreadStarted = false
    private var mThread: Thread? = null
    private var mFrameReleaseTimeHelper: VideoFrameReleaseTimeHelper = VideoFrameReleaseTimeHelper(context)
    private val audioDecoder = AudioDecoder(this)
    private val videoDecoder = VideoDecoder(this)
    private var mState: Int = STATE_IDLE
    var mDurationUs: Long = 0
    private var mDeltaTimeUs: Long = 0

    companion object {
        private const val TAG = "SyncPlayer"
        private const val STATE_IDLE = 1
        private const val STATE_PREPARING = 2
        private const val STATE_PLAYING = 3
        private const val STATE_PAUSED = 4
        private const val INVALID_TIME = -1L
    }

    fun setDataSource(uri: Uri) {
        audioDecoder.setDataSource(uri)
        videoDecoder.setDataSource(uri)
    }

    fun prepare(): Boolean {
        if (!audioDecoder.prepare()) {
            Log.e(TAG, "prepare - prepareAudio() failed!")
            return false
        }
        if (audioDecoder.mDurationUs > mDurationUs) {
            mDurationUs = audioDecoder.mDurationUs
        }
        if (!videoDecoder.prepare()) {
            Log.e(TAG, "prepare - prepareVideo() failed!")
            return false
        }
        if (videoDecoder.mDurationUs > mDurationUs) {
            mDurationUs = videoDecoder.mDurationUs
        }
        synchronized(mState) { mState = STATE_PAUSED }
        return true
    }


    override fun getNowUs(): Long {
        return if (audioDecoder.mAudioTrackState == null) {
            System.currentTimeMillis() * 1000
        } else audioDecoder.mAudioTrackState.getAudioTimeUs()
    }

    override fun getRealTimeUsForMediaTime(mediaTimeUs: Long): Long {
        if (mDeltaTimeUs == INVALID_TIME) {
            mDeltaTimeUs = getNowUs() - mediaTimeUs
        }
        val earlyUs = mDeltaTimeUs + mediaTimeUs - getNowUs()
        val unadjustedFrameReleaseTimeNs = System.nanoTime() + (earlyUs * 1000)
        val adjustedReleaseTimeNs: Long = mFrameReleaseTimeHelper.adjustReleaseTime(
            mDeltaTimeUs + mediaTimeUs, unadjustedFrameReleaseTimeNs
        )
        return adjustedReleaseTimeNs / 1000

    }


    override fun getVsyncDurationNs(): Long {
        return mFrameReleaseTimeHelper.vsyncDurationNs
    }

    init {
        mThread = Thread(Runnable {
            while (true) {
                synchronized(mThreadStarted) {
                    if (!mThreadStarted) {
                        break
                    }
                }
                synchronized(mState) {
                    if (mState == STATE_PLAYING) {
                        doSomeWork()
                        audioDecoder.mAudioTrackState?.process()
                    }
                }
                try {
                    Thread.sleep(5) //5ms loop
                } catch (ex: InterruptedException) {
                    Log.d(TAG, "Thread interrupted")
                }
            }
        })
    }
}