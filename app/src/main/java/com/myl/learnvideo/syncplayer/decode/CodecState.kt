package com.myl.learnvideo.syncplayer.decode

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.myl.learnvideo.Constants.MIME_TYPE_AUDIO
import java.nio.ByteBuffer
import java.util.*

class CodecState(
    private val mMediaTimeProvider: IMediaTimeProvider,
    private val mExtractor: MediaExtractor,
    private val trackIndex: Int,
    private val mFormat: MediaFormat,
    private val mCodec: MediaCodec,
    private val mLimitQueueDepth: Boolean
) {

    private var mCodecInputBuffers: Array<ByteBuffer> = arrayOf()
    private var mCodecOutputBuffers: Array<ByteBuffer> = arrayOf()
    private var mAvailableInputBufferIndices: LinkedList<Int> = LinkedList<Int>()
    private var mAvailableOutputBufferIndices: LinkedList<Int> = LinkedList<Int>()
    private var mAvailableOutputBufferInfos: LinkedList<MediaCodec.BufferInfo> = LinkedList<MediaCodec.BufferInfo>()
    private var mPresentationTimeUs: Long = 0
    private var mSampleBaseTimeUs: Long = 0
    private val isAudio = mFormat.getString(MediaFormat.KEY_MIME)?.startsWith(MIME_TYPE_AUDIO)
    private var mAudioTrack: NonBlockingAudioTrack? = null

    fun release() {
        mCodec.stop()
        mCodecInputBuffers = arrayOf()
        mCodecOutputBuffers = arrayOf()

        mAvailableInputBufferIndices.clear()
        mAvailableOutputBufferIndices.clear()
        mAvailableOutputBufferInfos.clear()

        mCodec.release()
        mAudioTrack?.release()
        mAudioTrack = null
    }
}