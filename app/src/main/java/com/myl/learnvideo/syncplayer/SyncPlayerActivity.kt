package com.myl.learnvideo.syncplayer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.Window
import androidx.appcompat.app.AppCompatActivity
import com.myl.learnvideo.Constants.SEPARATE
import com.myl.learnvideo.databinding.ActivitySyncPlayerBinding
import com.myl.learnvideo.utils.ResCopyHelper
import com.myl.learnvideo.utils.ResCopyHelper.TEST_RES

class SyncPlayerActivity : AppCompatActivity(), SurfaceHolder.Callback {
    private lateinit var activitySyncPlayerBinding: ActivitySyncPlayerBinding
    private var syncPlayer: SyncPlayer? = null

    companion object {
        fun startSyncPlayerActivity(context: Context) {
            context.startActivity(Intent(context, SyncPlayerActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        activitySyncPlayerBinding = ActivitySyncPlayerBinding.inflate(layoutInflater)
        setContentView(activitySyncPlayerBinding.root)
        initView()
        initData()
    }

    private fun initData() {
        ResCopyHelper.copyAssets(this)
    }

    private fun initView() {
        val surfaceHolder = activitySyncPlayerBinding.surfaceView.holder
        surfaceHolder.addCallback(this)
        activitySyncPlayerBinding.btnStart.setOnClickListener {
            syncPlayer?.play()
        }
        activitySyncPlayerBinding.btnPause.setOnClickListener {
            syncPlayer?.pause()
        }
    }

    override fun surfaceCreated(p0: SurfaceHolder) {
        initSyncPlayer()
    }

    private fun initSyncPlayer() {
        syncPlayer = SyncPlayer(activitySyncPlayerBinding.surfaceView.holder, applicationContext)
        syncPlayer?.apply {
            setDataSource(getTestVideoPath())
            prepare()
        }
    }

    override fun onPause() {
        super.onPause()
        syncPlayer?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        syncPlayer?.release()
    }

    override fun surfaceChanged(p0: SurfaceHolder, p1: Int, p2: Int, p3: Int) {
    }

    override fun surfaceDestroyed(p0: SurfaceHolder) {
        syncPlayer?.release()
    }

    private fun getTestVideoPath(): String {
        return ResCopyHelper.getTestDirPath(this) + SEPARATE + TEST_RES + SEPARATE + "test_video.mp4"
    }
}

