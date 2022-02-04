package com.myl.learnvideo.simpleplayer

import android.content.res.AssetFileDescriptor
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import android.util.Log
import android.view.Surface
import androidx.annotation.RequiresApi
import java.io.IOException

/**
 * 简单视频播放器
 */
class SimplePlayer {
    companion object {
        private const val TAG = "SimplePlayer"
        private const val VIDEO = "video/"
        private const val DECODE_TIME_OUT = 10 * 1000L

    }

    private lateinit var extractor: MediaExtractor
    private var mediaCodec: MediaCodec? = null

    // Declare this here to reduce allocations.
    private val mBufferInfo = MediaCodec.BufferInfo()

    @Volatile
    private var mIsStopRequested = false
    private var mLoop = false

    private var playTask: PlayTask? = null

    /**
     * Sets the loop mode.  If true, playback will loop forever.
     */
    fun setLoopMode(loopMode: Boolean) {
        mLoop = loopMode
    }


    @RequiresApi(Build.VERSION_CODES.N)
    fun prepare(surface: Surface, file: AssetFileDescriptor) {
        extractor = MediaExtractor()
        extractor.setDataSource(file)
        val trackIndex: Int = selectTrack(extractor)
        if (trackIndex < 0) {
            throw RuntimeException("No video track found in $file")
        }
        extractor.selectTrack(trackIndex)
        val format = extractor.getTrackFormat(trackIndex)

        // Create a MediaCodec decoder, and configure it with the MediaFormat from the
        // extractor.  It's very important to use the format from the extractor because
        // it contains a copy of the CSD-0/CSD-1 codec-specific data chunks.
        val mime = format.getString(MediaFormat.KEY_MIME)
        mediaCodec = mime?.let { MediaCodec.createDecoderByType(it) }
        mediaCodec?.configure(format, surface, null, 0)
        mediaCodec?.start()
    }

    fun release() {
        mediaCodec?.stop()
        mediaCodec?.release()
        extractor.release()
    }

    private fun requestStop() {
        mIsStopRequested = true
    }
    private fun doExtract() {
        if (extractor == null || mediaCodec == null) {
            throw RuntimeException("please init player first!!")
        }
        var inputChunk = 0
        var firstInputTimeNsec: Long = -1
        var outputDone = false
        var inputDone = false
        while (!outputDone) {
            if (mIsStopRequested) {
                Log.d(TAG, "Stop requested")
                return
            }
            if (!inputDone) {
                mediaCodec?.dequeueInputBuffer(DECODE_TIME_OUT)?.takeIf { it >= 0 }?.let { index ->
                    val inputBuffer = mediaCodec?.getInputBuffer(index)
                    inputBuffer?.let {
//                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        // Read the sample data into the ByteBuffer.  This neither respects nor
                        // updates inputBuf's position, limit, etc.
                        val chunkSize = extractor.readSampleData(inputBuffer, 0)
                        if (chunkSize < 0) {
                            // End of stream -- send empty frame with EOS flag set.
                            mediaCodec?.queueInputBuffer(
                                index, 0, 0, 0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                            Log.d(TAG, "sent input EOS")
                        } else {
                            val presentationTimeUs = extractor.sampleTime
                            mediaCodec?.queueInputBuffer(
                                index, 0, chunkSize,
                                presentationTimeUs, 0 /*flags*/
                            )
                            Log.d(TAG, "submitted frame $inputChunk to dec, size=$chunkSize")
                            inputChunk++
                            extractor.advance()
                        }
                    }
                }
            } else {
                Log.d(TAG, "input buffer not available")
            }
            if (!outputDone) {
                val decoderStatus =
                    mediaCodec?.dequeueOutputBuffer(mBufferInfo, DECODE_TIME_OUT) ?: 0
                if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // no output available yet
                    Log.d(TAG, "no output from decoder available")
                } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    // not important for us, since we're using Surface
                    Log.d(TAG, "decoder output buffers changed")
                } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val newFormat = mediaCodec?.outputFormat
                    Log.d(TAG, "decoder output format changed: $newFormat")
                } else if (decoderStatus < 0) {
                    throw java.lang.RuntimeException(
                        "unexpected result from decoder.dequeueOutputBuffer: " +
                                decoderStatus
                    )
                } else {
                    // decoderStatus >= 0
                    if (firstInputTimeNsec != 0L) {
                        // Log the delay from the first buffer of input to the first buffer
                        // of output.
                        val nowTimeUs = System.nanoTime()
                        Log.d(
                            TAG,
                            "startup lag " + (nowTimeUs - firstInputTimeNsec) / 1000000.0 + " ms"
                        )
                        firstInputTimeNsec = 0
                    }
                    var doLoop = false
                    Log.d(
                        TAG, "surface decoder given buffer " + decoderStatus +
                                " (size=" + mBufferInfo.size + ")"
                    )
                    if ((mBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        Log.d(TAG, "output EOS")
                        if (mLoop) {
                            doLoop = true
                        } else {
                            outputDone = true
                        }
                    }

                    val doRender = (mBufferInfo.size != 0)

                    // As soon as we call releaseOutputBuffer, the buffer will be forwarded
                    // to SurfaceTexture to convert to a texture.  We can't control when it
                    // appears on-screen, but we can manage the pace at which we release
                    // the buffers.

                    // As soon as we call releaseOutputBuffer, the buffer will be forwarded
                    // to SurfaceTexture to convert to a texture.  We can't control when it
                    // appears on-screen, but we can manage the pace at which we release
                    // the buffers.

                    // As soon as we call releaseOutputBuffer, the buffer will be forwarded
                    // to SurfaceTexture to convert to a texture.  We can't control when it
                    // appears on-screen, but we can manage the pace at which we release
                    // the buffers.
                    mediaCodec?.releaseOutputBuffer(decoderStatus, doRender)
                    if (doLoop) {
                        Log.d(TAG, "Reached EOS, looping")
                        extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                        inputDone = false
                        mediaCodec?.flush() // reset decoder state
                    }
                }
            }
        }
    }

    fun pause() {
        playTask?.requestStop()
    }

    fun play() {
        playTask = PlayTask(this)
        playTask?.setLoopMode(mLoop)
        playTask?.execute()
    }

    /**
     * Selects the video track, if any.
     *
     * @return the track index, or -1 if no video track is found.
     */
    private fun selectTrack(extractor: MediaExtractor): Int {
        // Select the first video track we find, ignore the rest.
        val numTracks = extractor.trackCount
        for (i in 0 until numTracks) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith(VIDEO) == true) {
                Log.d(TAG, "Extractor selected track $i ($mime): $format")
                return i
            }
        }
        return -1
    }


    class PlayTask(
        private val mPlayer: SimplePlayer
    ) : Runnable {
        private var mDoLoop = false
        private var mThread: Thread? = null
        private val mStopLock = Object()
        private var mStopped = false


        /**
         * Sets the loop mode.  If true, playback will loop forever.
         */
        fun setLoopMode(loopMode: Boolean) {
            mDoLoop = loopMode
        }

        /**
         * Creates a new thread, and starts execution of the player.
         */
        fun execute() {
            mPlayer.setLoopMode(mDoLoop)
            mThread = Thread(this, "Simple Player")
            mThread?.start()
        }

        /**
         * Requests that the player stop.
         *
         *
         * Called from arbitrary thread.
         */
        fun requestStop() {
            mPlayer.requestStop()
        }

        /**
         * Wait for the player to stop.
         *
         *
         * Called from any thread other than the PlayTask thread.
         */
        fun waitForStop() {
            synchronized(mStopLock) {
                while (!mStopped) {
                    try {
                        mStopLock.wait()
                    } catch (ie: InterruptedException) {
                        // discard
                    }
                }
            }
        }

        override fun run() {
            try {
                mPlayer.doExtract()
            } catch (ioe: IOException) {
                throw java.lang.RuntimeException(ioe)
            } finally {
                // tell anybody waiting on us that we're done
                synchronized(mStopLock) {
                    mStopped = true
                    mStopLock.notifyAll()
                }
            }
        }
    }
}