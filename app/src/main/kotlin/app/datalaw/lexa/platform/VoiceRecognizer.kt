package app.datalaw.lexa.platform

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import app.datalaw.lexa.core.Text

/**
 * permission  — нет доступа к микрофону;
 * unavailable — в системе нет службы распознавания речи;
 * network     — службе нужна сеть (нет офлайн-пакета языка);
 * noSpeech    — ничего не услышали / не разобрали;
 * lang        — язык не поддерживается службой;
 * busy        — служба занята другим запросом;
 * failed      — прочий сбой.
 */
enum class VoiceError { PERMISSION, UNAVAILABLE, NETWORK, NO_SPEECH, LANG, BUSY, FAILED }

enum class VoicePhase { STARTING, LISTENING, PROCESSING, ERROR }

interface VoiceHandlers {
    fun onPhase(phase: VoicePhase)

    /** Громкость голоса 0…1 (для анимации) */
    fun onLevel(level: Float)

    /** Промежуточный результат — слово появляется по мере произнесения */
    fun onPartial(text: String)

    /** Итог: варианты распознавания, лучший — первый */
    fun onFinal(alternatives: List<String>)

    fun onError(error: VoiceError)
}

interface VoiceSession {
    /** Закончить запись и выдать результат */
    fun stop()

    /** Прервать без результата */
    fun cancel()
}

/** Голосовой ввод через android.speech.SpeechRecognizer. Все вызовы — из главного потока. */
object VoiceRecognizer {
    fun available(context: Context): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun locale(russian: Boolean): String = if (russian) "ru-RU" else "it-IT"

    private fun mapError(error: Int): VoiceError = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> VoiceError.NO_SPEECH
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> VoiceError.NETWORK
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceError.PERMISSION
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> VoiceError.BUSY
        12, 13 -> VoiceError.LANG // ERROR_LANGUAGE_NOT_SUPPORTED / ERROR_LANGUAGE_UNAVAILABLE (API 31)
        else -> VoiceError.FAILED
    }

    private fun texts(b: Bundle?): List<String> =
        b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.filter { it.isNotBlank() } ?: emptyList()

    private val NOOP = object : VoiceSession {
        override fun stop() = Unit
        override fun cancel() = Unit
    }

    fun start(context: Context, lang: String, preferOffline: Boolean, h: VoiceHandlers): VoiceSession {
        if (!available(context)) {
            h.onError(VoiceError.UNAVAILABLE)
            return NOOP
        }
        var active = false
        var rec: SpeechRecognizer? = null

        fun destroy() {
            rec?.let { r -> runCatching { r.cancel(); r.destroy() } }
            rec = null
        }

        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                if (active) h.onPhase(VoicePhase.LISTENING)
            }

            override fun onBeginningOfSpeech() = Unit

            override fun onRmsChanged(rmsdB: Float) {
                if (!active) return
                // rmsdB ≈ −2…10 → 0…1
                h.onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
            }

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                if (active) h.onPhase(VoicePhase.PROCESSING)
            }

            override fun onError(error: Int) {
                if (!active) return
                active = false
                h.onError(mapError(error))
            }

            override fun onResults(results: Bundle?) {
                if (!active) return
                active = false
                h.onFinal(texts(results))
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (!active) return
                texts(partialResults).firstOrNull()?.let(h::onPartial)
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        }

        try {
            val r = SpeechRecognizer.createSpeechRecognizer(context)
            rec = r
            r.setRecognitionListener(listener)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
                .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            active = true
            r.startListening(intent)
        } catch (e: Exception) {
            active = false
            destroy()
            h.onError(VoiceError.FAILED)
            return NOOP
        }

        return object : VoiceSession {
            override fun stop() {
                if (active) runCatching { rec?.stopListening() }
            }

            override fun cancel() {
                // после cancel() запоздалые колбэки (ERROR_CLIENT и т.п.) игнорируем
                active = false
                destroy()
            }
        }
    }

    /** Нормализует варианты как поисковые ключи: без регистра и знаков по краям, без дублей, не более 5. */
    fun cleanAlternatives(raw: List<String>, russian: Boolean): List<String> {
        val out = ArrayList<String>()
        for (r in raw) {
            val k = if (russian) Text.normalizeRu(r) else Text.normalizeIt(r)
            if (k.isNotEmpty() && k !in out) out.add(k)
            if (out.size >= 5) break
        }
        return out
    }

    data class Pick(val text: String, val exact: Boolean)

    /**
     * Выбирает лучший вариант: первый, который есть в словаре (exact), иначе просто первый.
     * Распознаватель часто путает близкие слова — словарь помогает выбрать верное.
     */
    fun pickAlternative(candidates: List<String>, known: (String) -> Boolean): Pick? {
        for (c in candidates) if (known(c)) return Pick(c, true)
        return candidates.firstOrNull()?.let { Pick(it, false) }
    }
}
