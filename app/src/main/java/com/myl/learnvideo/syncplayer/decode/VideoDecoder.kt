package com.myl.learnvideo.syncplayer.decode

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import com.myl.learnvideo.Constants

/**
 *
 * @description 视频解码器
 * @author kingma-1993
 * @date 2022/2/11 3:45 下午
 */
class VideoDecoder(
    private val mediaTimeProvider: IMediaTimeProvider
) : IDecoder {

    private val mVideoExtractor: MediaExtractor = MediaExtractor()
    private var mVideoCodecStates: HashMap<Int, CodecState>? = null
    private var mVideoUri: Uri? = null
    private val decoderHelper: DecoderHelper = DecoderHelper()
    private var mMediaFormatHeight = 0
    private var mMediaFormatWidth = 0
    var mDurationUs: Long = 0

    companion object {
        private const val TAG = "VideoDecoder"
    }

    override fun prepare(): Boolean {
        mVideoExtractor.setDataSource(mVideoUri.toString(), null)
        if (null == mVideoCodecStates) {
            mVideoCodecStates = HashMap()
        } else {
            mVideoCodecStates?.clear()
        }
        var videoTrackCount = mVideoExtractor.trackCount
        while (videoTrackCount-- > 0) {
            val format = mVideoExtractor.getTrackFormat(videoTrackCount)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith(Constants.MIME_TYPE_VIDEO) == false) {
                continue
            }
            mMediaFormatHeight = decoderHelper.getMediaFormatInteger(format, MediaFormat.KEY_HEIGHT)
            mMediaFormatWidth = decoderHelper.getMediaFormatInteger(format, MediaFormat.KEY_WIDTH)
            Log.d(
                TAG, "video track #$videoTrackCount $format $mime Width:$mMediaFormatWidth, " +
                        "Height:$mMediaFormatHeight"
            )

            mVideoExtractor.selectTrack(videoTrackCount)
            if (!addTrack(videoTrackCount, format)) {
                Log.e(TAG, "prepareVideo - addTrack() failed!")
                return false
            }
            if (format.containsKey(MediaFormat.KEY_DURATION)) {
                val durationUs = format.getLong(MediaFormat.KEY_DURATION)
                if (durationUs > mDurationUs) {
                    mDurationUs = durationUs
                }
                Log.d(TAG, "audio track format #$videoTrackCount Duration:$mDurationUs microseconds")
            }
        }
        return true
    }

    override fun setDataSource(uri: Uri) {
        mVideoUri = uri
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
            mediaTimeProvider, mVideoExtractor,
            trackIndex, format, mediaCodec, true
        )
        mVideoCodecStates?.put(Integer.valueOf(trackIndex), codecState)
        return true
    }
}