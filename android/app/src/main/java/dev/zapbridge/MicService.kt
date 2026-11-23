package dev.zapbridge

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.net.Socket
import kotlin.concurrent.thread

/** Streams the phone mic to the PC as raw 48 kHz mono PCM16 over TCP. Foreground so it keeps working with the screen off. */
class MicService : Service() {
    companion object {
        const val PORT = 8767
        const val RATE = 48000
        @Volatile var running = false
    }

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val host = intent?.getStringExtra("host")
        if (host == null) { stopSelf(); return START_NOT_STICKY }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("mic", "Mikrofon", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "mic")
            .setContentTitle("ZapBridge mikrofon")
            .setContentText("Ovoz PC'ga uzatilmoqda")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true).build()
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        if (!running) {
            running = true
            val pin = intent.getStringExtra("pin").orEmpty()
            thread { stream(host, pin) }
        }
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission") // MainActivity asks for RECORD_AUDIO before starting us
    private fun stream(host: String, pin: String) {
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        // VOICE_COMMUNICATION turns on the phone's own echo cancelling and noise suppression
        val rec = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, min * 2)
        try {
            Socket(host, PORT).use { s ->
                s.tcpNoDelay = true
                val out = s.getOutputStream()
                out.write("$pin\n".toByteArray())
                rec.startRecording()
                val buf = ByteArray(RATE / 100 * 2) // 10 ms frames keep latency low
                while (running) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n > 0) out.write(buf, 0, n)
                }
            }
        } catch (_: Exception) {
        } finally {
            rec.release()
            running = false
            stopSelf()
        }
    }

    override fun onDestroy() { running = false }
}
