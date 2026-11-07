package app.datalaw.lexa.platform

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/** LANG — нет голоса/данных для этого языка; ENGINE — в системе нет синтезатора; FAILED — сбой воспроизведения. */
class SpeechException(val kind: Kind, message: String? = null) : Exception(message ?: kind.name) {
    enum class Kind { LANG, ENGINE, FAILED }
}

/** Озвучка системным синтезатором Android (офлайн, если голоса установлены). */
class Speaker(private val context: Context) {
    private var tts: TextToSpeech? = null
    private val initLock = Mutex()
    private val seq = AtomicLong(0)
    private val lock = Any()
    private var activeId: String? = null
    private var active: CompletableDeferred<Boolean>? = null

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit
        override fun onDone(utteranceId: String?) = finish(utteranceId, true)

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = finish(utteranceId, false)
        override fun onError(utteranceId: String?, errorCode: Int) = finish(utteranceId, false)
        override fun onStop(utteranceId: String?, interrupted: Boolean) = finish(utteranceId, true)
    }

    private fun finish(id: String?, ok: Boolean) {
        val d = synchronized(lock) {
            if (id == null || id != activeId) return
            activeId = null
            active.also { active = null }
        }
        d?.complete(ok)
    }

    private suspend fun engine(): TextToSpeech {
        tts?.let { return it }
        return initLock.withLock {
            tts?.let { return@withLock it }
            val ok = CompletableDeferred<Boolean>()
            val created = withContext(Dispatchers.Main) {
                TextToSpeech(context.applicationContext) { status -> ok.complete(status == TextToSpeech.SUCCESS) }
            }
            if (!ok.await()) {
                runCatching { created.shutdown() }
                throw SpeechException(SpeechException.Kind.ENGINE)
            }
            created.setOnUtteranceProgressListener(listener)
            tts = created
            created
        }
    }

    /** Произносит текст; возвращается, когда фраза произнесена. Новый вызов вытесняет предыдущий. */
    suspend fun speak(text: String, locale: String, rate: Float = 0.92f) {
        if (text.isBlank()) return
        val engine = try {
            engine()
        } catch (e: SpeechException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw SpeechException(SpeechException.Kind.ENGINE, e.message)
        }
        val r = engine.setLanguage(Locale.forLanguageTag(locale))
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            throw SpeechException(SpeechException.Kind.LANG)
        }
        engine.setSpeechRate(rate)
        val id = "lexa-${seq.incrementAndGet()}"
        val done = CompletableDeferred<Boolean>()
        val previous = synchronized(lock) {
            val p = active
            activeId = id
            active = done
            p
        }
        previous?.complete(true)
        if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), id) != TextToSpeech.SUCCESS) {
            synchronized(lock) {
                if (activeId == id) {
                    activeId = null
                    active = null
                }
            }
            throw SpeechException(SpeechException.Kind.FAILED)
        }
        try {
            if (!done.await()) throw SpeechException(SpeechException.Kind.FAILED)
        } catch (e: CancellationException) {
            runCatching { engine.stop() }
            throw e
        }
    }

    fun stop() {
        runCatching { tts?.stop() }
        val d = synchronized(lock) {
            activeId = null
            active.also { active = null }
        }
        d?.complete(true)
    }

    fun shutdown() {
        stop()
        runCatching { tts?.shutdown() }
        tts = null
    }
}
