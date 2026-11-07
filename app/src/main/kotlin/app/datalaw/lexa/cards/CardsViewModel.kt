package app.datalaw.lexa.cards

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.datalaw.lexa.LexaApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Состояние экрана «Карточки»: очередь слов, их перевод, существующая колода и экспорт.
 * Живёт в ViewModel — перевод не прерывается поворотом экрана или переходом на другую вкладку.
 */
class CardsViewModel : ViewModel() {
    private val app = LexaApp.instance
    private val generator = CardGenerator(app.dictionary)

    val items = mutableStateListOf<CardItem>()
    var deckName by mutableStateOf("")
    var base by mutableStateOf<BaseDeck?>(null)
        private set
    var running by mutableStateOf(false)
        private set
    var done by mutableIntStateOf(0)
        private set
    var total by mutableIntStateOf(0)
        private set

    private var job: Job? = null
    private var nextId = 1L

    /* ───────── Список слов ───────── */

    /** Добавляет слова (без повторов того, что уже в списке). Возвращает число добавленных. */
    fun addWords(words: List<String>): Int {
        val have = items.map { it.input.lowercase() }.toHashSet()
        var added = 0
        for (w in words) {
            val tidy = w.trim()
            if (tidy.isEmpty() || !have.add(tidy.lowercase())) continue
            items.add(CardItem(nextId++, tidy))
            added++
        }
        return added
    }

    fun remove(id: Long) {
        items.removeAll { it.id == id }
        recomputeDuplicates()
    }

    fun clear() {
        job?.cancel()
        running = false
        items.clear()
    }

    private fun update(id: Long, transform: (CardItem) -> CardItem) {
        val i = items.indexOfFirst { it.id == id }
        if (i >= 0) items[i] = transform(items[i])
    }

    /* ───────── Перевод ───────── */

    /** Переводит все ожидающие слова и те, что не удалось прочитать в прошлый раз. */
    fun translate() {
        if (running) return
        val todo = items.filter { it.status == CardStatus.QUEUED || it.status == CardStatus.ERROR }.map { it.id }
        if (todo.isEmpty()) return
        val s = app.store.settings
        val opts = GenOptions(maxTranslations = s.cardsMaxTr)
        running = true
        done = 0
        total = todo.size
        job = viewModelScope.launch {
            try {
                val gate = Semaphore(4)
                coroutineScope {
                    todo.map { id ->
                        async {
                            gate.withPermit { process(id, opts) }
                            done += 1
                        }
                    }.awaitAll()
                }
            } finally {
                running = false
                recomputeDuplicates()
            }
        }
    }

    private suspend fun process(id: Long, opts: GenOptions) {
        val input = items.firstOrNull { it.id == id }?.input ?: return
        update(id) { it.copy(status = CardStatus.WORKING) }
        val result = try {
            generator.generate(input, opts)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            update(id) { it.copy(status = CardStatus.ERROR) }
            return
        }
        update(id) {
            val first = result.alternatives.firstOrNull()
            it.copy(
                status = result.status,
                front = result.front,
                back = result.back,
                example = first?.ita.orEmpty(),
                exampleTranslation = first?.ru.orEmpty(),
                alternatives = result.alternatives,
                altIndex = 0,
                via = result.via,
                suggestions = result.suggestions,
                suggestionsAreTranslations = result.suggestionsAreTranslations,
            )
        }
    }

    /** Заменяет слово (например, выбрано исправление из подсказки) и переводит заново. */
    fun replaceWord(id: Long, word: String) {
        update(id) { CardItem(it.id, word.trim()) }
        translate()
    }

    /* ───────── Правка карточки ───────── */

    fun nextExample(id: Long) = update(id) {
        if (it.alternatives.size < 2) return@update it
        val n = (it.altIndex + 1) % it.alternatives.size
        val a = it.alternatives[n]
        it.copy(altIndex = n, example = a.ita, exampleTranslation = a.ru)
    }

    fun edit(id: Long, front: String, back: String, example: String, exampleTranslation: String) {
        update(id) {
            it.copy(
                front = front.trim(),
                back = back.trim(),
                example = example.trim(),
                exampleTranslation = exampleTranslation.trim(),
                status = if (back.isBlank() || front.isBlank()) CardStatus.NO_TRANSLATION else CardStatus.READY,
            )
        }
        recomputeDuplicates()
    }

    /* ───────── Существующая колода ───────── */

    /** false — файл не похож на колоду flashcards-deck. */
    fun loadBase(text: String): Boolean {
        val parsed = DeckIo.parse(text) ?: return false
        base = parsed
        if (deckName.isBlank()) deckName = parsed.name
        recomputeDuplicates()
        return true
    }

    fun detachBase() {
        base = null
        recomputeDuplicates()
    }

    /**
     * Карточка уже есть в колоде или выше в списке — помечаем как дубликат (данные остаются:
     * если колоду отвязать, карточка снова станет готовой).
     */
    fun recomputeDuplicates() {
        val seen = HashSet<String>(base?.keys ?: emptySet())
        for (i in items.indices) {
            val it = items[i]
            if (!it.hasData) continue
            val key = CardLogic.dedupKey(it.front)
            val next = if (key in seen) CardStatus.DUPLICATE else CardStatus.READY.also { seen.add(key) }
            if (next != it.status) items[i] = it.copy(status = next)
        }
    }

    /* ───────── Экспорт ───────── */

    val readyCount: Int get() = items.count { it.exportable }
    val attentionCount: Int get() = items.count { it.needsAttention }
    val queuedCount: Int get() = items.count { it.status == CardStatus.QUEUED || it.status == CardStatus.ERROR }

    fun exportName(default: String): String = deckName.trim().ifEmpty { base?.name?.ifEmpty { null } ?: default }

    /** JSON колоды: только готовые карточки; с существующей колодой — дописывает к ней. */
    fun exportJson(defaultName: String): String {
        val cards = items.filter { it.exportable }.map { it.toCard() }
        val name = exportName(defaultName)
        return base?.let { DeckIo.merge(it, name, cards) } ?: DeckIo.create(name, cards)
    }
}
