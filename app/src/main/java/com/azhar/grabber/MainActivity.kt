package com.azhar.grabber

import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var urlBox: EditText
    private lateinit var typeSpin: Spinner
    private lateinit var qualSpin: Spinner
    private lateinit var goBtn: Button
    private lateinit var updBtn: Button
    private lateinit var bar: ProgressBar
    private lateinit var status: TextView
    private lateinit var historyView: TextView
    private var busy = false

    private val prefs by lazy { getSharedPreferences("grabber", Context.MODE_PRIVATE) }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        showHistory()
        handleIntent(intent)

        // prepare the engine in the background so the first download starts faster
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Engine.init(applicationContext)
                runOnUiThread { status.text = "Tayyar hai. Link paste karo." }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Engine shuru nahi hua: ${shortError(e)}" }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        if (i?.action == Intent.ACTION_SEND) {
            extractUrl(i.getStringExtra(Intent.EXTRA_TEXT))?.let { urlBox.setText(it) }
        }
    }

    private fun extractUrl(text: String?): String? =
        text?.let { Regex("https?://\\S+").find(it)?.value }

    // ------------------------------------------------------------------ UI
    private fun buildUi(): ScrollView {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(28))
        }

        root.addView(TextView(this).apply {
            text = "Grabber"
            textSize = 30f
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Link paste karo, video ya audio download karo."
            setPadding(0, dp(4), 0, dp(18))
        })

        urlBox = EditText(this).apply {
            hint = "Link yahan paste karo"
            setSingleLine(false)
            minLines = 2
            maxLines = 4
        }
        root.addView(urlBox, matchWrap())

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val pasteBtn = Button(this).apply {
            text = "Paste"
            setOnClickListener { pasteFromClipboard() }
        }
        val clearBtn = Button(this).apply {
            text = "Clear"
            setOnClickListener { urlBox.setText("") }
        }
        row.addView(pasteBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(clearBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(row, matchWrap())

        root.addView(label("Kya chahiye?"))
        typeSpin = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("Video", "Audio (MP3)")
            )
        }
        root.addView(typeSpin, matchWrap())

        root.addView(label("Video quality"))
        qualSpin = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("Sab se behtareen", "1080p", "720p", "480p")
            )
        }
        root.addView(qualSpin, matchWrap())

        goBtn = Button(this).apply {
            text = "Download"
            textSize = 18f
            setOnClickListener { startDownload() }
        }
        root.addView(goBtn, matchWrap().apply { topMargin = dp(16) })

        bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = android.view.View.GONE
        }
        root.addView(bar, matchWrap().apply { topMargin = dp(12) })

        status = TextView(this).apply {
            text = "Engine tayyar ho raha hai..."
            setPadding(0, dp(8), 0, dp(8))
        }
        root.addView(status, matchWrap())

        updBtn = Button(this).apply {
            text = "Engine update karo (agar download na chale)"
            setOnClickListener { updateEngine() }
        }
        root.addView(updBtn, matchWrap())

        root.addView(label("Pichle downloads").apply { setPadding(0, dp(22), 0, dp(4)) })
        historyView = TextView(this)
        root.addView(historyView, matchWrap())

        root.addView(TextView(this).apply {
            text = "Download hote waqt app khuli rakho. Files Movies/Grabber ya Music/Grabber folder mein save hoti hain. " +
                "Sirf apne personal use ke liye, aur doosron ke haqooq ka khayal rakho."
            textSize = 12f
            gravity = Gravity.START
            setPadding(0, dp(20), 0, 0)
        })

        return ScrollView(this).apply { addView(root) }
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun label(t: String) = TextView(this).apply {
        text = t
        setPadding(0, dp(14), 0, dp(2))
    }

    private fun pasteFromClipboard() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
        val url = extractUrl(text)
        if (url == null) toast("Clipboard mein koi link nahi mila") else urlBox.setText(url)
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    private fun setBusy(b: Boolean) {
        busy = b
        goBtn.isEnabled = !b
        updBtn.isEnabled = !b
        if (!b) bar.visibility = android.view.View.GONE
    }

    // ------------------------------------------------------------ download
    private fun startDownload() {
        val url = extractUrl(urlBox.text.toString())
        if (url == null) {
            toast("Pehle sahi link paste karo")
            return
        }
        if (busy) return
        setBusy(true)
        bar.visibility = android.view.View.VISIBLE
        bar.isIndeterminate = true
        status.text = "Download shuru ho raha hai..."

        val isAudio = typeSpin.selectedItemPosition == 1
        val height = when (qualSpin.selectedItemPosition) {
            1 -> 1080
            2 -> 720
            3 -> 480
            else -> 0
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val dir = File(cacheDir, "dl/${System.currentTimeMillis()}").apply { mkdirs() }
            try {
                Engine.init(applicationContext)

                val req = YoutubeDLRequest(url)
                req.addOption("--no-playlist")
                req.addOption("--no-mtime")
                req.addOption("-o", dir.absolutePath + "/%(title).80s.%(ext)s")
                if (isAudio) {
                    req.addOption("-x")
                    req.addOption("--audio-format", "mp3")
                    req.addOption("--audio-quality", "0")
                } else {
                    req.addOption("-f", "bv*+ba/b")
                    req.addOption(
                        "-S",
                        if (height > 0) "res:$height,vcodec:h264,acodec:m4a"
                        else "res,vcodec:h264,acodec:m4a"
                    )
                    req.addOption("--merge-output-format", "mp4")
                }

                YoutubeDL.getInstance().execute(req, null) { progress, _, _ ->
                    runOnUiThread {
                        if (progress >= 0f) {
                            bar.isIndeterminate = false
                            bar.progress = progress.toInt()
                            status.text = "Download ho raha hai: ${progress.toInt()}%"
                        }
                    }
                }

                val files = dir.listFiles()?.filter {
                    it.isFile && it.length() > 0 && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl")
                }.orEmpty()
                if (files.isEmpty()) throw IOException("Koi file nahi bani")

                runOnUiThread { status.text = "Phone mein save ho raha hai..." }
                val saved = files.map { f -> saveToPhone(f, isAudio).also { f.delete() } }
                addHistory(files.first().nameWithoutExtension, if (isAudio) "audio" else "video")
                runOnUiThread { status.text = "Ho gaya! Save hui: ${saved.first()}" }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Masla aaya: ${shortError(e)}" }
            } finally {
                dir.deleteRecursively()
                runOnUiThread { setBusy(false) }
            }
        }
    }

    private fun saveToPhone(f: File, isAudio: Boolean): String {
        val ext = f.extension.lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: if (isAudio) "audio/mpeg" else "video/mp4"
        val (collection, folder) = when {
            mime.startsWith("audio") -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_MUSIC
            mime.startsWith("video") -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_MOVIES
            mime.startsWith("image") -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_PICTURES
            else -> MediaStore.Downloads.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_DOWNLOADS
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, f.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$folder/Grabber")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(collection, values) ?: throw IOException("File save nahi hui")
        contentResolver.openOutputStream(uri)?.use { out ->
            f.inputStream().use { it.copyTo(out) }
        } ?: throw IOException("File save nahi hui")
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        contentResolver.update(uri, values, null, null)
        return "$folder/Grabber/${f.name}"
    }

    // -------------------------------------------------------------- update
    private fun updateEngine() {
        if (busy) return
        setBusy(true)
        bar.visibility = android.view.View.VISIBLE
        bar.isIndeterminate = true
        status.text = "Engine update ho raha hai..."
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Engine.init(applicationContext)
                val result = YoutubeDL.getInstance().updateYoutubeDL(applicationContext)
                runOnUiThread { status.text = "Update mukammal: $result" }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Update nahi hua: ${shortError(e)}" }
            } finally {
                runOnUiThread { setBusy(false) }
            }
        }
    }

    // ------------------------------------------------------------- history
    private fun addHistory(title: String, kind: String) {
        val old = prefs.getString("history", "").orEmpty().lines().filter { it.isNotBlank() }
        val updated = (listOf("$title ($kind)") + old).take(10)
        prefs.edit().putString("history", updated.joinToString("\n")).apply()
        runOnUiThread { showHistory() }
    }

    private fun showHistory() {
        val h = prefs.getString("history", "").orEmpty()
        historyView.text = if (h.isBlank()) "Abhi koi nahi" else h
    }

    private fun shortError(e: Throwable): String {
        val m = (e.message ?: e.toString()).trim()
        return if (m.length > 350) "..." + m.takeLast(350) else m
    }
}
