package com.myl.learnvideo.camera

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCapture.OutputFileOptions
import androidx.camera.core.ImageCapture.OutputFileResults
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.myl.learnvideo.camera.Constants.getCachePath
import com.myl.learnvideo.databinding.ActivityCameraxBinding
import com.myl.learnvideo.utils.PhotoUtils
import java.io.File

class CameraXActivity : AppCompatActivity() {

    private lateinit var activityCameraxBinding: ActivityCameraxBinding

    companion object {
        fun startCameraXActivity(context: Context) {
            context.startActivity(Intent(context, CameraXActivity::class.java))
        }
    }

    private var mImageCapture: ImageCapture? = null
    private var mFacing = CameraSelector.LENS_FACING_BACK

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityCameraxBinding = ActivityCameraxBinding.inflate(layoutInflater)
        setContentView(activityCameraxBinding.root)
        initView()
        startPreview()
    }

    private fun initView() {
        activityCameraxBinding.switchCamera.setOnClickListener {
            switchCamera()
        }
        activityCameraxBinding.cameraCaptureButton.setOnClickListener {
            takePhoto()
        }
    }

    /**
     * 开启摄像头
     */
    private fun startPreview() {
        //返回当前可以绑定生命周期的 ProcessCameraProvider
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                //将相机的生命周期和activity的生命周期绑定，camerax 会自己释放，不用担心了
                val cameraProvider = cameraProviderFuture.get()
                //预览的 capture，它里面支持角度换算
                val preview = Preview.Builder().build()

                //创建图片的 capture
                mImageCapture = ImageCapture.Builder()
                    .setFlashMode(ImageCapture.FLASH_MODE_AUTO)
                    .build()


                //选择后置摄像头
                val cameraSelector = CameraSelector.Builder().requireLensFacing(mFacing).build()

                //预览之前先解绑
                cameraProvider.unbindAll()

                //将数据绑定到相机的生命周期中
                val camera = cameraProvider.bindToLifecycle(
                    this@CameraXActivity,
                    cameraSelector,
                    preview,
                    mImageCapture
                )
                //将previewview 的 surface 给相机预览
                preview.setSurfaceProvider(
                    activityCameraxBinding.viewFinder.createSurfaceProvider(
                        camera.cameraInfo
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        if (mImageCapture != null) {
            val dir = File(getCachePath(this))
            if (!dir.exists()) {
                dir.mkdirs()
            }
            //创建文件
            val file = File(getCachePath(this), "test_cameraX.jpg")
            if (file.exists()) {
                file.delete()
            }
            //创建包文件的数据，比如创建文件
            val outputFileOptions = OutputFileOptions.Builder(file).build()

            //开始拍照
            mImageCapture?.takePicture(
                outputFileOptions,
                ContextCompat.getMainExecutor(this),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: OutputFileResults) {
                        //    Uri savedUri = outputFileResults.getSavedUri();
                        Toast.makeText(this@CameraXActivity, "保存成功:${file.absolutePath}", Toast.LENGTH_SHORT).show()
                        PhotoUtils.saveFile2Gallery(this@CameraXActivity,file.path)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Toast.makeText(this@CameraXActivity, "保存失败:${exception.printStackTrace()}", Toast.LENGTH_SHORT).show()
                    }
                })
        }
    }

    private fun switchCamera() {
        /**
         * 白屏的问题是 PreviewView 移除所有View，且没数据到 Surface，
         * 所以只留背景色，可以对次做处理
         */
        mFacing =
            if (mFacing == CameraSelector.LENS_FACING_FRONT) CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT
        startPreview()
    }
}