package app.datalaw.lexa.core

import java.text.Normalizer

/** Нормализация ключей — как norm_it()/norm_ru() в scripts/build_data.py. */
object Text {
    private val SPACES = Regex("[\\s\\u00A0\\u2000-\\u200A\\u202F\\u205F\\u3000]+")
    private val EDGE = Regex("^[\\s\"'“”«»(\\[]+|[\\s\"'“”«»)\\].,;:!?]+$")

    private fun base(input: String): String =
        Normalizer.normalize(input, Normalizer.Form.NFC)

    /** Ключ итальянского слова: регистр и знаки по краям убираются, диакритика остаётся (è ≠ e). */
    fun normalizeIt(input: String): String =
        base(input)
            .replace('‘', '\'')
            .replace('’', '\'')
            .replace(SPACES, " ")
            .trim()
            .replace(EDGE, "")
            .lowercase()

    /** Ключ русского термина: без ударений, ё → е. */
    fun normalizeRu(input: String): String =
        base(input)
            .replace("\u0301", "")
            .replace("\u0300", "")
            .replace(SPACES, " ")
            .trim()
            .replace(EDGE, "")
            .lowercase()
            .replace('ё', 'е')

    private val MARKS = Regex("\\p{Mn}+")

    /** Без диакритики: «perché» → «perche». Нужна для поиска, когда ударение не набрали. */
    fun fold(input: String): String =
        Normalizer.normalize(Normalizer.normalize(input, Normalizer.Form.NFD).replace(MARKS, ""), Normalizer.Form.NFC)

    private val VERB_END = Regex("(?:are|ere|ire)$")

    /**
     * Слово и его формы в предложении (для подсветки и выбора примеров).
     * Глагол: основа инфинитива + до 5 букв (mangiare → mangio, mangiato, mangiamo);
     * существительное/прилагательное на гласную: основа + гласная (casa → case, bello → bella);
     * остальное и словосочетания — только точное слово.
     */
    fun wordFormRegex(word: String): Regex {
        val w = word.trim().lowercase()
        val body = when {
            w.isEmpty() -> ""
            w.contains(' ') || w.contains('\'') || w.contains('-') -> Regex.escape(w)
            w.length >= 6 && VERB_END.containsMatchIn(w) -> Regex.escape(w.dropLast(3)) + "\\p{L}{0,5}"
            w.length >= 4 && w.last() in "aeio" -> Regex.escape(w.dropLast(1)) + "[aeio]"
            else -> Regex.escape(w)
        }
        return Regex("(?<!\\p{L})" + body + "(?!\\p{L})", RegexOption.IGNORE_CASE)
    }

    enum class Script { LATIN, CYRILLIC, OTHER }

    /** На каком языке введён запрос — по первой букве. */
    fun detectScript(text: String): Script {
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (Character.isLetter(cp)) {
                return when (Character.UnicodeScript.of(cp)) {
                    Character.UnicodeScript.CYRILLIC -> Script.CYRILLIC
                    Character.UnicodeScript.LATIN -> Script.LATIN
                    else -> Script.OTHER
                }
            }
            i += Character.charCount(cp)
        }
        return Script.OTHER
    }
}
