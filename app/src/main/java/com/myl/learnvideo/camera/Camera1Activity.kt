package com.myl.learnvideo.camera

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.SurfaceHolder
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.myl.learnvideo.databinding.ActivityCamera1Binding

class Camera1Activity : AppCompatActivity() {
    private lateinit var activityCamera1Binding: ActivityCamera1Binding
    private val camera1ViewModel: Camera1ViewModel by viewModels()

    companion object {
        fun startCamera1Activity(context: Context) {
            context.startActivity(Intent(context, Camera1Activity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityCamera1Binding = ActivityCamera1Binding.inflate(layoutInflater)
        setContentView(activityCamera1Binding.root)
        initView()
        camera1ViewModel.initCamera(
            applicationContext,
            windowManager,
            activityCamera1Binding.surface
        )
        camera1ViewModel.openCamera()
    }

    override fun onResume() {
        super.onResume()
        if (activityCamera1Binding.surface.width != 0) {
            camera1ViewModel.openCamera()
            camera1ViewModel.startPreview(activityCamera1Binding.surface.width, activityCamera1Binding.surface.height)
        } else {
            activityCamera1Binding.surface.holder.addCallback(PreviewCallback())
        }
    }

    override fun onPause() {
        super.onPause()
        camera1ViewModel.closeCamera()
    }

    private fun initView() {
        activityCamera1Binding.switchCamera.setOnClickListener {
            camera1ViewModel.switchCamera()
        }
        activityCamera1Binding.cameraCaptureButton.setOnClickListener {
            camera1ViewModel.takePhoto()
        }
    }

    inner class PreviewCallback : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {}
        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            camera1ViewModel.startPreview(width, height)
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {}
    }

}

