package com.example.composebasics.cameraxguide

import android.annotation.SuppressLint
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.setViewTreeLifecycleOwner

@SuppressLint("RestrictedApi")
@Composable
fun CameraPreview(
    controller: LifecycleCameraController,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                this.controller = controller
                // Set the lifecycle owner for the preview view
                setViewTreeLifecycleOwner(lifecycleOwner)
                // Bind the controller to the lifecycle
                controller.bindToLifecycle(lifecycleOwner)
            }
        },
        update = { previewView ->
            // Update logic if needed
            previewView.controller = controller
        },
        modifier = modifier
    )
}