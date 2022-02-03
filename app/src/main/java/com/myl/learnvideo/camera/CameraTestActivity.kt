package com.myl.learnvideo.camera

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.myl.learnvideo.databinding.ActivityCameraTestBinding

class CameraTestActivity : AppCompatActivity() {

    private lateinit var activityCameraTestBinding: ActivityCameraTestBinding

    companion object {
        fun startCameraTestActivity(context: Context) {
            context.startActivity(Intent(context, CameraTestActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityCameraTestBinding = ActivityCameraTestBinding.inflate(layoutInflater)
        setContentView(activityCameraTestBinding.root)
        initView()
    }

    private fun initView() {
        activityCameraTestBinding.camera1Test.setOnClickListener {
            Camera1Activity.startCamera1Activity(this)
        }
        activityCameraTestBinding.camera2Test.setOnClickListener {
            Camera2Activity.startCamera2Activity(this)
        }
        activityCameraTestBinding.cameraXTest.setOnClickListener {
            CameraXActivity.startCameraXActivity(this)
        }
    }
}