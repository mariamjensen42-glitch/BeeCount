package com.cycling.beecount.ui.assistant

import android.Manifest
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cycling.beecount.R
import kotlinx.coroutines.launch
import java.io.File

/**
 * 相机预览区域 + 拍摄按钮。
 * 独立拆出以避免 LaunchedEffect 与 AndroidView 的重组竞争。
 */
@Composable
private fun CameraPreviewWithCapture(
    context: Context,
    imageCapture: ImageCapture,
    cameraProvider: ProcessCameraProvider?,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    onCapture: (Uri) -> Unit,
) {
    androidx.compose.foundation.layout.Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { previewView ->
                    previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            update = { previewView ->
                val provider = cameraProvider ?: return@AndroidView
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                try {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture,
                    )
                } catch (_: Exception) { /* 设备无后置摄像头时忽略 */ }
            },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f),
        )

        androidx.compose.foundation.layout.Spacer(Modifier.padding(12.dp))

        Button(
            onClick = { captureImage(context, imageCapture, onCapture) },
            shape = CircleShape,
            modifier = Modifier.padding(bottom = 8.dp),
        ) {
            Text(stringResource(R.string.camera_capture_button))
        }
    }
}

private fun captureImage(
    context: Context,
    imageCapture: ImageCapture,
    onCapture: (Uri) -> Unit,
) {
    val cacheDir = File(context.cacheDir, "camera").also { it.mkdirs() }
    val file = File.createTempFile("ocr_", ".jpg", cacheDir)
    val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()
    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                )
                onCapture(uri)
            }

            override fun onError(exc: ImageCaptureException) {
                // 拍照失败：文件不存在，不回调，Sheet 保持打开
            }
        },
    )
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraCaptureSheet(
    onImageCaptured: (Uri) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val imageCapture = remember { ImageCapture.Builder().build() }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    // 绑定 CameraX 生命周期
    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission) return@LaunchedEffect
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            cameraProvider = providerFuture.get()
        }, ContextCompat.getMainExecutor(context))
    }

    DisposableEffect(Unit) {
        onDispose { cameraProvider?.unbindAll() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (hasCameraPermission) {
                CameraPreviewWithCapture(
                    context = context,
                    imageCapture = imageCapture,
                    cameraProvider = cameraProvider,
                    lifecycleOwner = lifecycleOwner,
                    onCapture = { uri ->
                        scope.launch {
                            sheetState.hide()
                            onImageCaptured(uri)
                            onDismiss()
                        }
                    },
                )
            } else {
                Text(
                    text = stringResource(R.string.camera_permission_denied),
                    modifier = Modifier.padding(32.dp),
                )
            }
        }
    }
}
