package to.bitkit.ui.screens.wallets.send

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Build
import android.view.View.LAYER_TYPE_HARDWARE
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import to.bitkit.R
import to.bitkit.ext.getClipboardText
import to.bitkit.ext.startActivityAppSettings
import to.bitkit.models.Toast
import to.bitkit.ui.appViewModel
import to.bitkit.ui.components.BottomSheetPreview
import to.bitkit.ui.components.RectangleButton
import to.bitkit.ui.scaffold.SheetTopBar
import to.bitkit.ui.screens.scanner.CameraPermissionView
import to.bitkit.ui.screens.scanner.DeniedContent
import to.bitkit.ui.screens.scanner.QrCodeAnalyzer
import to.bitkit.ui.shared.modifiers.sheetHeight
import to.bitkit.ui.shared.util.gradientBackground
import to.bitkit.ui.theme.AppThemeSurface
import to.bitkit.ui.theme.Colors
import to.bitkit.ui.theme.Shapes
import to.bitkit.utils.Logger
import to.bitkit.viewmodels.SendEvent
import java.util.concurrent.Executors
import androidx.camera.core.Preview as CameraPreview

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SendRecipientScreen(
    onEvent: (SendEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val app = appViewModel

    // Context & lifecycle
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    // Camera state
    var isFlashlightOn by remember { mutableStateOf(false) }
    val previewView = remember { PreviewView(context) }
    val preview = remember { CameraPreview.Builder().build() }
    var camera by remember { mutableStateOf<Camera?>(null) }

    // Fixed back camera selector
    val cameraSelector = remember {
        CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()
    }

    // Request permission on screen start
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                cameraPermissionState.launchPermissionRequest()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // QR code analyzer with auto-proceed callback
    val analyzer = remember {
        QrCodeAnalyzer { result ->
            if (result.isSuccess) {
                val qrCode = result.getOrThrow()
                Logger.debug("QR scanned: $qrCode")
                onEvent(SendEvent.AddressContinue(qrCode))
            } else {
                val error = requireNotNull(result.exceptionOrNull())
                Logger.error("Scan failed", error)
                app?.toast(
                    type = Toast.ToastType.ERROR,
                    title = context.getString(R.string.other__qr_error_header),
                    description = context.getString(R.string.other__qr_error_text),
                )
            }
        }
    }

    val imageAnalysis = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
    }

    LaunchedEffect(Unit) {
        imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor(), analyzer)
    }

    // Camera binding
    LaunchedEffect(Unit) {
        val cameraProvider = withContext(Dispatchers.IO) {
            ProcessCameraProvider.getInstance(context).get()
        }
        camera = cameraProvider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            preview,
            imageAnalysis
        )
        preview.surfaceProvider = previewView.surfaceProvider
    }

    // Camera cleanup
    DisposableEffect(Unit) {
        onDispose {
            camera?.let {
                ProcessCameraProvider.getInstance(context).get().unbindAll()
            }
        }
    }

    // Gallery picker launchers
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            uri?.let {
                processImageFromGallery(
                    context = context,
                    uri = it,
                    onScanSuccess = { qrCode ->
                        Logger.debug("QR from gallery: $qrCode")
                        onEvent(SendEvent.AddressContinue(qrCode))
                    },
                    onError = { e ->
                        app?.toast(e)
                    }
                )
            }
        }
    )

    val pickMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            processImageFromGallery(
                context = context,
                uri = it,
                onScanSuccess = { qrCode ->
                    Logger.debug("QR from photo picker: $qrCode")
                    onEvent(SendEvent.AddressContinue(qrCode))
                },
                onError = { e ->
                    app?.toast(e)
                }
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .gradientBackground()
            .navigationBarsPadding()
    ) {
        SheetTopBar(titleText = stringResource(R.string.wallet__send_bitcoin))
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                CameraPermissionView(
                    permissionState = cameraPermissionState,
                    deniedContent = {
                        DeniedContent(
                            shouldShowRationale = cameraPermissionState.status.shouldShowRationale,
                            inSheet = true,
                            onClickOpenSettings = {
                                context.startActivityAppSettings()
                            },
                            onClickRetry = cameraPermissionState::launchPermissionRequest,
                            onClickPaste = {
                                val clipboard = context.getClipboardText()?.trim()
                                if (clipboard.isNullOrBlank()) {
                                    app?.toast(
                                        type = Toast.ToastType.WARNING,
                                        title = context.getString(R.string.wallet__send_clipboard_empty_title),
                                        description = context.getString(R.string.wallet__send_clipboard_empty_text),
                                    )
                                } else {
                                    onEvent(SendEvent.AddressContinue(clipboard))
                                }
                            },
                            onBack = { /* No back needed in sheet */ },
                        )
                    },
                    grantedContent = {
                        CameraPreviewWithControls(
                            previewView = previewView,
                            onClickFlashlight = {
                                isFlashlightOn = !isFlashlightOn
                                camera?.cameraControl?.enableTorch(isFlashlightOn)
                            },
                            onClickGallery = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    pickMedia.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                } else {
                                    galleryLauncher.launch("image/*")
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                )
            }

            RectangleButton(
                label = stringResource(R.string.wallet__recipient_contact),
                icon = {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .size(40.dp)
                            .background(Colors.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_users),
                            contentDescription = null,
                            tint = Colors.Brand,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
                modifier = Modifier.testTag("RecipientContact")
            ) {
                scope.launch {
                    app?.toast(Exception("Coming soon: Contact"))
                }
            }

            RectangleButton(
                label = stringResource(R.string.wallet__recipient_invoice),
                icon = {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .size(40.dp)
                            .background(Colors.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_clipboard_text),
                            contentDescription = null,
                            tint = Colors.Brand,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
                modifier = Modifier.testTag("RecipientInvoice")
            ) {
                onEvent(SendEvent.Paste)
            }

            RectangleButton(
                label = stringResource(R.string.wallet__recipient_manual),
                icon = {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .size(40.dp)
                            .background(Colors.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_pencil_simple),
                            contentDescription = null,
                            tint = Colors.Brand,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
                modifier = Modifier.testTag("RecipientManual")
            ) {
                onEvent(SendEvent.EnterManually)
            }
        }
    }
}

@Composable
private fun CameraPreviewWithControls(
    previewView: PreviewView,
    onClickFlashlight: () -> Unit,
    onClickGallery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(Shapes.medium)
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds(),
            factory = {
                previewView.apply {
                    setLayerType(LAYER_TYPE_HARDWARE, null)
                }
            }
        )

        // Gallery button (top-left)
        IconButton(
            onClick = onClickGallery,
            modifier = Modifier
                .padding(16.dp)
                .clip(CircleShape)
                .background(Colors.White64)
                .size(48.dp)
                .align(Alignment.TopStart)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_image_square),
                contentDescription = null,
                tint = Colors.White
            )
        }

        // Flashlight button (top-right)
        IconButton(
            onClick = onClickFlashlight,
            modifier = Modifier
                .padding(16.dp)
                .clip(CircleShape)
                .background(Colors.White64)
                .size(48.dp)
                .align(Alignment.TopEnd)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_flashlight),
                contentDescription = null,
                tint = Colors.White
            )
        }
    }
}

private fun processImageFromGallery(
    context: Context,
    uri: Uri,
    onScanSuccess: (String) -> Unit,
    onError: (Exception) -> Unit,
) {
    try {
        val image = InputImage.fromFilePath(context, uri)
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        val scanner = BarcodeScanning.getClient(options)

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    barcode.rawValue?.let { qrCode ->
                        onScanSuccess(qrCode)
                        Logger.info("QR from gallery: $qrCode")
                        return@addOnSuccessListener
                    }
                }
                Logger.error("No QR code in image")
                onError(Exception(context.getString(R.string.other__qr_error_text)))
            }
            .addOnFailureListener { e ->
                Logger.error("Gallery scan failed", e)
                onError(e)
            }
    } catch (e: IllegalArgumentException) {
        Logger.error("Gallery processing failed", e)
        onError(e)
    }
}

@Preview(showSystemUi = true)
@Composable
private fun Preview() {
    AppThemeSurface {
        BottomSheetPreview {
            SendRecipientScreen(
                onEvent = {},
                modifier = Modifier.sheetHeight(),
            )
        }
    }
}
