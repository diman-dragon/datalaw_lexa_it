package app.datalaw.lexa.cards

import app.datalaw.lexa.core.Entry
import app.datalaw.lexa.core.EntryUtil
import app.datalaw.lexa.core.Search
import app.datalaw.lexa.core.SentencePair
import app.datalaw.lexa.core.Text
import app.datalaw.lexa.data.DictionarySource

data class GenOptions(
    /** сколько переводов класть на оборот */
    val maxTranslations: Int = 3,
)

/** Результат разбора одного слова. */
data class GenResult(
    val status: CardStatus,
    val front: String,
    val back: String = "",
    val alternatives: List<ExamplePair> = emptyList(),
    val via: String? = null,
    val suggestions: List<String> = emptyList(),
    val suggestionsAreTranslations: Boolean = false,
)

/** Чистая логика сборки карточки — отдельно от Android, чтобы её можно было тестировать. */
object CardLogic {
    /** Артикль перед словом в списках: «la casa», «gli amici»; с апострофом пишется слитно: «l'amico». */
    private val ARTICLE = Regex("^(?:(?:il|lo|la|i|gli|le|un|uno|una)\\s+|(?:l|un)'\\s*)", RegexOption.IGNORE_CASE)

    private fun withoutArticle(norm: String): String = norm.replace(ARTICLE, "").trim()

    /** Ключи для поиска в словаре, от самого точного: «la casa» → [«la casa», «casa»]. */
    fun candidateKeys(input: String): List<String> {
        val norm = Text.normalizeIt(input)
        val out = LinkedHashSet<String>()
        if (norm.isNotEmpty()) out.add(norm)
        out.add(withoutArticle(norm))
        return out.filter { it.isNotEmpty() }
    }

    /** Ключ для сравнения карточек: «La casa.» и «casa» — одно и то же. */
    fun dedupKey(front: String): String = withoutArticle(Text.normalizeIt(front))

    /** Слово встречается в предложении точно, как его ввели. */
    fun containsExact(sentence: String, word: String): Boolean {
        if (word.isBlank()) return false
        return Regex("(?<!\\p{L})" + Regex.escape(word.trim()) + "(?!\\p{L})", RegexOption.IGNORE_CASE).containsMatchIn(sentence)
    }

    /** Слово встречается в предложении (в т.ч. в другой форме), как в подсветке примеров. */
    fun containsWord(sentence: String, word: String): Boolean {
        if (word.isBlank()) return false
        return Text.wordFormRegex(word).containsMatchIn(sentence)
    }

    /**
     * Примеры: сначала пары Tatoeba (с переводом), затем примеры из словаря (с переводом, если он есть).
     * Внутри Tatoeba — предложения с той формой слова, что ввёл пользователь, и средней длины.
     */
    fun chooseExamples(
        pairs: List<SentencePair>,
        dictionaryExamples: List<ExamplePair>,
        surfaceKey: String,
        limit: Int = 6,
    ): List<ExamplePair> {
        fun penalty(len: Int): Int = when {
            len < 20 -> 20 - len
            len > 80 -> len - 80
            else -> 0
        }
        fun formRank(sentence: String): Int = when {
            containsExact(sentence, surfaceKey) -> 0
            containsWord(sentence, surfaceKey) -> 500
            else -> 1000
        }
        val ranked = pairs
            .filter { it.ita.isNotBlank() && it.ru.isNotBlank() }
            .sortedBy { formRank(it.ita) + penalty(it.ita.length) * 2 + it.ita.length / 10 }
            .take(limit)
            .map { ExamplePair(it.ita.trim(), it.ru.trim()) }
        val fromDict = dictionaryExamples
            .map { ExamplePair(it.ita.trim(), it.ru.trim()) }
            .filter { it.ita.isNotEmpty() && it.ita.length <= 140 }
            .distinctBy { it.ita }
            .sortedWith(compareBy<ExamplePair> { it.ru.isEmpty() }.thenBy { penalty(it.ita.length) })
        return (ranked + fromDict).distinctBy { it.ita }.take(limit + 2)
    }

    /** Примеры из статьи словаря (с русским переводом, если он есть). */
    fun dictionaryExamples(entry: Entry): List<ExamplePair> =
        entry.m.flatMap { m ->
            m.s.flatMap { s -> s.e.mapIndexed { i, text -> ExamplePair(text, s.x.getOrNull(i).orEmpty()) } }
        }
}

class CardGenerator(private val dict: DictionarySource) {

    suspend fun generate(rawInput: String, opts: GenOptions = GenOptions()): GenResult {
        val input = Text.tidy(rawInput)
        if (input.isEmpty()) return GenResult(CardStatus.NOT_FOUND, input)

        // Русский ввод: подсказываем итальянские эквиваленты из обратного индекса
        if (Text.detectScript(input) == Text.Script.CYRILLIC) {
            val ita = dict.getReverse(Text.normalizeRu(input)).take(8)
            return GenResult(CardStatus.NOT_FOUND, input, suggestions = ita, suggestionsAreTranslations = true)
        }

        val keys = CardLogic.candidateKeys(input)
        var entry: Entry? = null
        var usedKey = ""
        for (k in keys) {
            val e = dict.getEntry(k)
            if (e != null) {
                entry = e
                usedKey = k
                break
            }
        }
        if (entry == null) {
            val ix = dict.itIndex()
            val word = keys.lastOrNull()
            val similar = if (word == null) emptyList() else {
                // сначала слова, совпавшие без диакритики («caffe» → «caffè»), затем близкие по написанию
                val folded = Search.prefixSearch(ix, word, 3).filter { Text.fold(it.key) == Text.fold(word) }
                (folded + Search.suggestSimilar(ix, word, 6)).map { it.label }.distinct().take(6)
            }
            return GenResult(CardStatus.NOT_FOUND, input, suggestions = similar)
        }

        // Форма слова («mangio») → статья леммы («mangiare»)
        var lemmaKey = usedKey
        var via: String? = null
        if (entry.isForm) {
            var lemma: Entry? = null
            for (r in entry.r) {
                val e = dict.getEntry(r)
                if (e != null && !e.isForm) {
                    lemma = e
                    lemmaKey = r
                    via = r
                    break
                }
            }
            if (lemma == null) return GenResult(CardStatus.NO_TRANSLATION, input, via = entry.r.firstOrNull())
            entry = lemma
        }

        val back = EntryUtil
            .topTranslations(entry.tr, opts.maxTranslations.coerceIn(1, 6))
            .joinToString(", ")

        val pairs = dict.getSentences(lemmaKey)
        val examples = CardLogic.chooseExamples(pairs, CardLogic.dictionaryExamples(entry), keys.last())

        return GenResult(
            status = if (back.isBlank()) CardStatus.NO_TRANSLATION else CardStatus.READY,
            front = input,
            back = back,
            alternatives = examples,
            via = via,
        )
    }
}
