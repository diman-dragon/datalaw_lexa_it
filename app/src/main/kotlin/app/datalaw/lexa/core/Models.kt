package app.datalaw.lexa.core

/** Формат офлайн-данных (см. scripts/build_data.py). */

data class Sense(
    /** толкование (по-русски) */
    val d: String,
    /** примеры (итальянские предложения) */
    val e: List<String> = emptyList(),
    /** русские переводы примеров, по порядку с e; пустая строка — перевода нет */
    val x: List<String> = emptyList(),
    /** синонимы значения */
    val y: List<String> = emptyList(),
    /** антонимы значения */
    val a: List<String> = emptyList(),
    /** пометки: informal, slang, archaic … */
    val g: List<String> = emptyList(),
)

data class Meaning(
    /** часть речи */
    val t: String,
    val s: List<Sense>,
)

/** Русский перевод: слово, пояснение (r, например «о погоде»), грамматические пометки (x). */
data class TrTerm(val w: String, val r: String? = null, val x: String? = null)

data class TrGroup(
    val pos: String? = null,
    /** номер значения внутри части речи */
    val g: String? = null,
    val t: List<TrTerm>,
)

/** Транскрипция IPA. accent оставлен ради формата данных: у итальянского он один («other»). */
data class Pron(val ipa: String, val accent: String)

enum class RelationKey(val id: String) {
    SYN("syn"), ANT("ant"), HYPER("hyper"), HYPO("hypo"), REL("rel"), DER("der"), COORD("coord");

    companion object {
        fun of(id: String): RelationKey? = entries.firstOrNull { it.id == id }
    }
}

data class Entry(
    /** написание */
    val w: String,
    /** Zipf-частотность × 10 */
    val z: Int? = null,
    /** транскрипции */
    val p: List<Pron> = emptyList(),
    val m: List<Meaning> = emptyList(),
    val tr: List<TrGroup> = emptyList(),
    val rel: Map<RelationKey, List<String>> = emptyMap(),
    /** этимология */
    val et: String? = null,
    /** перенаправление: это форма другого слова */
    val r: List<String> = emptyList(),
    /** пояснение формы («1-е лицо ед. ч. глагола mangiare») */
    val rg: String? = null,
) {
    val isForm: Boolean get() = r.isNotEmpty()
}

data class Meta(
    val schema: Int,
    val built: String,
    val words: Int,
    val forms: Int,
    val translated: Int,
    val ruTerms: Int,
    val sentences: Int,
    val frequency: Boolean,
)

/** Предложение Tatoeba: итальянское + русское. */
data class SentencePair(val ita: String, val ru: String)
