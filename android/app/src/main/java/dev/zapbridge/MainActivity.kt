package dev.zapbridge

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.text.InputType
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val io = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("zapbridge", MODE_PRIVATE) }
    private var base: String?
        get() = prefs.getString("base", null)
        set(v) = prefs.edit().putString("base", v).apply()

    private lateinit var status: TextView
    private lateinit var pin: EditText
    private lateinit var list: ArrayAdapter<String>
    private val names = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply { text = "PC topilmagan"; textSize = 16f }
        pin = EditText(this).apply {
            hint = "PC'dagi PIN"; inputType = InputType.TYPE_CLASS_NUMBER
            setText(prefs.getString("pin", ""))
        }
        list = ArrayAdapter(this, android.R.layout.simple_list_item_1)
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
            addView(status); addView(pin)
            addView(Button(context).apply { text = "PC'ni topish"; setOnClickListener { savePin(); discover() } })
            addView(Button(context).apply { text = "PC'ga fayl yuborish"; setOnClickListener { pickFiles() } })
            addView(Button(context).apply { text = "PC fayllarini yangilash"; setOnClickListener { refresh() } })
            addView(ListView(context).apply {
                adapter = list
                setOnItemClickListener { _, _, i, _ -> download(names[i]) }
            })
        })
        if (base != null) refresh() else discover()
        handleShare(intent)
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handleShare(intent) }

    private fun savePin() = prefs.edit().putString("pin", pin.text.toString()).apply()
    private fun say(msg: String) = runOnUiThread { status.text = msg }

    @Suppress("DEPRECATION") // typed overloads need API 33, minSdk is 29
    private fun handleShare(i: Intent) {
        val uris = when (i.action) {
            Intent.ACTION_SEND -> listOfNotNull(i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
            Intent.ACTION_SEND_MULTIPLE -> i.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
            else -> return
        }
        uris.forEach(::upload)
    }

    /** Broadcast "ZAPBRIDGE?" on the LAN; the PC replies with its port. */
    private fun discover() = io.execute {
        say("PC qidirilmoqda…")
        try {
            DatagramSocket().use { s ->
                s.broadcast = true; s.soTimeout = 2000
                val q = "ZAPBRIDGE?".toByteArray()
                s.send(DatagramPacket(q, q.size, InetAddress.getByName("255.255.255.255"), 8766))
                val buf = ByteArray(256)
                val r = DatagramPacket(buf, buf.size).also { s.receive(it) }
                val port = JSONObject(String(buf, 0, r.length)).getInt("port")
                base = "http://${r.address.hostAddress}:$port"
            }
            refreshNow()
        } catch (e: Exception) {
            say("PC topilmadi: bir Wi-Fi'dami va server.py ishlayaptimi?")
        }
    }

    private fun conn(path: String, method: String = "GET") =
        (URL(base + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("X-PIN", prefs.getString("pin", ""))
        }

    private fun enc(name: String) = URLEncoder.encode(name, "UTF-8").replace("+", "%20")

    private fun refresh() = io.execute { refreshNow() }
    private fun refreshNow() {
        try {
            val c = conn("/files")
            if (c.responseCode == 401) return say("PIN noto'g'ri")
            val arr = JSONArray(c.inputStream.bufferedReader().readText())
            val items = (0 until arr.length()).map { arr.getJSONObject(it) }
            runOnUiThread {
                names.clear(); names += items.map { it.getString("name") }
                list.clear(); list.addAll(items.map { "${it.getString("name")}  (${it.getLong("size") / 1024} KB)" })
                status.text = "Ulandi: $base"
            }
        } catch (e: Exception) { say("Xato: ${e.message}") }
    }

    private fun pickFiles() = startActivityForResult(
        Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE)
            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true), 1)

    @Deprecated("simple and enough here")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        if (res != RESULT_OK || data == null) return
        data.clipData?.let { c -> (0 until c.itemCount).forEach { upload(c.getItemAt(it).uri) } }
            ?: data.data?.let(::upload)
    }

    private fun upload(uri: Uri) = io.execute {
        try {
            if (base == null) return@execute say("Avval PC'ni toping")
            var name = "file"; var size = -1L
            contentResolver.query(uri, null, null, null, null)?.use {
                if (it.moveToFirst()) {
                    name = it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                    size = it.getLong(it.getColumnIndexOrThrow(OpenableColumns.SIZE))
                }
            }
            if (size < 0) return@execute say("Fayl hajmi noma'lum: $name")
            val t0 = System.nanoTime()
            val c = conn("/files/${enc(name)}", "PUT").apply { doOutput = true; setFixedLengthStreamingMode(size) }
            contentResolver.openInputStream(uri)!!.use { inp -> c.outputStream.use { inp.copyTo(it, 1 shl 20) } }
            if (c.responseCode != 201) return@execute say("Yuborilmadi: ${c.responseCode}")
            say("Yuborildi: $name — ${speed(size, t0)}")
            refreshNow()
        } catch (e: Exception) { say("Xato: ${e.message}") }
    }

    /** Saves into the phone's Downloads folder via MediaStore (no storage permission needed). */
    private fun download(name: String) = io.execute {
        try {
            say("Yuklanmoqda: $name…")
            val t0 = System.nanoTime()
            val c = conn("/files/${enc(name)}")
            if (c.responseCode != 200) return@execute say("Yuklab bo'lmadi: ${c.responseCode}")
            val values = ContentValues().apply { put(MediaStore.Downloads.DISPLAY_NAME, name) }
            val out = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
            c.inputStream.use { inp -> contentResolver.openOutputStream(out)!!.use { inp.copyTo(it, 1 shl 20) } }
            say("Saqlandi (Downloads): $name — ${speed(c.contentLengthLong, t0)}")
        } catch (e: Exception) { say("Xato: ${e.message}") }
    }

    private fun speed(bytes: Long, t0: Long): String {
        val sec = (System.nanoTime() - t0) / 1e9
        return "%.1f MB/s".format(bytes / 1e6 / sec.coerceAtLeast(0.001))
    }
}
