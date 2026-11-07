package app.datalaw.lexa.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns

/** Мелкие обёртки над системными возможностями. */
object Share {
    /** Системное меню «Поделиться» с текстом. */
    fun text(context: Context, title: String, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun copy(context: Context, label: String, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    /** Читает текстовый файл по Uri из системного выбора файла; не больше maxBytes. */
    fun readText(context: Context, uri: Uri, maxBytes: Int = 4 * 1024 * 1024): String {
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "cannot open $uri" }
            val buf = input.readNBytes(maxBytes + 1)
            require(buf.size <= maxBytes) { "file too large" }
            return String(buf, Charsets.UTF_8)
        }
    }

    fun writeText(context: Context, uri: Uri, text: String) {
        // "wt" — перезаписать, а не дописывать в конец старого содержимого
        context.contentResolver.openOutputStream(uri, "wt").use { out ->
            requireNotNull(out) { "cannot write $uri" }
            out.write(text.toByteArray(Charsets.UTF_8))
        }
    }

    fun fileName(context: Context, uri: Uri): String? =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment
}
