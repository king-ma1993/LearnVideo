package com.myl.learnvideo.syncplayer.decode

import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaCodec.CryptoInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import com.myl.learnvideo.Constants.INVALID_TIME
import com.myl.learnvideo.Constants.MIME_TYPE_AUDIO
import com.myl.learnvideo.Constants.MIME_TYPE_VIDEO
import com.myl.learnvideo.syncplayer.NonBlockingAudioTrack
import com.myl.learnvideo.utils.TimeUtils
import java.nio.ByteBuffer
import java.util.*

class CodecState(
    private val mMediaTimeProvider: IMediaTimeProvider,
    private val mExtractor: MediaExtractor,
    private var mTrackIndex: Int = 0,
    private val mFormat: MediaFormat,
    private val mCodec: MediaCodec,
    private val mLimitQueueDepth: Boolean
) {

    //    private var mCodecInputBuffers: Array<ByteBuffer> = arrayOf()
//    private var mCodecOutputBuffers: Array<ByteBuffer> = arrayOf()
    private var mAvailableInputBufferIndices: LinkedList<Int> = LinkedList<Int>()
    private var mAvailableOutputBufferIndices: LinkedList<Int> = LinkedList<Int>()
    private var mAvailableOutputBufferInfos: LinkedList<MediaCodec.BufferInfo> = LinkedList<MediaCodec.BufferInfo>()
    private var mPresentationTimeUs: Long = 0
    private var mSampleBaseTimeUs: Long = 0
    private var mIsAudio = mFormat.getString(MediaFormat.KEY_MIME)?.startsWith(MIME_TYPE_AUDIO)
    private var mAudioTrack: NonBlockingAudioTrack? = null
    private var mSawInputEOS = false
    private var mSawOutputEOS = false
    private var mOutputFormat: MediaFormat? = null

    companion object {
        private const val TAG = "CodecState"
        private const val TWO_MB = 2 * 1024 * 1024
    }

    fun release() {
        mCodec.stop()
//        mCodecInputBuffers = arrayOf()
//        mCodecOutputBuffers = arrayOf()

        mAvailableInputBufferIndices.clear()
        mAvailableOutputBufferIndices.clear()
        mAvailableOutputBufferInfos.clear()

        mCodec.release()
        mAudioTrack?.release()
        mAudioTrack = null
    }

    fun start() {
        mCodec.start()
//        mCodecInputBuffers = mCodec.inputBuffers
//        mCodecOutputBuffers = mCodec.outputBuffers
        mAudioTrack?.play()
    }

    fun pause() {
        mAudioTrack?.pause()
    }

    fun getCurrentPositionUs(): Long {
        return mPresentationTimeUs
    }

    fun flush() {
        mAvailableInputBufferIndices.clear()
        mAvailableOutputBufferIndices.clear()
        mAvailableOutputBufferInfos.clear()
        mSawInputEOS = false
        mSawOutputEOS = false
        if (mAudioTrack?.playState != AudioTrack.PLAYSTATE_PLAYING) {
            mAudioTrack?.flush()
        }
        mCodec.flush()
    }

    fun isEnded(): Boolean {
        return mSawInputEOS && mSawOutputEOS
    }

    /**
     * doSomeWork() is the worker function that does all buffer handling and decoding works.
     * It first reads data from {@link MediaExtractor} and pushes it into {@link MediaCodec};
     * it then dequeues buffer from {@link MediaCodec}, consumes it and pushes back to its own
     * buffer queue for next round reading data from {@link MediaExtractor}.
     */
    fun doSomeWork() {
        val indexInput = mCodec.dequeueInputBuffer(0)

        if (indexInput != MediaCodec.INFO_TRY_AGAIN_LATER) {
            mAvailableInputBufferIndices.add(indexInput)
        }

        while (feedInputBuffer()) {
        }

        val info = MediaCodec.BufferInfo()
        val indexOutput = mCodec.dequeueOutputBuffer(info, 0 /* timeoutUs */)

        if (indexOutput == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            mOutputFormat = mCodec.outputFormat
            onOutputFormatChanged()
        } else if (indexOutput == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
//            mCodecOutputBuffers = mCodec.outputBuffers
        } else if (indexOutput != MediaCodec.INFO_TRY_AGAIN_LATER) {
            mAvailableOutputBufferIndices.add(indexOutput)
            mAvailableOutputBufferInfos.add(info)
        }

        while (drainOutputBuffer()) {
        }
    }

    /** Returns true if more output data could be drained.  */
    //audio and video belongs to different codecstate, one has audio, the other one dont
    //so there exists two mPresentationTimeUs, one for audio, the other one for video
    //however, audio and video draining works in the same thread
    private fun drainOutputBuffer(): Boolean {
        if (mSawOutputEOS || mAvailableOutputBufferIndices.isEmpty()) {
            return false
        }
        val index = mAvailableOutputBufferIndices.peekFirst().toInt()
        val info = mAvailableOutputBufferInfos.peekFirst()
        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
            Log.d(TAG, "saw output EOS on track $mTrackIndex")
            mSawOutputEOS = true
            return false
        }
        return if (mAudioTrack != null) {
            val buffer =/* mCodecOutputBuffers[index]*/ mCodec.getOutputBuffer(index)
            buffer?.apply {
                buffer.clear()
                val audioBuffer = ByteBuffer.allocate(buffer.remaining())
                audioBuffer.put(buffer)
                audioBuffer.clear()
                mAudioTrack?.write(audioBuffer, info.size, TimeUtils.usToNs(info.presentationTimeUs))
            }
            mCodec.releaseOutputBuffer(index, false)
            mPresentationTimeUs = info.presentationTimeUs
            mAvailableOutputBufferIndices.removeFirst()
            mAvailableOutputBufferInfos.removeFirst()
            true
        } else {
            // video
            val twiceVsyncDurationUs = 2 * TimeUtils.nsToUs(mMediaTimeProvider.getVsyncDurationNs())
            val realTimeUs = mMediaTimeProvider.getRealTimeUsForMediaTime(info.presentationTimeUs) //映射到nowUs时间轴上
            val nowUs = mMediaTimeProvider.getNowUs() //audio play time
            //String streamType = mAudioTrack == null ? "video:":"audio:";
            //Log.d("avsync", streamType + " presentationUs is " + info.presentationTimeUs + ",realTimeUs is " + realTimeUs + ",nowUs is " + nowUs);
            val lateUs = TimeUtils.nsToUs(System.nanoTime()) - realTimeUs
            if (lateUs < -twiceVsyncDurationUs) {
                // too early;
                return false
            } else if (lateUs > 30000) {
                Log.d(TAG, "video late by $lateUs us.")
            } else {
                mPresentationTimeUs = info.presentationTimeUs
            }

            //mCodec.releaseOutputBuffer(index, render);
            mCodec.releaseOutputBuffer(index, TimeUtils.usToNs(realTimeUs))
            mAvailableOutputBufferIndices.removeFirst()
            mAvailableOutputBufferInfos.removeFirst()
            true
        }
    }

    private fun onOutputFormatChanged() {
        val mime = mOutputFormat?.getString(MediaFormat.KEY_MIME)
        Log.d(TAG, "CodecState::onOutputFormatChanged $mime")
        mIsAudio = false
        if (mime?.startsWith(MIME_TYPE_AUDIO) == true) {
            mIsAudio = true
            val sampleRate = mOutputFormat!!.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = mOutputFormat!!.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            Log.d(
                TAG, "CodecState::onOutputFormatChanged Audio" +
                        " sampleRate:" + sampleRate + " channels:" + channelCount
            )
            // We do sanity check here after we receive data from MediaExtractor and before
            // we pass them down to AudioTrack. If MediaExtractor works properly, this
            // sanity-check is not necessary, however, in our tests, we found that there
            // are a few cases where ch=0 and samplerate=0 were returned by MediaExtractor.
            if (channelCount < 1 || channelCount > 8 || sampleRate < 8000 || sampleRate > 128000) {
                return
            }
            mAudioTrack = NonBlockingAudioTrack(sampleRate, channelCount)
            mAudioTrack?.play()
        }
        if (mime?.startsWith(MIME_TYPE_VIDEO) == true) {
            val width = mOutputFormat!!.getInteger(MediaFormat.KEY_WIDTH)
            val height = mOutputFormat!!.getInteger(MediaFormat.KEY_HEIGHT)
            Log.d(
                TAG, "CodecState::onOutputFormatChanged Video" +
                        " width:" + width + " height:" + height
            )
        }
    }


    /** Returns true if more input data could be fed.  */
    private fun feedInputBuffer(): Boolean {
        if (mSawInputEOS || mAvailableInputBufferIndices.isEmpty()) {
            return false
        }

        // stalls read if audio queue is larger than 2MB full so we will not occupy too much heap
        if (mLimitQueueDepth && mAudioTrack?.numBytesQueued ?: 0 > TWO_MB) {
            return false
        }
        val index = mAvailableInputBufferIndices.peekFirst().toInt()
        val codecData = mCodec.getInputBuffer(index)/*mCodecInputBuffers[index]*/
        val trackIndex = mExtractor.sampleTrackIndex
        if (trackIndex == mTrackIndex) {
            val sampleSize = codecData?.let { mExtractor.readSampleData(it, 0) } ?: 0
            var sampleTime = mExtractor.sampleTime
            val sampleFlags = mExtractor.sampleFlags
            if (sampleSize <= 0) {
                Log.d(
                    TAG, "sampleSize: " + sampleSize + " trackIndex:" + trackIndex +
                            " sampleTime:" + sampleTime + " sampleFlags:" + sampleFlags
                )
                mSawInputEOS = true
                return false
            }
            if (mIsAudio == false) {
                if (mSampleBaseTimeUs == INVALID_TIME) {
                    mSampleBaseTimeUs = sampleTime
                }
                sampleTime -= mSampleBaseTimeUs
                // this is just used for getCurrentPosition, not used for avsync
                mPresentationTimeUs = sampleTime
            }
            if (sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED != 0) {
                val info = CryptoInfo()
                mExtractor.getSampleCryptoInfo(info)
                mCodec.queueSecureInputBuffer(
                    index, 0 /* offset */, info, sampleTime, 0 /* flags */
                )
            } else {
                mCodec.queueInputBuffer(
                    index, 0 /* offset */, sampleSize, sampleTime, 0 /* flags */
                )
            }
            mAvailableInputBufferIndices.removeFirst()
            mExtractor.advance()
            return true
        } else if (trackIndex < 0) {
            Log.d(TAG, "saw input EOS on track $mTrackIndex")
            mSawInputEOS = true
            mCodec.queueInputBuffer(
                index, 0 /* offset */, 0 /* sampleSize */,
                0 /* sampleTime */, MediaCodec.BUFFER_FLAG_END_OF_STREAM
            )
            mAvailableInputBufferIndices.removeFirst()
        }
        return false
    }

    fun getAudioTimeUs(): Long {
        return mAudioTrack?.getAudioTimeUs() ?: 0
    }

    fun process() {
        mAudioTrack?.process()
    }
}