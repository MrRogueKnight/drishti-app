package io.github.mrroguekknight.drishti.ui

import android.content.Context
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.common.util.concurrent.ListenableFuture

// CameraX preview composable placeholder. To use, request camera permission and then show this composable.
// This uses AndroidView to host a PreviewView from CameraX.
@Composable
fun CameraPreview(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>? = null
    AndroidView(factory = { ctx ->
        val previewView = androidx.camera.view.PreviewView(ctx)
        previewView.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
        cameraProviderFuture?.addListener({
            try {
                val cameraProvider = cameraProviderFuture?.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(ctx as androidx.lifecycle.LifecycleOwner, cameraSelector, preview)
            } catch (e: Exception) {
                Log.e("CameraPreview", "Camera init failed: ${e.message}")
            }
        }, ctx.mainExecutor)
        previewView
    }, modifier = modifier.fillMaxSize())
}
