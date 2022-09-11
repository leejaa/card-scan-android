package com.brg.card_scan_android

import android.Manifest
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.Rational
import android.util.Size
import android.view.Surface
import android.view.Surface.ROTATION_270
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionRequired
import com.google.accompanist.permissions.rememberPermissionState
import com.google.firebase.FirebaseApp
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.ktx.storage
import com.google.mlkit.vision.objects.DetectedObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*


class CameraActivity : AppCompatActivity() {

    lateinit var storage: FirebaseStorage

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let{
            File(it, resources.getString(R.string.app_name)).apply{mkdirs()}
        }
        return if(mediaDir != null && mediaDir.exists())
            mediaDir
        else
            filesDir
    }

    @ExperimentalPermissionsApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        FirebaseApp.initializeApp(this)

        storage = Firebase.storage("gs://image-upload2-dbafc.appspot.com")

        setContent {
            val viewModel = viewModel<MainViewModel>()

            val navController = rememberNavController()

            NavHost(navController = navController, startDestination = "CameraHome") {
                composable(route = "CameraHome") {
                    CameraHome(getOutputDirectory = { getOutputDirectory() }, storage = storage, viewModel = viewModel, navController)
                }
                composable(route = "Result") {
                    WebViewScreen(url = viewModel.url.value.toString())
                }
            }

        }
    }
}

@ExperimentalPermissionsApi
@Composable
fun CameraHome(
    getOutputDirectory: () -> File,
    storage: FirebaseStorage,
    viewModel: MainViewModel,
    navController: NavController
) {
    var widthFactor by remember { mutableStateOf<Int>(480) }
    var heightFactor by remember { mutableStateOf<Int>(640) }
//    var top by remember { mutableStateOf<Float>(0.0F) }
//    var bottom by remember { mutableStateOf<Float>(0.0F) }
//    var right by remember { mutableStateOf<Float>(0.0F) }
//    var left by remember { mutableStateOf<Float>(0.0F) }

    ComposePlaygroundTheme {
        // A surface container using the 'background' color from the theme
        Surface(color = MaterialTheme.colors.background) {
            val permission = Manifest.permission.CAMERA
            val permissionState = rememberPermissionState(permission)

            LaunchedEffect(Unit) {
                permissionState.launchPermissionRequest()
            }

            PermissionRequired(
                permissionState = permissionState,
                {}, {}, {
                    val detectedObjects = mutableStateListOf<DetectedObject>()

                    Box {
                        CameraPreview(detectedObjects, onChange = { newWidthFactor, newHeightFactor ->
                            widthFactor = newWidthFactor
                            heightFactor = newHeightFactor
                        }, getOutputDirectory = { getOutputDirectory() }, storage, viewModel, navController
                        , width = heightFactor, height = widthFactor
                        )
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawIntoCanvas { canvas ->
                                detectedObjects.forEach {
                                    val rect = it.boundingBox.toComposeRect()
                                    val newRect = Rect(Offset(x = rect.left - 30F, y = rect.top), Offset(x = rect.right + 30F, y = rect.bottom))

                                    canvas.scale(size.width / widthFactor, size.height / heightFactor)
                                    canvas.drawRect(
                                        newRect,
                                        Paint().apply {
                                            color = Color.White
                                            style = PaintingStyle.Stroke
                                            strokeWidth = 3f
                                        })
//                                            canvas.nativeCanvas.drawText(
//                                                "TrackingId_${it.trackingId}",
//                                                it.boundingBox.left.toFloat(),
//                                                it.boundingBox.top.toFloat(),
//                                                android.graphics.Paint().apply {
//                                                    color = Color.Green.toArgb()
//                                                    textSize = 20f
//                                                })
                                }

                            }

                        }
                    }

                }
            )

        }
    }
}

@Composable
private fun CameraPreview(
    detectedObjects: SnapshotStateList<DetectedObject>,
    onChange: (widthFactor: Int, heightFactor: Int) -> Unit,
    getOutputDirectory: () -> File,
    storage: FirebaseStorage,
    viewModel: MainViewModel,
    navController: NavController,
    width: Int,
    height: Int,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val imageCapture: ImageCapture = remember { ImageCapture.Builder().build() }
    val executor = ContextCompat.getMainExecutor(context)

    val coroutineScope = rememberCoroutineScope()
    val objectAnalyzer = remember { ObjectAnalyzer(coroutineScope, detectedObjects, onChange) }

    val buttonText = if(viewModel.isLoading.value) "이미지를 업로드중입니다..." else "이미지 캡쳐"

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val executor = ContextCompat.getMainExecutor(ctx)

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(executor, objectAnalyzer)
                }

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .build()

                val useCaseGroup = UseCaseGroup.Builder()
                    .addUseCase(preview)
                    .addUseCase(imageAnalyzer)
                    .addUseCase(imageCapture)
                    .build()

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    useCaseGroup
                )
            }, executor)
            previewView
        },
        modifier = Modifier.fillMaxSize(),
    )
    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        Text(modifier = Modifier.align(Alignment.TopCenter).padding(vertical = 30.dp), text = "카드에 카메라를 대주세요", color = Color.White, fontSize = 20.sp)
        Button(modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(30.dp), onClick = {
            viewModel.isLoading.value = true

            val outputDirectory = getOutputDirectory()

            val fileName = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.KOREA)
                .format(System.currentTimeMillis()) + ".jpg"

            val photoFile = File(outputDirectory, fileName)

            val outputOptions = ImageCapture
                .OutputFileOptions
                .Builder(photoFile)
                .build()

            imageCapture.takePicture(
                outputOptions,
                executor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onError(exception: ImageCaptureException) {
                        Log.e("error", "Photo capture failed: ${exception.message}", exception)
                    }

                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        val storageRef = storage.reference

                        var file = Uri.fromFile(outputFileResults.savedUri?.toFile())

                        val ref = storageRef.child("images/${fileName}")
                        var uploadTask = ref.putFile(file)

                        uploadTask.continueWithTask { task ->
                            if (!task.isSuccessful) {
                                task.exception?.let {
                                    throw it
                                }
                            }
                            ref.downloadUrl
                        }.addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                val detectedObject = detectedObjects?.firstOrNull()

                                val top = detectedObject?.boundingBox?.top ?: 0
                                val bottom = detectedObject?.boundingBox?.bottom ?: 0
                                val left = detectedObject?.boundingBox?.left ?: 0
                                val right = detectedObject?.boundingBox?.right ?: 0

                                Log.d("successful", "successful..........")
                                val downloadUri = task.result
                                viewModel.url.value = "https://brg-test.vercel.app/webview?imageUrl=${downloadUri.toString()}&width=${width}&height=${height}&top=${top}&bottom=${bottom}&right=${right}&left=${left}"
                                navController.navigate("Result")
                            } else {
                                Log.d("fail", "fail..........")
                            }
                            viewModel.isLoading.value = false
                        }
                    }
                }
            )
        }) {
            Text(text = buttonText, color = Color.White)
        }
    }
}

@Composable
fun rememberWebView(url: String): WebView {
    val context = LocalContext.current
    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            webViewClient = WebViewClient()
            loadUrl(url)
        }
    }
    return webView
}

@Composable
fun WebViewScreen(url: String) {
    val webview = rememberWebView(url)

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { webview },
    )
}

class MainViewModel : ViewModel() {
    val url = mutableStateOf("")
    val isLoading = mutableStateOf(false)
}