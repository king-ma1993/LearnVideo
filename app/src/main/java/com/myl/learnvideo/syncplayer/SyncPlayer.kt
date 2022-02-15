package com.myl.learnvideo.syncplayer

import android.content.Context
import android.util.Log
import android.view.SurfaceHolder
import com.myl.learnvideo.Constants.INVALID_TIME
import com.myl.learnvideo.syncplayer.decode.AudioDecoder
import com.myl.learnvideo.syncplayer.decode.IMediaTimeProvider
import com.myl.learnvideo.syncplayer.decode.VideoDecoder
import com.myl.learnvideo.syncplayer.decode.VideoFrameReleaseTimeHelper
import com.myl.learnvideo.utils.TimeUtils

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
    private val videoDecoder = VideoDecoder(this, mSurfaceHolder)
    private var mState: Int = STATE_IDLE
    var mDurationUs: Long = 0
    private var mDeltaTimeUs: Long = 0

    companion object {
        private const val TAG = "SyncPlayer"
        private const val STATE_IDLE = 1
        private const val STATE_PREPARING = 2
        private const val STATE_PLAYING = 3
        private const val STATE_PAUSED = 4
    }

    fun setDataSource(path: String) {
        audioDecoder.setDataSource(path)
        videoDecoder.setDataSource(path)
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
            TimeUtils.msToUs(System.currentTimeMillis())
        } else {
            audioDecoder.mAudioTrackState?.getAudioTimeUs() ?: 0
        }
    }

    override fun getRealTimeUsForMediaTime(mediaTimeUs: Long): Long {
        if (mDeltaTimeUs == INVALID_TIME) {
            mDeltaTimeUs = getNowUs() - mediaTimeUs
        }
        val earlyUs = mDeltaTimeUs + mediaTimeUs - getNowUs()
        val unadjustedFrameReleaseTimeNs = System.nanoTime() + TimeUtils.usToNs(earlyUs)
        val adjustedReleaseTimeNs: Long = mFrameReleaseTimeHelper.adjustReleaseTime(
            mDeltaTimeUs + mediaTimeUs, unadjustedFrameReleaseTimeNs
        )
        return TimeUtils.nsToUs(adjustedReleaseTimeNs)

    }

    private fun doSomeWork() {
        videoDecoder.doSomeWork()
        audioDecoder.doSomeWork()
    }

    fun play() {
        mFrameReleaseTimeHelper.enable()
        start()
        synchronized(mThreadStarted) {
            mThreadStarted = true
            mThread?.start()
        }
    }

    override fun getVsyncDurationNs(): Long {
        return mFrameReleaseTimeHelper.vsyncDurationNs
    }

    init {
        mThread = Thread(Runnable {
            while (true) {
                if (!mThreadStarted) {
                    break
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

    private fun start(): Boolean {
        Log.d(TAG, "start")
        synchronized(mState) {
            if (mState == STATE_PLAYING || mState == STATE_PREPARING) {
                return true
            } else if (mState == STATE_IDLE) {
                mState = STATE_PREPARING
                return true
            } else if (mState != STATE_PAUSED) {
                throw IllegalStateException()
            }
            videoDecoder.start()
            audioDecoder.start()
            mDeltaTimeUs = INVALID_TIME
            mState = STATE_PLAYING
        }
        return false
    }

    fun pause() {
        Log.d(TAG, "pause")
        synchronized(mState) {
            if (mState == STATE_PAUSED) {
                return
            } else if (mState != STATE_PLAYING) {
                throw IllegalStateException()
            }
            videoDecoder.pause()
            audioDecoder.pause()
            mState = STATE_PAUSED
        }
    }

    fun flush() {
        Log.d(TAG, "flush")
        synchronized(mState) {
            if (mState == STATE_PLAYING || mState == STATE_PREPARING) {
                return
            }
            videoDecoder.flush()
            audioDecoder.flush()
        }
    }

    fun release() {
        synchronized(mState) {
            if (mState == STATE_PLAYING) {
                pause()
            }
            videoDecoder.release()
            audioDecoder.release()
            mFrameReleaseTimeHelper.disable()
            mDurationUs = INVALID_TIME
            mState = STATE_IDLE
        }
        synchronized(mThreadStarted) { mThreadStarted = false }
        try {
            mThread?.join()
        } catch (ex: InterruptedException) {
            Log.d(TAG, "mThread.join $ex")
        }
    }

    fun isEnded(): Boolean {
        return videoDecoder.isEnded() && audioDecoder.isEnded()
    }

    fun getCurrentPosition(): Int {
        return videoDecoder.getPositionUs()
    }
}