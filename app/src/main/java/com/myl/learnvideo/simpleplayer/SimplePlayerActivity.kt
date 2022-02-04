package com.myl.learnvideo.simpleplayer

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.SurfaceHolder
import androidx.appcompat.app.AppCompatActivity
import com.myl.learnvideo.R
import com.myl.learnvideo.databinding.ActivitySimpleplayerBinding

class SimplePlayerActivity : AppCompatActivity(), SurfaceHolder.Callback {
    private lateinit var activitySimpleplayerBinding: ActivitySimpleplayerBinding

    private lateinit var simplePlayer: SimplePlayer

    companion object {
        fun startSimplePlayerActivity(context: Context) {
            context.startActivity(Intent(context, SimplePlayerActivity::class.java))
        }
    }

    private val videoPath by lazy {
        resources.openRawResourceFd(R.raw.sample_video)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activitySimpleplayerBinding = ActivitySimpleplayerBinding.inflate(layoutInflater)
        setContentView(activitySimpleplayerBinding.root)
        initView()
        simplePlayer = SimplePlayer()
    }

    private fun initView() {
        val surfaceHolder = activitySimpleplayerBinding.sfvShow.holder
        surfaceHolder.addCallback(this)
        val display = windowManager.defaultDisplay
        surfaceHolder.setFixedSize(display.width, display.height)   //显示的分辨率,不设置为视频默认
        activitySimpleplayerBinding.btnStart.setOnClickListener {
            simplePlayer.setLoopMode(true)
            simplePlayer.play()
        }
        activitySimpleplayerBinding.btnPause.setOnClickListener {
            simplePlayer.pause()
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            simplePlayer.prepare(holder.surface, videoPath)
        }
    }

    override fun surfaceChanged(p0: SurfaceHolder, p1: Int, p2: Int, p3: Int) {
    }

    override fun surfaceDestroyed(p0: SurfaceHolder) {
    }

    override fun onDestroy() {
        super.onDestroy()
        simplePlayer.release()
    }
}

