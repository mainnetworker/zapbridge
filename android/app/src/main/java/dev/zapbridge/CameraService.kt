package dev.zapbridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleService
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.Socket
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/** Sends phone camera frames to the PC as JPEGs over TCP; the PC shows them as a webcam. */
class CameraService : LifecycleService() {
    companion object {
        const val PORT = 8768
        @Volatile var running = false
    }

    private val worker = Executors.newSingleThreadExecutor()
    private var socket: Socket? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val host = intent?.getStringExtra("host")
        if (host == null || running) return START_NOT_STICKY
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("cam", "Kamera", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "cam")
            .setContentTitle("ZapBridge kamera")
            .setContentText("Kamera PC'ga uzatilmoqda")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true).build()
        startForeground(2, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        running = true
        val pin = intent.getStringExtra("pin").orEmpty()
        val front = intent.getBooleanExtra("front", false)
        thread {
            try {
                val s = Socket(host, PORT).apply { tcpNoDelay = true }
                socket = s
                val out = DataOutputStream(s.getOutputStream().buffered(1 shl 16))
                out.write("$pin\n".toByteArray()); out.flush()
                mainExecutor.execute { startCamera(out, front) }
            } catch (e: Exception) { stopSelf() }
        }
        return START_NOT_STICKY
    }

    private fun startCamera(out: DataOutputStream, front: Boolean) {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(
                    ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)).build())
                // while a frame is still being sent, newer ones replace it: no lag build-up on slow Wi-Fi
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
            val jpeg = ByteArrayOutputStream(256 * 1024)
            analysis.setAnalyzer(worker) { img ->
                try {
                    val rot = img.imageInfo.rotationDegrees / 90
                    jpeg.reset()
                    img.toBitmap().compress(Bitmap.CompressFormat.JPEG, 70, jpeg)
                    out.writeByte(rot); out.writeInt(jpeg.size()); jpeg.writeTo(out); out.flush()
                } catch (e: Exception) {
                    mainExecutor.execute { stopSelf() }
                } finally { img.close() }
            }
            val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            future.get().unbindAll()
            future.get().bindToLifecycle(this, selector, analysis)
        }, mainExecutor)
    }

    override fun onDestroy() {
        running = false
        socket?.close()
        worker.shutdown()
        super.onDestroy()
    }
}
