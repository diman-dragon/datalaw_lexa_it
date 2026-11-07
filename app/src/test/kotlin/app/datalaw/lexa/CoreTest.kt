package app.datalaw.lexa

import app.datalaw.lexa.core.EntryParse
import app.datalaw.lexa.core.EntryUtil
import app.datalaw.lexa.core.Hash
import app.datalaw.lexa.core.Search
import app.datalaw.lexa.core.Text
import app.datalaw.lexa.core.WordIndex
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CoreTest {
    /** Значения посчитаны scripts/build_data.py — хэши обязаны совпадать, иначе приложение не найдёт файлы. */
    @Test
    fun fnv1aMatchesPythonBuilder() {
        assertEquals(4153443133L, Hash.fnv1a("ciao"))
        assertEquals(2670018301L, Hash.fnv1a("casa"))
        assertEquals(970764874L, Hash.fnv1a("caffè"))
        assertEquals(579243040L, Hash.fnv1a("бег"))
    }

    @Test
    fun shardPaths() {
        assertEquals("e/253.json", Hash.shardPath("e", "casa"))
        assertEquals("e/074.json", Hash.shardPath("e", "caffè"))
        assertEquals("e/283.json", Hash.shardPath("e", "mangiare"))
        assertEquals("r/032.json", Hash.shardPath("r", "бег"))
        assertEquals("s/027.json", Hash.shardPath("s", "mangiare"))
    }

    @Test
    fun normalization() {
        assertEquals("ciao", Text.normalizeIt("  “Ciao!” "))
        assertEquals("cos'è", Text.normalizeIt("Cos’è"))
        assertEquals("perché", Text.normalizeIt("Perché"))
        assertEquals("a presto", Text.normalizeIt("A   presto."))
        assertEquals("perche", Text.fold("perché"))
        assertEquals("caffe", Text.fold("caffè"))
        assertEquals("елка", Text.normalizeRu("Ёлка"))
        assertEquals("замок", Text.normalizeRu("за\u0301мок"))
        assertEquals(Text.Script.CYRILLIC, Text.detectScript("12 привет"))
        assertEquals(Text.Script.LATIN, Text.detectScript("ciao"))
        assertEquals(Text.Script.OTHER, Text.detectScript("123"))
    }

    private val index = WordIndex.parse("bello\t50\nbianco\t46\ncaffè\t44\ncasa\t54\nciao\t51\nmangiare\t46\nperché\t58\n")

    @Test
    fun prefixAndSimilar() {
        assertEquals(listOf("casa"), Search.prefixSearch(index, "cas").map { it.key })
        assertEquals(listOf("bello", "bianco"), Search.prefixSearch(index, "b").map { it.key })
        assertTrue(index.has("casa"))
        assertEquals("ciao", Search.suggestSimilar(index, "ciap").first().key)
        assertEquals("casa", Search.suggestSimilar(index, "csaa").first().key) // перестановка соседних букв
        assertEquals(1, Search.editDistance("ab", "ba", 2))
    }

    @Test
    fun searchIgnoresMissingAccents() {
        assertEquals(listOf("perché"), Search.prefixSearch(index, "perche").map { it.key })
        assertEquals(listOf("caffè"), Search.prefixSearch(index, "caffe").map { it.key })
        assertEquals("perché", Search.suggestSimilar(index, "perche").first().key)
    }

    @Test
    fun wordFormsAreHighlighted() {
        fun hit(sentence: String, word: String) = Text.wordFormRegex(word).containsMatchIn(sentence)
        assertTrue(hit("Le case sono vecchie.", "casa"))
        assertTrue(hit("Lui mangia sempre.", "mangiare"))
        assertTrue(hit("Abbiamo mangiato tutto.", "mangiare"))
        assertTrue(hit("Che bella giornata!", "bello"))
        assertTrue(!hit("Un casino enorme.", "casa"))
        assertTrue(hit("Prendo un caffè.", "caffè"))
    }

    @Test
    fun parsesEntryWithMixedTranslationTerms() {
        val json = """{"w":"caffè","z":44,"m":[{"t":"noun","s":[{"d":"кафе, кофейня","e":["Andiamo al caffè."],"x":["Пойдём в кафе."]}]}],
            "tr":[{"pos":"noun","g":"1","t":[{"w":"кафе","x":"n"},"кофейня"]}],"rel":{"syn":["x"]}}"""
        val e = EntryParse.entry(Json.parseToJsonElement(json) as JsonObject)
        assertNotNull(e)
        assertEquals("кафе", e!!.tr[0].t[0].w)
        assertEquals("n", e.tr[0].t[0].x)
        assertEquals(listOf("Пойдём в кафе."), e.m[0].s[0].x)
        assertEquals("кофейня", e.tr[0].t[1].w)
        assertEquals(listOf("кафе", "кофейня"), EntryUtil.topTranslations(e.tr, 4))
    }

    @Test
    fun topTranslationsTakesOnePerSenseFirst() {
        val json = """{"w":"banca","tr":[{"pos":"noun","t":["банк","учреждение"]},{"pos":"noun","t":["берег"]}]}"""
        val e = EntryParse.entry(Json.parseToJsonElement(json) as JsonObject)!!
        assertEquals(listOf("банк", "берег", "учреждение"), EntryUtil.topTranslations(e.tr, 3))
    }

    @Test
    fun wordOfTheDayIsStableAndInRange() {
        val d = LocalDate.of(2026, 11, 6)
        assertEquals(EntryUtil.wordOfTheDay(0, d), EntryUtil.wordOfTheDay(0, d))
        assertTrue(EntryUtil.wordOfTheDay(-5, d) in EntryUtil.WORDS_OF_THE_DAY)
    }
}
