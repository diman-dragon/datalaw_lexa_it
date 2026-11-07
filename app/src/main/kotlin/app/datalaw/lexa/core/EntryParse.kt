package app.datalaw.lexa.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.booleanOrNull

/** Разбор JSON словаря вручную: формат компактный (короткие ключи, TrTerm — строка или объект). */
object EntryParse {
    private fun JsonElement?.str(): String? =
        (this as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    private fun JsonElement?.strList(): List<String> =
        (this as? JsonArray)?.mapNotNull { it.str() } ?: emptyList()

    private fun JsonElement?.int(): Int? = (this as? JsonPrimitive)?.intOrNull

    private fun JsonElement?.obj(): JsonObject? = this as? JsonObject

    private fun sense(o: JsonObject): Sense? {
        val d = o["d"].str() ?: return null
        return Sense(d, o["e"].strList(), o["x"].strList(), o["y"].strList(), o["a"].strList(), o["g"].strList())
    }

    private fun term(e: JsonElement): TrTerm? = when (e) {
        is JsonPrimitive -> e.str()?.let { TrTerm(it) }
        is JsonObject -> e["w"].str()?.let { TrTerm(it, e["r"].str(), e["x"].str()) }
        else -> null
    }

    fun entry(o: JsonObject): Entry? {
        val w = o["w"].str() ?: return null
        val pron = (o["p"] as? JsonArray)?.mapNotNull { p ->
            val a = p as? JsonArray ?: return@mapNotNull null
            val ipa = a.getOrNull(0).str() ?: return@mapNotNull null
            Pron(ipa, a.getOrNull(1).str() ?: "other")
        } ?: emptyList()
        val meanings = (o["m"] as? JsonArray)?.mapNotNull { m ->
            val mo = m.obj() ?: return@mapNotNull null
            val t = mo["t"].str() ?: return@mapNotNull null
            Meaning(t, (mo["s"] as? JsonArray)?.mapNotNull { it.obj()?.let(::sense) } ?: emptyList())
        } ?: emptyList()
        val groups = (o["tr"] as? JsonArray)?.mapNotNull { g ->
            val go = g.obj() ?: return@mapNotNull null
            val terms = (go["t"] as? JsonArray)?.mapNotNull(::term) ?: emptyList()
            if (terms.isEmpty()) null else TrGroup(go["pos"].str(), go["g"].str(), terms)
        } ?: emptyList()
        val rel = HashMap<RelationKey, List<String>>()
        o["rel"].obj()?.forEach { (k, v) ->
            val key = RelationKey.of(k) ?: return@forEach
            val list = v.strList()
            if (list.isNotEmpty()) rel[key] = list
        }
        return Entry(
            w = w,
            z = o["z"].int(),
            p = pron,
            m = meanings,
            tr = groups,
            rel = rel,
            et = o["et"].str(),
            r = o["r"].strList(),
            rg = o["rg"].str(),
        )
    }

    fun sentences(e: JsonElement?): List<SentencePair> =
        (e as? JsonArray)?.mapNotNull { p ->
            val a = p as? JsonArray ?: return@mapNotNull null
            val ita = a.getOrNull(0).str() ?: return@mapNotNull null
            val ru = a.getOrNull(1).str() ?: return@mapNotNull null
            SentencePair(ita, ru)
        } ?: emptyList()

    fun reverse(e: JsonElement?): List<String> = e.strList()

    fun meta(o: JsonObject): Meta = Meta(
        schema = o["schema"].int() ?: 1,
        built = o["built"].str() ?: "",
        words = o["words"].int() ?: 0,
        forms = o["forms"].int() ?: 0,
        translated = o["translated"].int() ?: 0,
        ruTerms = o["ruTerms"].int() ?: 0,
        sentences = o["sentences"].int() ?: 0,
        frequency = (o["frequency"] as? JsonPrimitive)?.booleanOrNull ?: false,
    )
}
