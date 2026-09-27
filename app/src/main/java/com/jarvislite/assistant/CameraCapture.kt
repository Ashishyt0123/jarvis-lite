package com.jarvislite.assistant

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Takes a photo without showing any camera preview on screen - the model
 * asked for a picture (e.g. "what am I looking at?"), not the user opening
 * a camera app. Must be called from something with a Lifecycle, which is
 * why JarvisForegroundService extends LifecycleService.
 */
class CameraCapture(private val context: Context, private val lifecycleOwner: LifecycleOwner) {

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    /** [useFrontCamera] true = selfie camera, false = rear/main camera. Calls back with the saved file, or null on failure. */
    fun takePhoto(useFrontCamera: Boolean, onResult: (File?) -> Unit) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                val imageCapture = ImageCapture.Builder().build()
                val selector = if (useFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA
                else CameraSelector.DEFAULT_BACK_CAMERA

                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, selector, imageCapture)

                val file = File(context.cacheDir, "jarvis_photo_${System.currentTimeMillis()}.jpg")
                val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

                imageCapture.takePicture(
                    outputOptions,
                    executor,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            provider.unbindAll()
                            onResult(file)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            provider.unbindAll()
                            onResult(null)
                        }
                    }
                )
            } catch (e: Exception) {
                onResult(null)
            }
        }, ContextCompat.getMainExecutor(context))
    }
}
