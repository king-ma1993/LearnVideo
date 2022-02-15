package com.myl.learnvideo.syncplayer

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.os.Build
import androidx.annotation.RequiresApi
import java.lang.reflect.Method
import java.nio.ByteBuffer
import java.util.*

/**
 * Class for playing audio by using audio track.
 * audioTrack.write methods will
 * block until all data has been written to system. In order to avoid blocking, this class
 * caculates available buffer size first then writes to audio sink.
 */
class NonBlockingAudioTrack(sampleRate: Int, channelCount: Int) {
    internal inner class QueueElement {
        var data: ByteBuffer? = null
        var size = 0
        var ptsNs: Long = 0
    }

    private var mAudioTrack: AudioTrack?
    private val mSampleRate: Int
    var numBytesQueued = 0
        private set
    private val mQueue = LinkedList<QueueElement>()
    private var mStopped = false
    private var getLatencyMethod: Method? = null
    private var mLatencyUs: Long
    private var mLastTimestampSampleTimeUs: Long
    private var mAudioTimestampSet = false
    private val mAudioTimestamp: AudioTimestamp

    // Calculate the speed-adjusted position using the timestamp (which may be in the future).
    fun getAudioTimeUs(): Long {
        val systemClockUs = System.nanoTime() / 1000
        var numFramesPlayed = mAudioTrack?.playbackHeadPosition ?: 0
        if (systemClockUs - mLastTimestampSampleTimeUs >= MIN_TIMESTAMP_SAMPLE_INTERVAL_US) {
            mAudioTimestampSet = mAudioTrack?.getTimestamp(mAudioTimestamp) == true
            getLatencyMethod?.apply {
                try {
                    mLatencyUs = getLatencyMethod?.invoke(mAudioTrack, null) as Int * 1000L / 2
                    mLatencyUs = mLatencyUs.coerceAtLeast(0)
                } catch (e: Exception) {
                    getLatencyMethod = null
                }
            }
            mLastTimestampSampleTimeUs = systemClockUs
        }
        return if (mAudioTimestampSet) {
            // Calculate the speed-adjusted position using the timestamp (which may be in the future).
            val elapsedSinceTimestampUs = System.nanoTime() / 1000 - mAudioTimestamp.nanoTime / 1000
            val elapsedSinceTimestampFrames = elapsedSinceTimestampUs * mSampleRate / 1000000L
            val elapsedFrames = mAudioTimestamp.framePosition + elapsedSinceTimestampFrames
            elapsedFrames * 1000000L / mSampleRate
        } else {
            numFramesPlayed * 1000000L / mSampleRate - mLatencyUs
        }
    }

    fun play() {
        mStopped = false
        mAudioTrack?.play()
    }

    fun stop() {
        if (mQueue.isEmpty()) {
            mAudioTrack?.stop()
            numBytesQueued = 0
        } else {
            mStopped = true
        }
    }

    fun pause() {
        mAudioTrack?.pause()
    }

    fun flush() {
        if (mAudioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
            return
        }
        mAudioTrack?.flush()
        mQueue.clear()
        numBytesQueued = 0
        mStopped = false
    }

    fun release() {
        mQueue.clear()
        numBytesQueued = 0
        mLatencyUs = 0
        mLastTimestampSampleTimeUs = 0
        mAudioTrack?.release()
        mAudioTrack = null
        mStopped = false
        mAudioTimestampSet = false
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun process() {
        while (!mQueue.isEmpty()) {
            val element = mQueue.peekFirst()
            val written = mAudioTrack?.write(
                element.data!!, element.size,
                AudioTrack.WRITE_NON_BLOCKING, element.ptsNs
            ) ?: 0
            if (written < 0) {
                throw RuntimeException("Audiotrack.write() failed.")
            }
            numBytesQueued -= written
            element.size -= written
            if (element.size != 0) {
                break
            }
            mQueue.removeFirst()
        }
        if (mStopped) {
            mAudioTrack?.stop()
            numBytesQueued = 0
            mStopped = false
        }
    }

    val playState: Int
        get() = mAudioTrack?.playState ?: 0

    fun write(data: ByteBuffer?, size: Int, ptsNs: Long) {
        val element = QueueElement()
        element.data = data
        element.size = size
        element.ptsNs = ptsNs

        // accumulate size written to queue
        numBytesQueued += size
        mQueue.add(element)
    }

    companion object {
        private val TAG = NonBlockingAudioTrack::class.java.simpleName
        private const val MIN_TIMESTAMP_SAMPLE_INTERVAL_US = 250000
    }

    init {
        val channelConfig: Int = when (channelCount) {
            1 -> AudioFormat.CHANNEL_OUT_MONO
            2 -> AudioFormat.CHANNEL_OUT_STEREO
            6 -> AudioFormat.CHANNEL_OUT_5POINT1
            else -> throw IllegalArgumentException()
        }
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            channelConfig,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = 2 * minBufferSize
        mAudioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC,
            sampleRate,
            channelConfig,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
            AudioTrack.MODE_STREAM
        )
        mSampleRate = sampleRate
        try {
            getLatencyMethod = AudioTrack::class.java.getMethod("getLatency", null)
        } catch (e: NoSuchMethodException) {
        }
        mLatencyUs = 0
        mLastTimestampSampleTimeUs = 0
        mAudioTimestamp = AudioTimestamp()
    }
}



