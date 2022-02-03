package com.myl.learnvideo.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.hardware.Camera
import android.view.Surface
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myl.learnvideo.utils.BitmapUtils
import com.myl.learnvideo.utils.CloseUtils
import com.myl.learnvideo.utils.PhotoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

class Camera1ViewModel : ViewModel() {

    private var mFrontCameraId = 0
    private var mBackCameraId = 0
    private var mFrontCameraInfo: Camera.CameraInfo? = null
    private var mBackCameraInfo: Camera.CameraInfo? = null
    private var mCamera: Camera? = null
    private var mSurfaceView: SurfaceView? = null
    private var mCameraID = 0
    private var windowManager: WindowManager? = null
    private lateinit var context: Context

    fun switchCamera() {
        //关闭摄像头
        closeCamera()
        mCameraID = if (mCameraID == mFrontCameraId) mBackCameraId else mFrontCameraId
        //打开相机
        openCamera(mCameraID)
        //开启预览
        startPreview(mSurfaceView!!.width, mSurfaceView!!.height)
    }

    /**
     * 关闭摄像头
     */
    fun closeCamera() {
        //停止预览
        mCamera?.stopPreview()
        mCamera?.release()
        mCamera = null
    }

    /**
     * 初始化相机
     */
    fun initCamera(context: Context, windowManager: WindowManager, surfaceView: SurfaceView) {
        this.context = context
        this.windowManager = windowManager
        this.mSurfaceView = surfaceView
        //获取相机个数
        val numberOfCameras = Camera.getNumberOfCameras()
        for (i in 0 until numberOfCameras) {
            val info = Camera.CameraInfo()
            //获取相机信息
            Camera.getCameraInfo(i, info)
            //前置摄像头
            if (Camera.CameraInfo.CAMERA_FACING_FRONT == info.facing) {
                mFrontCameraId = i
                mFrontCameraInfo = info
            } else if (Camera.CameraInfo.CAMERA_FACING_BACK == info.facing) {
                mBackCameraId = i
                mBackCameraInfo = info
            }
        }
    }

    //打开摄像头
    fun openCamera(cameraId: Int = mBackCameraId) {
        //根据 cameraId 打开不同摄像头,注意，Camera1只有打开摄像头之后，才能拿到那些配置数据
        mCamera = Camera.open(cameraId)
        mCameraID = cameraId
        val info = if (cameraId == mFrontCameraId) mFrontCameraInfo!! else mBackCameraInfo!!
        adjustCameraOrientation(info)
    }

    /**
     * 矫正相机预览画面
     *
     * @param info
     */
    private fun adjustCameraOrientation(info: Camera.CameraInfo) {
        //判断当前的横竖屏
        val rotation: Int = windowManager?.defaultDisplay?.rotation ?: Surface.ROTATION_0
        var degress = 0
        when (rotation) {
            Surface.ROTATION_0 -> degress = 0
            Surface.ROTATION_90 -> degress = 90
            Surface.ROTATION_180 -> degress = 180
            Surface.ROTATION_270 -> degress = 270
        }
        var result = 0
        //后置摄像头
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
            result = (info.orientation - degress + 360) % 360
        } else if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            //先镜像
            result = (info.orientation + degress) % 360
            result = (360 - result) % 360
        }
        mCamera?.setDisplayOrientation(result)
    }

    /**
     * 开始显示
     */
    fun startPreview(width: Int, height: Int) {
        initPreviewParams(width, height)
        //设置预览 SurfaceHolder
        val camera = mCamera
        try {
            camera?.setPreviewDisplay(mSurfaceView!!.holder)
        } catch (e: IOException) {
            e.printStackTrace()
        }
        //开始显示
        camera?.startPreview()
    }

    /**
     * 设置预览参数，需要制定尺寸才行
     * 在相机中，width > height 的，而我们的UI是3:4，所以这里也要做换算
     *
     * @param shortSize
     * @param longSize
     */
    private fun initPreviewParams(shortSize: Int, longSize: Int) {
        val camera = mCamera
        if (camera != null) {
            val parameters = camera.parameters
            //获取手机支持的尺寸
            val sizes = parameters.supportedPreviewSizes
            val bestSize: Camera.Size? = getBestSize(shortSize, longSize, sizes)
            bestSize?.apply {
                //设置预览大小
                parameters.setPreviewSize(bestSize.width, bestSize.height)
                //设置图片大小，拍照
                parameters.setPictureSize(bestSize.width, bestSize.height)
            }
            //设置格式
            parameters.previewFormat = ImageFormat.NV21

            //设置自动聚焦
            val modes = parameters.supportedFocusModes
            //查看支持的聚焦模式
            for (mode in modes) {
                //默认图片聚焦模式
                if (mode.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                    parameters.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
                    break
                }
            }
            camera.parameters = parameters
        }
    }

    /**
     * 获取预览最后尺寸
     *
     * @param shortSize
     * @param longSize
     * @param sizes
     * @return
     */
    private fun getBestSize(shortSize: Int, longSize: Int, sizes: List<Camera.Size>): Camera.Size? {
        var bestSize: Camera.Size? = null
        val uiRatio = longSize.toFloat() / shortSize
        var minRatio = uiRatio
        for (previewSize in sizes) {
            val cameraRatio = previewSize.width.toFloat() / previewSize.height

            //如果找不到比例相同的，找一个最近的,防止预览变形
            val offset = Math.abs(cameraRatio - minRatio)
            if (offset < minRatio) {
                minRatio = offset
                bestSize = previewSize
            }
            //比例相同
            if (uiRatio == cameraRatio) {
                bestSize = previewSize
                break
            }
        }
        return bestSize
    }

    fun takePhoto() {
        mCamera?.takePicture({ }, null,
            { data, camera ->
                viewModelScope.launch(Dispatchers.IO) {
                    savePic(data)
                }
            })
    }

    private fun savePic(data: ByteArray) {
        val dir = File(Constants.getCachePath(context))
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val name = "test_camera1.jpg"
        val file = File(dir, name)
        var bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
        var fos: FileOutputStream? = null
        try {
            fos = FileOutputStream(file)
            //保存之前先调整方向
            val info = if (mCameraID == mFrontCameraId) mFrontCameraInfo!! else mBackCameraInfo!!
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                bitmap = BitmapUtils.rotate(bitmap, 90f)
            } else {
                bitmap = BitmapUtils.rotate(bitmap, 270f)
                bitmap = BitmapUtils.mirror(bitmap)
            }
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } finally {
            CloseUtils.close(fos)
        }
        if (bitmap != null) {
            PhotoUtils.saveBitmap2Gallery(context, bitmap)
            viewModelScope.launch(Dispatchers.Main) {
                Toast.makeText(context, "保存成功 插入相册", Toast.LENGTH_SHORT).show()
            }
        } else {
            viewModelScope.launch(Dispatchers.Main) {
                Toast.makeText(context, "拍照失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

}

