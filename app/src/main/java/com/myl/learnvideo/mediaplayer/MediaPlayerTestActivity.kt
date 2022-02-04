package com.myl.learnvideo.mediaplayer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.SurfaceHolder
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.myl.learnvideo.databinding.ActivityMediaplayerBinding

class MediaPlayerTestActivity : AppCompatActivity(), SurfaceHolder.Callback {
    private lateinit var activityMediaPlayerBinding: ActivityMediaplayerBinding
    private val mediaPlayerViewModel: MediaPlayerViewModel by viewModels()

    companion object {
        fun startMediaPlayerActivity(context: Context) {
            context.startActivity(Intent(context, MediaPlayerTestActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityMediaPlayerBinding = ActivityMediaplayerBinding.inflate(layoutInflater)
        setContentView(activityMediaPlayerBinding.root)
        initView()
        mediaPlayerViewModel.initMediaPlayer(applicationContext, activityMediaPlayerBinding.sfvShow)
    }


    private fun initView() {
        val surfaceHolder = activityMediaPlayerBinding.sfvShow.holder
        surfaceHolder.addCallback(this)
        val display = windowManager.defaultDisplay
        surfaceHolder.setFixedSize(display.width, display.height)   //显示的分辨率,不设置为视频默认
        activityMediaPlayerBinding.btnStart.setOnClickListener {
            mediaPlayerViewModel.start()
        }
        activityMediaPlayerBinding.btnPause.setOnClickListener {
            mediaPlayerViewModel.pause()
        }
        activityMediaPlayerBinding.btnStop.setOnClickListener {
            mediaPlayerViewModel.stop()
        }
    }

    override fun surfaceCreated(p0: SurfaceHolder) {
        mediaPlayerViewModel.prepare()
    }

    override fun surfaceChanged(p0: SurfaceHolder, p1: Int, p2: Int, p3: Int) {
    }

    override fun surfaceDestroyed(p0: SurfaceHolder) {
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayerViewModel.release()
    }
}

