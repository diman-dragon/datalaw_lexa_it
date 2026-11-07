package app.datalaw.lexa.store

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.Locale

/** auto — по системе (светлая — светло-коричневая, тёмная — AMOLED) */
@Serializable
enum class ThemeMode {
    @SerialName("auto") AUTO,
    @SerialName("light") LIGHT,
    @SerialName("amoled") AMOLED,
}

@Serializable
enum class UiLang {
    @SerialName("ru") RU,
    @SerialName("en") EN,
}

@Serializable
enum class VoiceLangPref {
    @SerialName("it") IT,
    @SerialName("ru") RU,
}

@Serializable
data class Settings(
    val theme: ThemeMode = ThemeMode.AUTO,
    val uiLang: UiLang = if (Locale.getDefault().language == "ru") UiLang.RU else UiLang.EN,
    val haptics: Boolean = true,
    /** Показывать карточку «скачайте словарь для офлайна» на главном */
    val offlineHint: Boolean = true,
    /** Язык голосового ввода (запоминается между запусками) */
    val voiceLang: VoiceLangPref = VoiceLangPref.IT,
    /** «Карточки»: сколько переводов класть на оборот */
    val cardsMaxTr: Int = 3,
)

@Serializable
data class SavedWord(val word: String, val at: Long, val gloss: String? = null)

@Serializable
data class HistoryItem(val word: String, val at: Long)

data class Toast(val id: Long, val text: String)

/** Настройки, «Мои слова» и история. Хранятся в SharedPreferences как JSON. */
class AppStore(context: Context) {
    private val prefs = context.getSharedPreferences("lexa", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; encodeDefaults = true }

    var settings by mutableStateOf(Settings())
        private set
    var saved by mutableStateOf<List<SavedWord>>(emptyList())
        private set
    var history by mutableStateOf<List<HistoryItem>>(emptyList())
        private set
    var toast by mutableStateOf<Toast?>(null)
        private set

    init {
        settings = read(K_SETTINGS, Settings.serializer()) ?: Settings()
        saved = read(K_SAVED, ListSerializer(SavedWord.serializer())) ?: emptyList()
        history = read(K_HISTORY, ListSerializer(HistoryItem.serializer())) ?: emptyList()
    }

    private fun <T> read(key: String, ser: KSerializer<T>): T? {
        val raw = prefs.getString(key, null) ?: return null
        return runCatching { json.decodeFromString(ser, raw) }.getOrNull()
    }

    private fun <T> write(key: String, ser: KSerializer<T>, value: T) {
        prefs.edit().putString(key, json.encodeToString(ser, value)).apply()
    }

    fun update(transform: (Settings) -> Settings) {
        settings = transform(settings)
        write(K_SETTINGS, Settings.serializer(), settings)
    }

    fun isSaved(word: String): Boolean = saved.any { it.word == word }

    /** true — слово добавлено, false — удалено */
    fun toggleSaved(word: String, gloss: String? = null): Boolean {
        val exists = isSaved(word)
        saved = if (exists) saved.filter { it.word != word } else listOf(SavedWord(word, System.currentTimeMillis(), gloss)) + saved
        write(K_SAVED, ListSerializer(SavedWord.serializer()), saved)
        return !exists
    }

    fun pushHistory(word: String) {
        history = (listOf(HistoryItem(word, System.currentTimeMillis())) + history.filter { it.word != word }).take(300)
        write(K_HISTORY, ListSerializer(HistoryItem.serializer()), history)
    }

    fun clearHistory() {
        history = emptyList()
        write(K_HISTORY, ListSerializer(HistoryItem.serializer()), history)
    }

    fun showToast(text: String) {
        toast = Toast(System.nanoTime(), text)
    }

    fun dismissToast(id: Long) {
        if (toast?.id == id) toast = null
    }

    private companion object {
        const val K_SETTINGS = "datalaw_lexa_it.settings"
        const val K_SAVED = "datalaw_lexa_it.saved"
        const val K_HISTORY = "datalaw_lexa_it.history"
    }
}
