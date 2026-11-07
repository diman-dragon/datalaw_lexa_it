package app.datalaw.lexa.core

import kotlin.math.abs
import kotlin.math.min

/**
 * Поиск по отсортированному списку слов: префикс (бинарный поиск) и исправление опечаток
 * (расстояние Дамерау–Левенштейна). Всё в памяти, без сети.
 */
class WordIndex(
    /** ключи, по возрастанию */
    val words: Array<String>,
    /** оценка (Zipf × 10) */
    val score: IntArray,
    /** отображаемое написание, если отличается от ключа (русское «ё») */
    val label: Map<Int, String>,
) {
    val size: Int get() = words.size

    /** Ключи без диакритики (perché → perche) — для поиска, когда ударение не набрали. Считаются при первом обращении. */
    val folded: Array<String> by lazy { Array(words.size) { Text.fold(words[it]) } }

    fun labelAt(i: Int): String = label[i] ?: words[i]

    /** Первая позиция, где words[i] >= key. */
    fun lowerBound(key: String): Int {
        var lo = 0
        var hi = words.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (words[mid] < key) lo = mid + 1 else hi = mid
        }
        return lo
    }

    fun indexOf(key: String): Int {
        val i = lowerBound(key)
        return if (i < words.size && words[i] == key) i else -1
    }

    fun has(key: String): Boolean = indexOf(key) >= 0

    companion object {
        val EMPTY = WordIndex(emptyArray(), IntArray(0), emptyMap())

        /** Формат строки: ключ \t оценка [\t отображаемое написание]. */
        fun parse(text: String): WordIndex {
            val words = ArrayList<String>()
            val score = ArrayList<Int>()
            val label = HashMap<Int, String>()
            for (line in text.lineSequence()) {
                if (line.isEmpty()) continue
                val parts = line.split('\t')
                val key = parts[0]
                if (key.isEmpty()) continue
                parts.getOrNull(2)?.takeIf { it.isNotEmpty() }?.let { label[words.size] = it }
                words.add(key)
                score.add(parts.getOrNull(1)?.toIntOrNull() ?: 0)
            }
            return WordIndex(words.toTypedArray(), score.toIntArray(), label)
        }
    }
}

data class Hit(val key: String, val label: String, val score: Int)

object Search {
    private fun rank(ix: WordIndex): Comparator<Int> =
        compareByDescending<Int> { ix.score[it] }
            .thenBy { ix.words[it].length }
            .thenBy { ix.words[it] }

    /**
     * Слова, начинающиеся с prefix; частотные — выше, затем короткие.
     * Если точных совпадений мало, а запрос латиницей, добавляются слова, совпадающие без учёта
     * диакритики («perche» находит «perché»).
     */
    fun prefixSearch(ix: WordIndex, prefix: String, limit: Int = 8): List<Hit> {
        if (prefix.isEmpty()) return emptyList()
        val found = ArrayList<Int>()
        var i = ix.lowerBound(prefix)
        while (i < ix.size && found.size < 600) {
            if (!ix.words[i].startsWith(prefix)) break
            found.add(i)
            i++
        }
        found.sortWith(rank(ix))
        val result = ArrayList<Int>(found.take(limit))
        if (result.size < limit && prefix.length >= 2 && Text.detectScript(prefix) == Text.Script.LATIN) {
            val fp = Text.fold(prefix)
            val have = result.toHashSet()
            val extra = ArrayList<Int>()
            val fw = ix.folded
            for (j in 0 until ix.size) {
                if (j !in have && fw[j].startsWith(fp)) {
                    extra.add(j)
                    if (extra.size >= 600) break
                }
            }
            extra.sortWith(rank(ix))
            result.addAll(extra.take(limit - result.size))
        }
        return result.map { Hit(ix.words[it], ix.labelAt(it), ix.score[it]) }
    }

    /** Расстояние Дамерау–Левенштейна (OSA) с досрочным выходом, если оно больше max. */
    fun editDistance(a: String, b: String, max: Int): Int {
        if (a == b) return 0
        val la = a.length
        val lb = b.length
        if (abs(la - lb) > max) return max + 1
        var prev2 = IntArray(lb + 1)
        var prev = IntArray(lb + 1) { it }
        for (i in 1..la) {
            val cur = IntArray(lb + 1)
            cur[0] = i
            var rowMin = i
            for (j in 1..lb) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var v = min(min(prev[j] + 1, cur[j - 1] + 1), prev[j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    v = min(v, prev2[j - 2] + 1)
                }
                cur[j] = v
                if (v < rowMin) rowMin = v
            }
            if (rowMin > max) return max + 1
            prev2 = prev
            prev = cur
        }
        return prev[lb]
    }

    /** «Возможно, вы искали…»: близкие по написанию слова словаря. */
    fun suggestSimilar(ix: WordIndex, word: String, limit: Int = 6): List<Hit> {
        if (word.length < 2) return emptyList()
        val maxDist = if (word.length <= 4) 1 else 2
        val found = ArrayList<Pair<Int, Int>>() // (индекс, расстояние)
        for (i in 0 until ix.size) {
            val w = ix.words[i]
            if (abs(w.length - word.length) > maxDist) continue
            // первая буква почти всегда верна — отсекаем большинство кандидатов дёшево
            if (word.length > 3 && w[0] != word[0] && w.getOrNull(1) != word[0]) continue
            val d = editDistance(word, w, maxDist)
            if (d <= maxDist) found.add(i to d)
        }
        found.sortWith(compareBy<Pair<Int, Int>> { it.second }.thenByDescending { ix.score[it.first] })
        return found.take(limit).map { (i, _) -> Hit(ix.words[i], ix.labelAt(i), ix.score[i]) }
    }
}

