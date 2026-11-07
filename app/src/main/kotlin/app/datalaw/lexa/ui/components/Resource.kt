package app.datalaw.lexa.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.datalaw.lexa.LexaApp
import app.datalaw.lexa.platform.SpeechException
import app.datalaw.lexa.ui.i18n.Str
import app.datalaw.lexa.ui.i18n.t
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

sealed interface Res<out T> {
    data object Loading : Res<Nothing>
    data class Ready<T>(val data: T) : Res<T>
    data class Failed(val error: Throwable) : Res<Nothing>
}

class Resource<T>(val state: Res<T>, val reload: () -> Unit) {
    val data: T? get() = (state as? Res.Ready<T>)?.data
    val isLoading: Boolean get() = state is Res.Loading
    val isFailed: Boolean get() = state is Res.Failed
    val error: Throwable? get() = (state as? Res.Failed)?.error
}

/** Результаты за сессию: возврат на экран не повторяет чтение данных. */
private class Box(val value: Any?)

private val memo = object : LinkedHashMap<Any, Box>(64, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Any, Box>?): Boolean = size > 300
}

/**
 * Асинхронная загрузка по ключу с сессионным мемо (аналог useResource из веб-версии).
 * key == null — ничего не грузим (ленивые вкладки).
 */
@Composable
@Suppress("UNCHECKED_CAST")
fun <T> rememberResource(key: Any?, loader: suspend () -> T): Resource<T> {
    var tick by remember(key) { mutableIntStateOf(0) }
    var state by remember(key) {
        val cached = if (key != null) memo[key] else null
        mutableStateOf<Res<T>>(if (cached != null) Res.Ready(cached.value as T) else Res.Loading)
    }
    LaunchedEffect(key, tick) {
        if (key == null) return@LaunchedEffect
        if (tick == 0) {
            memo[key]?.let {
                state = Res.Ready(it.value as T)
                return@LaunchedEffect
            }
        }
        state = Res.Loading
        try {
            val data = loader()
            memo[key] = Box(data)
            state = Res.Ready(data)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            state = Res.Failed(e)
        }
    }
    return Resource(state) {
        if (key != null) memo.remove(key)
        tick++
    }
}

/** Индекс итальянских слов: общий для подсказок и ссылок-слов. */
@Composable
fun rememberItIndex() = rememberResource("index:it") { LexaApp.instance.dictionary.itIndex() }

@Composable
fun rememberRuIndex() = rememberResource("index:ru") { LexaApp.instance.dictionary.ruIndex() }

/** Озвучка с понятным сообщением об ошибке (а не «синтез не установлен» на любой сбой). */
suspend fun sayNow(text: String, locale: String) {
    val store = LexaApp.instance.store
    try {
        LexaApp.instance.speaker.speak(text, locale)
    } catch (e: CancellationException) {
        throw e
    } catch (e: SpeechException) {
        store.showToast(
            t(
                when (e.kind) {
                    SpeechException.Kind.LANG -> Str.toastNoVoice
                    SpeechException.Kind.ENGINE -> Str.toastNoEngine
                    SpeechException.Kind.FAILED -> Str.toastSpeechFailed
                },
            ),
        )
    } catch (e: Exception) {
        store.showToast(t(Str.toastSpeechFailed))
    }
}

@Composable
fun rememberSay(): (String, String) -> Unit {
    val scope = rememberCoroutineScope()
    return remember {
        { text: String, locale: String ->
            scope.launch { sayNow(text, locale) }
            Unit
        }
    }
}
