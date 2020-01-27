package com.elkin.sample.ui.fragment

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Point
import android.hardware.camera2.*
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.Image
import android.media.ImageReader
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.*
import androidx.core.view.isVisible
import androidx.exifinterface.media.ExifInterface
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.elkin.sample.R
import com.elkin.sample.ui.MainViewModel
import com.elkin.sample.ui.State
import kotlinx.android.synthetic.main.fragment_camera.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

/**
 * @author elkin
 */
class CameraFragment : Fragment() {

    private val viewModel by lazy {
        ViewModelProvider(requireActivity()).get(MainViewModel::class.java)
    }

    private val cameraManager: CameraManager by lazy {
        val context = requireContext().applicationContext
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    private lateinit var cameraId: String

    private val characteristics: CameraCharacteristics by lazy {
        cameraManager.getCameraCharacteristics(cameraId)
    }

    private lateinit var imageReader: ImageReader
    private val cameraThread = HandlerThread("CameraThread").apply { start() }
    private val cameraHandler = Handler(cameraThread.looper)
    private val imageReaderThread = HandlerThread("imageReaderThread").apply { start() }
    private val imageReaderHandler = Handler(imageReaderThread.looper)

    private lateinit var camera: CameraDevice
    private lateinit var session: CameraCaptureSession

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_camera, container, false)

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.state.value = State.CAMERA_PREPARING

        viewModel.state.observe(viewLifecycleOwner, Observer { state ->
            when (state!!) {
                State.CAMERA_PREPARING -> setProgressState(true)
                State.CAMERA_READY -> {
                    cameraFragment_captureButton.setOnClickListener {
                        it.isEnabled = false
                        viewModel.state.postValue(State.CAPTURE_STARTED)
                        lifecycleScope.launch(Dispatchers.IO) {
                            takePhoto()
                        }
                    }
                    setProgressState(false)
                }
                State.CAPTURE_STARTED -> setProgressState(true)
                State.CAPTURE_FINISHED -> {
                    setProgressState(false)
                    cameraFragment_captureButton.isEnabled = true
                }
                State.ERROR -> {
                    requireActivity().supportFragmentManager.beginTransaction()
                        .replace(android.R.id.content, ErrorFragment())
                        .commit()
                }
            }
        })

        val cameraId = cameraManager.cameraIdList.lastOrNull {
            val characteristics = cameraManager.getCameraCharacteristics(it)
            if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) { // TODO
                false

            } else {
                val capabilities = characteristics.get(
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES
                )
                capabilities?.contains(
                    CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE
                ) ?: false
            }
        }

        if (cameraId == null) {
            viewModel.setErrorState(getString(R.string.cameraError_noSuitableCameraFound), false)
            return

        } else {
            this.cameraId = cameraId
        }

        cameraFragment_textureView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceDestroyed(holder: SurfaceHolder) = Unit

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) = Unit

            override fun surfaceCreated(holder: SurfaceHolder) {
                val previewSize =
                    getPreviewOutputSize(cameraFragment_textureView.display, characteristics)
                        ?: return
                Log.d(
                    LOG_TAG,
                    "View finder size: ${cameraFragment_textureView.width} x ${cameraFragment_textureView.height}"
                )
                Log.d(LOG_TAG, "Selected preview size: $previewSize")
                cameraFragment_textureView.holder.setFixedSize(previewSize.width, previewSize.height)
                cameraFragment_textureView.setAspectRatio(previewSize.width, previewSize.height)

                // To ensure that size is set, initialize camera in the view's thread
                view.post { openCamera() }
            }
        })
    }

    override fun onStop() {
        super.onStop()
        try {
            camera.close()
        } catch (exc: Throwable) {
            Log.e(LOG_TAG, "Error closing camera", exc)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraThread.quitSafely()
        imageReaderThread.quitSafely()
    }

    private fun setProgressState(inProgress: Boolean) {
        cameraFragment_captureButton.isVisible = !inProgress
        cameraFragment_progressBar.isVisible = inProgress
    }

    private fun getPreviewOutputSize(
        display: Display,
        characteristics: CameraCharacteristics
    ): Size? {
        val targetClass = SurfaceHolder::class.java
        if (!StreamConfigurationMap.isOutputSupportedFor(targetClass)) {
            viewModel.setErrorState(getString(R.string.cameraError_previewNotSupported), false)
            return null
        }

        // Find which is smaller: screen or 1080p
        val outPoint = Point()
        display.getRealSize(outPoint)
        val screenSize = Size(outPoint.x, outPoint.y)
        // Compares inverted because we support work only in portrait mode
        val hdScreen =
            screenSize.height >= SIZE_1080P.width || screenSize.width >= SIZE_1080P.height
        val maxSize = if (hdScreen) SIZE_1080P else screenSize

        val config = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
        val allSizes = config.getOutputSizes(targetClass)

        allSizes.sortByDescending { it.height * it.width }

        return allSizes.first { it.height <= maxSize.height && it.width <= maxSize.width }
    }

    @SuppressLint("MissingPermission")
    private fun openCamera() {
        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) {
                camera = device
                val size = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
                )!!.getOutputSizes(ImageFormat.JPEG).maxBy { it.height * it.width }!!
                imageReader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 1)
                val targets = listOf(cameraFragment_textureView.holder.surface, imageReader.surface)
                createCaptureSession(targets)
            }

            override fun onDisconnected(device: CameraDevice) {
                viewModel.setErrorState(getString(R.string.cameraError_cameraDisconnected), true)
            }

            override fun onError(device: CameraDevice, error: Int) {
                val message = when (error) {
                    ERROR_CAMERA_DEVICE -> "Fatal (device)"
                    ERROR_CAMERA_DISABLED -> "Device policy"
                    ERROR_CAMERA_IN_USE -> "Camera in use"
                    ERROR_CAMERA_SERVICE -> "Fatal (service)"
                    ERROR_MAX_CAMERAS_IN_USE -> "Maximum cameras in use"
                    else -> "Unknown"
                }
                Log.e(LOG_TAG, message)
                viewModel.setErrorState(getString(R.string.cameraError_cameraOpen, message), true)
            }
        }, cameraHandler)
    }

    private fun createCaptureSession(targets: List<Surface>) {
        camera.createCaptureSession(targets, object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                this@CameraFragment.session = session
                val captureRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    .apply { addTarget(cameraFragment_textureView.holder.surface) }
                session.setRepeatingRequest(captureRequest.build(), null, cameraHandler)
                viewModel.state.postValue(State.CAMERA_READY)
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                Log.e(LOG_TAG, "Camera session configuration failed")
                viewModel.setErrorState(getString(R.string.cameraError_cameraSession), true)
            }
        }, cameraHandler)
    }

    private fun takePhoto() {
        // Flush any images left in the image reader
        @Suppress("ControlFlowWithEmptyBody")
        while (imageReader.acquireNextImage() != null) {
        }

        imageReader.setOnImageAvailableListener({ reader ->
            imageReader.setOnImageAvailableListener(null, null)
            val image = reader.acquireNextImage()
            Log.d(LOG_TAG, "Image available: ${image.timestamp}")

            val output = saveResult(image) ?: return@setOnImageAvailableListener
            Log.d(LOG_TAG, "Image saved: ${output.absolutePath}")

            val exif = ExifInterface(output.absolutePath)
            exif.setAttribute(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL.toString()
            )
            exif.saveAttributes()
            Log.d(LOG_TAG, "EXIF metadata saved: ${output.absolutePath}")

            image.close()
            viewModel.state.postValue(State.CAPTURE_FINISHED)
        }, imageReaderHandler)

        val captureRequest =
            session.device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                .apply { addTarget(imageReader.surface) }
        session.capture(captureRequest.build(), object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult
            ) {
                super.onCaptureCompleted(session, request, result)
                val resultTimestamp = result.get(CaptureResult.SENSOR_TIMESTAMP)
                Log.d(LOG_TAG, "Capture result received: $resultTimestamp")
            }
        }, cameraHandler)
    }

    private fun saveResult(result: Image): File? {
        val buffer = result.planes[0].buffer
        val bytes = ByteArray(buffer.remaining()).apply { buffer.get(this) }
        return try {
            val output = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                "IMG_${sdf(Locale.getDefault()).format(Date())}.jpg"
            )
            FileOutputStream(output).use { it.write(bytes) }
            output
        } catch (exc: IOException) {
            Log.e(LOG_TAG, "Unable to write JPEG image to file", exc)
            viewModel.setErrorState(getString(R.string.cameraError_saveImage), true)
            null
        }
    }

    companion object {
        private val LOG_TAG = CameraFragment::class.java.simpleName

        val SIZE_1080P = Size(1920, 1080)

        private fun sdf(locale: Locale) = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", locale)
    }
}