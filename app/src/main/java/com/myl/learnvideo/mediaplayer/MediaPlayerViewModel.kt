package com.myl.learnvideo.mediaplayer

import android.content.Context
import android.media.MediaPlayer
import android.view.SurfaceView
import androidx.lifecycle.ViewModel
import com.myl.learnvideo.R


class MediaPlayerViewModel : ViewModel() {
    private lateinit var mPlayer: MediaPlayer
    private lateinit var context: Context
    private var surfaceView: SurfaceView? = null

    fun initMediaPlayer(context: Context, surfaceView: SurfaceView) {
        this.context = context
        this.surfaceView = surfaceView
    }

    fun prepare() {
        mPlayer = MediaPlayer.create(context, R.raw.sample_video)
//        mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC)
        surfaceView?.holder?.apply {
            mPlayer.setDisplay(this)
        }
    }

    fun start() {
        mPlayer.start()
    }

    fun stop() {
        mPlayer.stop()
    }

    fun pause() {
        mPlayer.pause()
    }

    fun release() {
        if (mPlayer.isPlaying) {
            mPlayer.stop()
        }
        mPlayer.release()
        surfaceView = null
    }

}