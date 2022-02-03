package com.myl.learnvideo.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCaptureSession.CaptureCallback
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import android.media.ImageReader
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.Surface
import android.view.TextureView
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myl.learnvideo.camera.Constants.getCachePath
import com.myl.learnvideo.utils.BitmapUtils
import com.myl.learnvideo.utils.CloseUtils
import com.myl.learnvideo.utils.PhotoUtils.saveBitmap2Gallery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.*

class Camera2ViewModel : ViewModel() {

    private lateinit var cameraManager: CameraManager
    private var backCameraId: String = ""
    private var cameraId: String = ""
    private var frontCameraId: String = ""
    private var frontCameraCharacteristics: CameraCharacteristics? = null
    private var backCameraCharacteristics: CameraCharacteristics? = null
    private var imageReader: ImageReader? = null
    private var sensorOrientation: Int = 0
    private var cameraDevice: CameraDevice? = null
    private var cameraCaptureSession: CameraCaptureSession? = null
    private lateinit var textureView: TextureView
    private val rotations = SparseIntArray()

    companion object {
        private const val TAG = "Camera2ViewModel"
    }

    private lateinit var context: Context

    private fun initOrientations() {
        rotations.append(Surface.ROTATION_0, 90)
        rotations.append(Surface.ROTATION_90, 0)
        rotations.append(Surface.ROTATION_180, 270)
        rotations.append(Surface.ROTATION_270, 180)
    }

    /**
     * 初始化相机，和配置相关属性
     */
    fun initCamera(context: Context, textureView: TextureView) {
        initOrientations()
        this.context = context
        this.textureView = textureView
        try {
            //获取相机服务 CameraManager
            cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

            //遍历设备支持的相机 ID ，比如前置，后置等
            val cameraIdList = cameraManager.cameraIdList
            for (cameraId in cameraIdList) {
                // 拿到装在所有相机信息的  CameraCharacteristics 类
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                //拿到相机的方向，前置，后置，外置
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing != null) {
                    //后置摄像头
                    if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                        backCameraId = cameraId
                        backCameraCharacteristics = characteristics
                    } else if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                        //前置摄像头
                        frontCameraId = cameraId
                        frontCameraCharacteristics = characteristics
                    }
                    this.cameraId = cameraId
                }

                //是否支持 Camera2 的高级特性
                val level = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
                /**
                 * 不支持 Camera2 的特性
                 */
                if (level == null || level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
                    Log.w(TAG, "您的手机不支持Camera2的高级特效")

                }
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    /**
     * 打开摄像头
     * @param width
     * @param height
     */
    @SuppressLint("MissingPermission")
    fun openCamera(width: Int, height: Int) {
        //判断不同摄像头，拿到 CameraCharacteristics
        val characteristics =
            if (cameraId == backCameraId) backCameraCharacteristics!! else frontCameraCharacteristics!!
        //拿到配置的map
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

        //获取摄像头传感器的方向
        sensorOrientation = characteristics?.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        //获取预览尺寸
        val previewSizes = map!!.getOutputSizes(
            SurfaceTexture::class.java
        )
        //获取最佳尺寸
        val bestSize: Size? = getBestSize(width, height, previewSizes)
        /**
         * 配置预览属性
         * 与 Cmaera1 不同的是，Camera 是把尺寸信息给到 Surface (SurfaceView 或者 ImageReader)，
         * Camera 会根据 Surface 配置的大小，输出对应尺寸的画面;
         * 注意摄像头的 width > height ，而我们使用竖屏，所以宽高要变化一下
         */
        bestSize?.apply {
            textureView.surfaceTexture?.setDefaultBufferSize(bestSize.height, bestSize.width)
        }
        /**
         * 设置图片尺寸，这里图片的话，选择最大的分辨率即可
         */
        val sizes = map.getOutputSizes(ImageFormat.JPEG)
        val largest = Collections.max(
            listOf(*sizes),
            CompareSizesByArea()
        )
        //设置imagereader，配置大小，且最大Image为 1，因为是 JPEG
        imageReader = ImageReader.newInstance(
            largest.width, largest.height,
            ImageFormat.JPEG, 1
        )

        //拍照监听
        imageReader?.setOnImageAvailableListener(
            ImageAvailable(),
            null
        )
        try {
            //打开摄像头，监听数据
            cameraManager.openCamera(
                cameraId,
                CameraDeviceCallback(),
                null
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    /**
     * 创建 Session
     */
    private fun createPreviewPipeline(cameraDevice: CameraDevice) {
        try {
            //创建作为预览的 CaptureRequest.builder
            val captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            val surface = Surface(textureView.surfaceTexture)
            //添加 surface 容器
            captureBuilder.addTarget(surface)
            // 创建CameraCaptureSession，该对象负责管理处理预览请求和拍照请求,这个必须在创建 Seesion 之前就准备好，传递给底层用于皮遏制 pipeline
            cameraDevice.createCaptureSession(
                listOf(surface, imageReader?.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        cameraCaptureSession = session
                        try {
                            //设置自动聚焦
                            captureBuilder.set(
                                CaptureRequest.CONTROL_AE_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                            )
                            //设置自动曝光
                            captureBuilder.set(
                                CaptureRequest.CONTROL_AE_MODE,
                                CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
                            )

                            //创建 CaptureRequest
                            val build = captureBuilder.build()
                            //设置预览时连续捕获图片数据
                            session.setRepeatingRequest(build, null, null)
                        } catch (e: Exception) {
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Toast.makeText(context, "配置失败", Toast.LENGTH_SHORT).show()
                    }
                },
                null
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    inner class CameraDeviceCallback : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            //此时摄像头已经打开，可以预览了
            createPreviewPipeline(camera)
        }

        override fun onDisconnected(camera: CameraDevice) {
            camera.close()
        }

        override fun onError(camera: CameraDevice, error: Int) {
            camera.close()
        }
    }

    /**
     * 关闭摄像头
     */
    fun closeCamera() {
        try {
            //停止预览
            cameraCaptureSession?.stopRepeating()
            cameraCaptureSession = null
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
        //关闭设备
        cameraDevice?.close()
        cameraDevice = null
        imageReader?.close()
        imageReader = null
    }

    fun switchCamera() {
        cameraId = if (cameraId == backCameraId) frontCameraId else backCameraId
        closeCamera()
        openCamera(textureView.width, textureView.height)
    }

    fun takePhoto(rotation: Int) {
        try {
            //创建一个拍照的 session
            val captureRequest =
                cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            //设置装在图像数据的 Surface
            captureRequest.addTarget(imageReader!!.surface)
            //聚焦
            captureRequest.set(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            )
            //自动曝光
            captureRequest.set(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
            )
            // 根据设备方向 计算设置照片的方向
            captureRequest.set(
                CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation)
            )
            // 先停止预览
            cameraCaptureSession!!.stopRepeating()
            cameraCaptureSession!!.capture(captureRequest.build(), object : CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    super.onCaptureCompleted(session, request, result)
                    try {
                        //拍完之后，让它继续可以预览
                        val captureRequest1 =
                            cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                        captureRequest1.addTarget(Surface(textureView.surfaceTexture))
                        cameraCaptureSession!!.setRepeatingRequest(
                            captureRequest1.build(),
                            null,
                            null
                        )
                    } catch (e: CameraAccessException) {
                        e.printStackTrace()
                    }
                }
            }, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
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
    private fun getBestSize(shortSize: Int, longSize: Int, sizes: Array<Size>): Size? {
        var bestSize: Size? = null
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

    /**
     * Retrieves the JPEG orientation from the specified screen rotation.
     *
     * @param rotation The screen rotation.
     * @return The JPEG orientation (one of 0, 90, 270, and 360)
     */
    private fun getOrientation(rotation: Int): Int {
        // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
        // We have to take that into account and rotate JPEG properly.
        // For devices with orientation of 90, we simply return our mapping from ORIENTATIONS.
        // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
        return (rotations.get(rotation) + sensorOrientation + 270) % 360
    }

    /**
     * 拍照监听,当有图片数据时，回调该接口
     */
    inner class ImageAvailable : ImageReader.OnImageAvailableListener {
        override fun onImageAvailable(reader: ImageReader) {
            viewModelScope.launch(Dispatchers.IO) {
                savePic(reader)
            }
        }

    }

    private fun savePic(imageReader: ImageReader) {
        val dir = File(getCachePath(context))
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val name = "test_camera2.jpg"
        val file = File(dir, name)

        var fos: FileOutputStream? = null
        var image: Image? = null
        var bitmap: Bitmap? = null
        try {
            fos = FileOutputStream(file)
            //获取捕获的照片数据
            image = imageReader.acquireLatestImage()
            //拿到所有的 Plane 数组
            val planes = image.planes
            //由于是 JPEG ，只需要获取下标为 0 的数据即可
            val buffer = planes[0].buffer
            val data = ByteArray(buffer.remaining())
            //把 bytebuffer 的数据给 byte数组
            buffer.get(data)
            bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
            //旋转图片
            if (cameraId == frontCameraId) {
                bitmap = BitmapUtils.rotate(bitmap, 270f)
                bitmap = BitmapUtils.mirror(bitmap)
            } else {
                bitmap = BitmapUtils.rotate(bitmap, 90f)
            }
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
            fos.flush()
        } catch (e: java.lang.Exception) {
            Log.d(TAG, "$e")
        } finally {
            CloseUtils.close(fos)
            //记得关闭 image
            image?.close()
        }

        if (bitmap != null) {
            saveBitmap2Gallery(context, bitmap)
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

