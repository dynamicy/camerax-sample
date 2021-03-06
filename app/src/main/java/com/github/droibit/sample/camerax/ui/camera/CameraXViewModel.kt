package com.github.droibit.sample.camerax.ui.camera

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.annotation.UiThread
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.github.droibit.sample.camerax.utils.Event
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import javax.inject.Named
import kotlin.LazyThreadSafetyMode.NONE
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

sealed class TakePictureResult {
    object Success : TakePictureResult()
    object Failure : TakePictureResult()
}

private const val FILENAME = "yyyy-MM-dd-HH-mm-ss-SSS"

class CameraXViewModel @ViewModelInject constructor(
    @ApplicationContext context: Context,
    @Named("pictureOutputDirectory") private val outputDirectory: File
) : AndroidViewModel(context as Application) {

    private val takePictureResultLiveData = MutableLiveData<Event<TakePictureResult>>()
    val takePictureResult: LiveData<Event<TakePictureResult>> get() = takePictureResultLiveData

    private val navigateToGalleryLiveData = MutableLiveData<Event<List<Uri>>>()
    val navigateToGallery: LiveData<Event<List<Uri>>> get() = navigateToGalleryLiveData

    private var imageCapture: ImageCapture? = null

    private var captureJob: Job? = null

    private val cameraProviderLiveData = MutableLiveData<ProcessCameraProvider>()
    val processCameraProvider: LiveData<ProcessCameraProvider> by lazy(NONE) {
        requestProcessCameraProvider()
        cameraProviderLiveData
    }

    @UiThread
    fun requestProcessCameraProvider() {
        val processCameraProvider = cameraProviderLiveData.value
        if (processCameraProvider != null) {
            cameraProviderLiveData.value = processCameraProvider
        }
        viewModelScope.launch {
            try {
                cameraProviderLiveData.value = ProcessCameraProvider(getApplication())
            } catch (e: Exception) {
                Timber.e(e)
            }
        }

    }

    @UiThread
    fun setImageCapture(imageCapture: ImageCapture) {
        this.imageCapture = imageCapture
    }

    @UiThread
    fun takePhoto() {
        if (captureJob?.isActive == true) {
            return
        }

        captureJob = viewModelScope.launch {
            imageCapture?.let { imageCapture ->
                // Create output file to hold the image
                val photoFile = File(
                    outputDirectory,
                    SimpleDateFormat(
                        FILENAME,
                        Locale.US
                    ).format(System.currentTimeMillis()) + ".jpg"
                )
                // Setup image capture metadata
                val metadata = ImageCapture.Metadata()

                // Create output options object which contains file + metadata
                val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile)
                    .setMetadata(metadata)
                    .build()

                val result = try {
                    val output = imageCapture.takePhoto(outputOptions)
                    val savedUri = output.savedUri ?: Uri.fromFile(photoFile)
                    Timber.d("Saved photo: $savedUri")
                    TakePictureResult.Success
                } catch (e: ImageCaptureException) {
                    Timber.e(e, "Photo capture failed: ${e.message}")
                    TakePictureResult.Failure
                }
                takePictureResultLiveData.value = Event(result)
            }
        }
    }

    private suspend fun ImageCapture.takePhoto(outputFileOptions: ImageCapture.OutputFileOptions): ImageCapture.OutputFileResults {
        return suspendCoroutine { cont ->
            takePicture(
                outputFileOptions,
                ContextCompat.getMainExecutor(getApplication()),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        cont.resume(outputFileResults)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        cont.resumeWithException(exception)
                    }
                }
            )
        }
    }

    @UiThread
    fun onGalleryButtonClick() {
        if (captureJob?.isActive == true) {
            return
        }

        val photos = outputDirectory.listFiles { file: File ->
            file.isFile && file.extension.equals("jpg", ignoreCase = false)
        }?.map { Uri.fromFile(it) } ?: emptyList()
        navigateToGalleryLiveData.value = Event(photos.reversed())
    }
}

@Suppress("FunctionName")
private suspend fun ProcessCameraProvider(context: Context): ProcessCameraProvider {
    return suspendCancellableCoroutine { cont ->
        Timber.d("ProcessCameraProvider: Start..")
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(Runnable {
            try {
                cont.resume(cameraProviderFuture.get())
            } catch (e: Exception) {
                when (e) {
                    is InterruptedException, is CancellationException -> cont.cancel(e)
                    is ExecutionException -> cont.resumeWithException(e.cause ?: e)
                    else -> cont.resumeWithException(e)
                }
            } finally {
                Timber.d("ProcessCameraProvider: End..")
            }
        }, ContextCompat.getMainExecutor(context))

        cont.invokeOnCancellation {
            // Don't cancel the Future.
//            cameraProviderFuture.cancel(false)
            Timber.d("ProcessCameraProvider: canceled($it)")
        }
    }
}