package com.myl.learnvideo.syncplayer.decode

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import android.view.SurfaceHolder

class AudioDecoder(private val holder: SurfaceHolder, private val context: Context) : IDecoder {

    private var mAudioExtractor: MediaExtractor? = null
    private var mAudioHeaders: Map<String, String>? = null
    private var mAudioCodecStates: Map<Int, CodecState>? = null
    private var mAudioUri: Uri? = null

    companion object {
        private const val TAG = "AudioDecoder"
    }

    override fun prepare() {
        var i = mAudioExtractor!!.trackCount
        while (i-- > 0) {
            val format = mAudioExtractor!!.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (!mime!!.startsWith("audio/")) {
                continue
            }
            Log.d(TAG,
                "audio track #" + i + " " + format + " " + mime +
                        " Is ADTS:" + getMediaFormatInteger(format, MediaFormat.KEY_IS_ADTS) +
                        " Sample rate:" + getMediaFormatInteger(
                    format,
                    MediaFormat.KEY_SAMPLE_RATE
                ) + " Channel count:" +
                        getMediaFormatInteger(format, MediaFormat.KEY_CHANNEL_COUNT)
            )
            mAudioExtractor!!.selectTrack(i)
            if (!addTrack(i, format)) {
                Log.e(
                    TAG,
                    "prepareAudio - addTrack() failed!"
                )
                return false
            }
            if (format.containsKey(MediaFormat.KEY_DURATION)) {
                val durationUs = format.getLong(MediaFormat.KEY_DURATION)
                if (durationUs > mDurationUs) {
                    mDurationUs = durationUs
                }
                Log.d(
                    com.example.zhanghui.avplayer.MediaCodecPlayer.TAG,
                    "audio track format #" + i +
                            " Duration:" + mDurationUs + " microseconds"
                )
            }
        }
        return true
    }

    override fun setDataSource() {

    }
}