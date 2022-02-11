package com.myl.learnvideo.syncplayer.decode

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import com.myl.learnvideo.Constants.MIME_TYPE_AUDIO

class AudioDecoder(
    private val mediaTimeProvider: IMediaTimeProvider
) : IDecoder {

    private val mAudioExtractor: MediaExtractor = MediaExtractor()
    private var mAudioCodecStates: HashMap<Int, CodecState>? = null
    private var mAudioUri: Uri? = null
    var mDurationUs: Long = 0
    private val decoderHelper: DecoderHelper = DecoderHelper()
    var mAudioTrackState: CodecState? = null

    companion object {
        private const val TAG = "AudioDecoder"
    }

    override fun prepare(): Boolean {
        mAudioExtractor.setDataSource(mAudioUri.toString(), null)
        if (null == mAudioCodecStates) {
            mAudioCodecStates = HashMap()
        } else {
            mAudioCodecStates?.clear()
        }
        var audioTrackCount = mAudioExtractor.trackCount
        while (audioTrackCount-- > 0) {
            val format = mAudioExtractor.getTrackFormat(audioTrackCount)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith(MIME_TYPE_AUDIO) == false) {
                continue
            }
            Log.d(
                TAG,
                "audio track #" + audioTrackCount + " " + format + " " + mime +
                        " Is ADTS:" + decoderHelper.getMediaFormatInteger(format, MediaFormat.KEY_IS_ADTS) +
                        " Sample rate:" + decoderHelper.getMediaFormatInteger(
                    format, MediaFormat.KEY_SAMPLE_RATE
                ) + " Channel count:" + decoderHelper.getMediaFormatInteger(format, MediaFormat.KEY_CHANNEL_COUNT)
            )
            mAudioExtractor.selectTrack(audioTrackCount)
            if (!addTrack(audioTrackCount, format)) {
                Log.e(TAG, "prepareAudio - addTrack() failed!")
                return false
            }
            if (format.containsKey(MediaFormat.KEY_DURATION)) {
                val durationUs = format.getLong(MediaFormat.KEY_DURATION)
                if (durationUs > mDurationUs) {
                    mDurationUs = durationUs
                }
                Log.d(TAG, "audio track format #$audioTrackCount Duration:$mDurationUs microseconds")
            }
        }
        return true
    }

    override fun setDataSource(uri: Uri) {
        mAudioUri = uri
    }


    override fun addTrack(trackIndex: Int, format: MediaFormat): Boolean {
        val mime = format.getString(MediaFormat.KEY_MIME)
        val mediaCodec = mime?.let { MediaCodec.createDecoderByType(it) }
        if (mediaCodec == null) {
            Log.e(
                TAG, "addTrack - Could not create regular playback codec for mime " +
                        mime + "!"
            )
            return false
        }
        mediaCodec.configure(format, null, null, 0)
        val codecState = CodecState(
            mediaTimeProvider, mAudioExtractor,
            trackIndex, format, mediaCodec, true
        )
        mAudioCodecStates?.put(Integer.valueOf(trackIndex), codecState)
        mAudioTrackState = codecState
        return true
    }
}