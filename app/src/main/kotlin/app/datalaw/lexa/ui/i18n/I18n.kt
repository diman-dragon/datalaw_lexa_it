package app.datalaw.lexa.ui.i18n

import app.datalaw.lexa.LexaApp
import app.datalaw.lexa.store.UiLang

private val isRu: Boolean get() = LexaApp.instance.store.settings.uiLang == UiLang.RU

private val GLUE_SHORT = Regex("(?<![\\p{L}\\p{N}])(\\p{L}{1,3}|\\p{N}+) (?=\\S)")

/**
 * Типографика подписей: короткие слова («в», «и», «на», «to») и числа не отрываются от следующего слова,
 * тире, точка-разделитель и стрелка не оказываются в начале строки. Только неразрывные пробелы — текст не меняется.
 */
fun typo(text: String): String =
    text.replace(GLUE_SHORT, "$1\u00A0")
        .replace(" —", "\u00A0—")
        .replace(" ·", "\u00A0·")
        .replace(" →", "\u00A0→")

/** Строка интерфейса на выбранном языке. Читает состояние настроек — Compose перерисует экран при смене языка. */
fun t(key: Str, vararg vars: Pair<String, Any>): String {
    var s = if (isRu) key.ru else key.en
    for ((k, v) in vars) s = s.replace("{$k}", v.toString())
    return typo(s)
}

/** «1 слово · 2 слова · 5 слов» */
fun wordsCount(n: Int): String {
    if (!isRu) return "$n ${if (n == 1) "word" else "words"}"
    val m10 = n % 10
    val m100 = n % 100
    val form = when {
        m10 == 1 && m100 != 11 -> "слово"
        m10 in 2..4 && (m100 < 10 || m100 >= 20) -> "слова"
        else -> "слов"
    }
    return "$n $form"
}

private val POS_RU = mapOf(
    "noun" to "существительное", "verb" to "глагол", "adjective" to "прилагательное", "adverb" to "наречие",
    "pronoun" to "местоимение", "preposition" to "предлог", "conjunction" to "союз", "interjection" to "междометие",
    "determiner" to "определитель", "numeral" to "числительное", "article" to "артикль", "particle" to "частица",
    "phrase" to "фраза", "proverb" to "пословица", "idiom" to "идиома", "prefix" to "приставка", "suffix" to "суффикс",
    "contraction" to "сокращение", "proper noun" to "имя собственное", "abbreviation" to "аббревиатура",
    "initialism" to "инициализм", "acronym" to "акроним", "prepositional phrase" to "предложный оборот",
    "verb phrase" to "глагольная фраза", "adverbial phrase" to "наречный оборот",
    "participle" to "причастие", "gerund" to "деепричастие", "root" to "корень", "affix" to "аффикс",
    "postposition" to "послелог", "other" to "другое",
)

fun posLabel(pos: String): String {
    val key = pos.lowercase()
    return if (isRu) POS_RU[key] ?: key else key
}

private val TAGS_RU = mapOf(
    "informal" to "разг.", "slang" to "сленг", "colloquial" to "разг.", "formal" to "офиц.", "literary" to "книжн.",
    "vulgar" to "вульг.", "offensive" to "оскорб.", "derogatory" to "пренебр.", "archaic" to "устар.",
    "obsolete" to "устар.", "dated" to "устар.", "rare" to "редк.", "figuratively" to "перен.", "humorous" to "шутл.",
    "poetic" to "поэт.", "technical" to "техн.", "transitive" to "перех.", "intransitive" to "неперех.",
    "countable" to "счётн.", "uncountable" to "несчётн.", "British" to "брит.", "US" to "США", "Australia" to "австрал.",
)

fun tagLabel(tag: String): String = if (isRu) TAGS_RU[tag] ?: tag else tag

/** Грамматические пометки русских переводов: m, f, n, pl, pf, impf … */
private val MARK_RU = mapOf(
    "m" to "м.", "f" to "ж.", "n" to "ср.", "pl" to "мн.", "pf" to "сов.", "impf" to "несов.",
    "anim" to "одуш.", "inan" to "неодуш.",
)

fun markLabel(marks: String): String = marks.split(' ').joinToString(" ") { MARK_RU[it] ?: it }
