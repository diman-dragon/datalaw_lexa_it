package app.datalaw.lexa.cards

import app.datalaw.lexa.core.Text
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Разбор списков слов: из поля ввода, из .txt/.csv/.tsv и из .json (в т.ч. из самой колоды). */
object WordListParser {
    const val MAX_WORDS = 2000
    private const val MAX_LEN = 120

    private val SPLIT = Regex("[\\r\\n;]+")
    private val LIST_MARK = Regex("^\\s*(?:[-*•–—]|\\d+[.)])\\s+")
    private val HEADERS = setOf("word", "words", "front", "italian", "italiano", "term", "it", "ita", "слово", "итальянский")
    private val JSON_KEYS = listOf("front", "word", "it", "italian", "italiano", "term")

    private fun clean(line: String): String? {
        var s = line.replace("\uFEFF", "").trim()
        if (s.isEmpty() || s.startsWith("#") || s.startsWith("//")) return null
        s = s.replace(LIST_MARK, "").trim()
        if (s.length >= 2 && s.first() == '"' && s.last() == '"') s = s.substring(1, s.length - 1).trim()
        return s.takeIf { it.isNotEmpty() && it.length <= MAX_LEN }
    }

    private fun unique(words: Sequence<String>): List<String> {
        val seen = HashSet<String>()
        val out = ArrayList<String>()
        for (w in words) {
            val tidy = Text.tidy(w)
            if (tidy.isNotEmpty() && seen.add(Text.normalizeIt(tidy).ifEmpty { tidy.lowercase() })) out.add(tidy)
            if (out.size >= MAX_WORDS) break
        }
        return out
    }

    /** Поле ввода и .txt: слова — с новой строки или через «;». */
    fun parseText(text: String): List<String> =
        unique(text.split(SPLIT).asSequence().mapNotNull(::clean))

    /** Первая колонка строки CSV/TSV (учитывает кавычки). */
    private fun firstColumn(line: String): String {
        val s = line.trimStart('\uFEFF')
        if (s.startsWith("\"")) {
            val end = s.indexOf('"', 1)
            if (end > 0) return s.substring(1, end)
        }
        val cut = s.indexOfFirst { it == '\t' || it == ',' || it == ';' }
        return if (cut >= 0) s.substring(0, cut) else s
    }

    private fun parseTable(text: String): List<String> {
        val rows = text.lineSequence().mapNotNull { clean(it) }.map { firstColumn(it).trim() }.filter { it.isNotEmpty() }.toList()
        val body = if ((rows.firstOrNull()?.lowercase() ?: "") in HEADERS) rows.drop(1) else rows
        return unique(body.asSequence())
    }

    private fun fromJson(e: JsonElement, out: MutableList<String>) {
        when (e) {
            is JsonPrimitive -> e.contentOrNull?.let(::clean)?.let(out::add)
            is JsonArray -> e.forEach { fromJson(it, out) }
            is JsonObject -> {
                val key = JSON_KEYS.firstOrNull { e[it] is JsonPrimitive }
                if (key != null) {
                    (e[key] as JsonPrimitive).contentOrNull?.let(::clean)?.let(out::add)
                } else {
                    // колода: {"cards": [...]} или {"words": [...]}
                    (e["cards"] ?: e["words"])?.let { fromJson(it, out) }
                }
            }
        }
    }

    private fun parseJson(text: String): List<String>? {
        val el = runCatching { Json.parseToJsonElement(text.trimStart('\uFEFF')) }.getOrNull() ?: return null
        val out = ArrayList<String>()
        fromJson(el, out)
        return unique(out.asSequence())
    }

    /** Разбор файла по имени и содержимому; формат определяется по расширению и по виду текста. */
    fun parseFile(fileName: String?, text: String): List<String> {
        val name = fileName?.lowercase().orEmpty()
        val head = text.trimStart('\uFEFF', ' ', '\n', '\r', '\t')
        if (name.endsWith(".json") || head.startsWith("[") || head.startsWith("{")) {
            parseJson(text)?.let { return it }
        }
        if (name.endsWith(".csv") || name.endsWith(".tsv")) return parseTable(text)
        return parseText(text)
    }
}
