package app.datalaw.lexa.platform

import android.content.Context
import android.content.Intent

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
}
