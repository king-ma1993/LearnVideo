package com.myl.learnvideo.camera

import android.content.Context
import android.content.Intent
import android.graphics.SurfaceTexture
import android.os.Bundle
import android.view.TextureView.SurfaceTextureListener
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.myl.learnvideo.databinding.ActivityCamera2Binding

class Camera2Activity : AppCompatActivity() {
    private lateinit var activityCamera2Binding: ActivityCamera2Binding
    private val camera2ViewModel: Camera2ViewModel by viewModels()

    companion object {
        fun startCamera2Activity(context: Context) {
            context.startActivity(Intent(context, Camera2Activity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityCamera2Binding = ActivityCamera2Binding.inflate(layoutInflater)
        setContentView(activityCamera2Binding.root)
        initView()
        camera2ViewModel.initCamera(applicationContext, activityCamera2Binding.surface)
    }

    override fun onResume() {
        super.onResume()
        if (activityCamera2Binding.surface.width != 0) {
            camera2ViewModel.openCamera(activityCamera2Binding.surface.width, activityCamera2Binding.surface.height)
        } else {
            activityCamera2Binding.surface.surfaceTextureListener = PreviewCallback()
        }
    }

    override fun onPause() {
        camera2ViewModel.closeCamera()
        super.onPause()
    }

    private fun initView() {
        activityCamera2Binding.switchCamera.setOnClickListener {
            camera2ViewModel.switchCamera()
        }
        activityCamera2Binding.cameraCaptureButton.setOnClickListener {
            camera2ViewModel.takePhoto(windowManager.defaultDisplay.orientation)
        }
    }

    inner class PreviewCallback : SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            //当有大小时，打开摄像头
            camera2ViewModel.openCamera(width, height)
        }

        override fun onSurfaceTextureSizeChanged(
            surface: SurfaceTexture,
            width: Int,
            height: Int
        ) {
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            return false
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
    }

}