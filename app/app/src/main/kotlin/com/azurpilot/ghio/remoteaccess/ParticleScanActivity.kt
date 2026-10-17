package com.azurpilot.ghio.remoteaccess

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import com.azurpilot.ghio.R
import com.azurpilot.ghio.theme.AzurPilotTheme
import com.azurpilot.ghio.ui.ksu.AppPageScaffold
import com.azurpilot.ghio.ui.ksu.AppText
import java.util.ArrayDeque
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

/**
 * 用 CameraX 原生读取双色相位，不保存相机帧；只向本应用返回经过校验的单次扫描密钥。
 *
 * Reads optical phases with native CameraX without saving frames, returning only a validated one-time secret.
 */
class ParticleScanActivity : ComponentActivity() {
    private var allowed by mutableStateOf(false)
    private var failed by mutableStateOf(false)
    private val executor = Executors.newSingleThreadExecutor()
    private val completed = AtomicBoolean(false)
    private var analysis: ImageAnalysis? = null
    private var preview: Preview? = null
    private var provider: ProcessCameraProvider? = null
    private val history = ArrayDeque<Pair<Long, List<FloatArray>>>()
    private var lastFrame = 0L
    private var lastSecret: String? = null
    private var confirmations = 0
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        allowed = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (!allowed) permission.launch(Manifest.permission.CAMERA)
        setContent {
            AzurPilotTheme {
                AppPageScaffold(title = stringResource(R.string.remote_pair_scan), onBack = { finish() }) { padding ->
                    Column(modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        AppText(stringResource(if (!allowed) R.string.remote_pair_camera_permission
                            else if (failed) R.string.remote_pair_failed else R.string.remote_pair_scan_hint),
                            style = MaterialTheme.typography.bodyLarge)
                        if (allowed) Box(Modifier.fillMaxWidth().aspectRatio(1f).clipToBounds()) {
                            AndroidView(factory = { context -> PreviewView(context).apply {
                                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                                doOnLayout { bindCamera(this) }
                            } }, modifier = Modifier.fillMaxSize())
                            Canvas(Modifier.fillMaxSize()) {
                                val margin = size.width * .11f
                                drawRect(Color.Cyan, Offset(margin, margin),
                                    ComposeSize(size.width * .78f, size.height * .78f), style = Stroke(2.dp.toPx()))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun bindCamera(view: PreviewView) {
        val pending = ProcessCameraProvider.getInstance(this)
        pending.addListener({
            if (isDestroyed) return@addListener
            runCatching {
                val camera = pending.get()
                provider = camera
                val output = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                val reader = ImageAnalysis.Builder()
                    .setTargetResolution(Size(640, 480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888).build()
                preview = output; analysis = reader
                reader.setAnalyzer(executor, ::analyze)
                // 预览与分析共用正方形视口，取景框才能对应 ImageProxy 的有效裁剪区。
                val group = UseCaseGroup.Builder().addUseCase(output).addUseCase(reader)
                    .setViewPort(requireNotNull(view.viewPort)).build()
                camera.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, group)
            }.onFailure { failed = true }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun sample(image: ImageProxy): List<FloatArray> {
        val plane = image.planes[0]
        val bytes = plane.buffer
        val crop = image.cropRect
        val shortSide = min(crop.width(), crop.height()).toFloat()
        val offsets = listOf(0f to 0f, -.04f to 0f, .04f to 0f, 0f to -.04f, 0f to .04f)
        return listOf(.84f, .92f, 1f, 1.08f, 1.16f).flatMap { scale -> offsets.map { (dx, dy) ->
            val side = shortSide * .78f * scale
            val left = crop.left + (crop.width() - side) / 2 + dx * shortSide
            val top = crop.top + (crop.height() - side) / 2 + dy * shortSide
            FloatArray(324) { index ->
                val x = (left + (index % 18 + .5f) * side / 18).toInt().coerceIn(1, image.width - 2)
                val y = (top + (index / 18 + .5f) * side / 18).toInt().coerceIn(1, image.height - 2)
                var total = 0
                for (oy in -1..1) for (ox in -1..1) {
                    total += bytes.get((y + oy) * plane.rowStride + (x + ox) * plane.pixelStride + 1).toInt() and 255
                }
                total / 9f
            }
        } }
    }

    private fun analyze(image: ImageProxy) {
        try {
            val now = image.imageInfo.timestamp
            if (completed.get() || now - lastFrame < 80_000_000L) return
            lastFrame = now
            val current = sample(image)
            while (history.isNotEmpty() && now - history.first.first > 900_000_000L) history.removeFirst()
            var found: String? = null
            for ((timestamp, previous) in history) {
                if (now - timestamp !in 180_000_000L..440_000_000L) continue
                for (index in current.indices) {
                    found = ParticleCode.decode(current[index], previous[index])
                    if (found != null) break
                }
                if (found != null) break
            }
            history.addLast(now to current)
            if (found != null) {
                confirmations = if (found == lastSecret) confirmations + 1 else 1
                lastSecret = found
                if (confirmations >= 2 && completed.compareAndSet(false, true)) runOnUiThread {
                    setResult(RESULT_OK, Intent().putExtra("particle_secret", found))
                    finish()
                }
            }
        } finally { image.close() }
    }

    override fun onDestroy() {
        analysis?.clearAnalyzer()
        preview?.let { provider?.unbind(it) }
        analysis?.let { provider?.unbind(it) }
        executor.shutdownNow()
        super.onDestroy()
    }
}
