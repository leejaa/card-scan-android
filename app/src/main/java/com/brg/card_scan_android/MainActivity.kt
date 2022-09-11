package com.brg.card_scan_android

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebView.setWebContentsDebuggingEnabled
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import java.util.concurrent.ExecutorService

class MainActivity : ComponentActivity() {

    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val navController = rememberNavController()

            NavHost(navController = navController, startDestination = "Home") {

                composable(route = "Home") {
                    val context = LocalContext.current
                    HomeScreen("https://brg-test.vercel.app/webviewhome", onClick = {
                        context.startActivity(Intent(context, CameraActivity::class.java))
                    })
                }
                composable(route = "Scan") {
                    ScanScreen()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }
}

class JSBridge(onClick: () -> Unit) {
    val handleClick = onClick
    @JavascriptInterface
    fun cardScan(message:String){
        handleClick()
    }
}

@Composable
fun rememberWebView(url: String, onClick: () -> Unit): WebView {
    val context = LocalContext.current
    val webview = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            webViewClient = WebViewClient()
            addJavascriptInterface(JSBridge(onClick), "JSBridge")
            setWebContentsDebuggingEnabled(true)
            loadUrl(url)
        }
    }
    return webview
}

@Composable
fun HomeScreen(url: String, onClick: () -> Unit) {
    val webview = rememberWebView(url, onClick)

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { webview }
    )
}
