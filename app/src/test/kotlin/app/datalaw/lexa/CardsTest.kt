package app.datalaw.lexa

import app.datalaw.lexa.cards.CardGenerator
import app.datalaw.lexa.cards.CardLogic
import app.datalaw.lexa.cards.CardStatus
import app.datalaw.lexa.cards.DeckCard
import app.datalaw.lexa.cards.DeckIo
import app.datalaw.lexa.cards.ExamplePair
import app.datalaw.lexa.cards.GenOptions
import app.datalaw.lexa.cards.WordListParser
import app.datalaw.lexa.core.Entry
import app.datalaw.lexa.core.Meaning
import app.datalaw.lexa.core.Sense
import app.datalaw.lexa.core.SentencePair
import app.datalaw.lexa.core.TrGroup
import app.datalaw.lexa.core.TrTerm
import app.datalaw.lexa.core.WordIndex
import app.datalaw.lexa.data.DictionarySource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeDict : DictionarySource {
    val entries = mapOf(
        "mangiare" to Entry(
            "mangiare", 46,
            m = listOf(Meaning("verb", listOf(Sense("есть, кушать", e = listOf("Mi piace mangiare."), x = listOf("Я люблю есть."))))),
            tr = listOf(TrGroup("verb", "1", listOf(TrTerm("есть"), TrTerm("кушать"))), TrGroup("verb", "2", listOf(TrTerm("съедать")))),
        ),
        "casa" to Entry(
            "casa", 54, m = listOf(Meaning("noun", listOf(Sense("дом, здание")))),
            tr = listOf(TrGroup("noun", "1", listOf(TrTerm("дом"), TrTerm("здание")))),
        ),
        "mangio" to Entry("mangio", 35, r = listOf("mangiare"), rg = "1-е лицо ед. ч. глагола mangiare"),
        "fantasma" to Entry("fantasma", 40, m = listOf(Meaning("noun", listOf(Sense("привидение"))))),
        "caffè" to Entry("caffè", 44, m = listOf(Meaning("noun", listOf(Sense("кофе")))), tr = listOf(TrGroup("noun", "1", listOf(TrTerm("кофе"))))),
    )
    val sentences = mapOf(
        "mangiare" to listOf(
            SentencePair("Mangiamo sempre insieme la domenica sera.", "Мы всегда ужинаем вместе по воскресеньям."),
            SentencePair("Io mangio una mela ogni mattina prima di uscire.", "Я ем яблоко каждое утро перед выходом."),
        ),
        "casa" to listOf(SentencePair("Abbiamo comprato una bella casa vicino al mare.", "Мы купили красивый дом у моря.")),
    )
    val reverse = mapOf("бег" to listOf("corsa", "correre"))

    override suspend fun getEntry(key: String) = entries[key]
    override suspend fun getReverse(key: String) = reverse[key] ?: emptyList()
    override suspend fun getSentences(key: String) = sentences[key] ?: emptyList()
    override suspend fun itIndex() = WordIndex.parse(entries.keys.sorted().joinToString("\n") { "$it\t50" } + "\n")
}

class CardsTest {
    private val gen = CardGenerator(FakeDict())
    private fun generate(s: String, o: GenOptions = GenOptions()) = runBlocking { gen.generate(s, o) }

    @Test
    fun candidateKeys() {
        assertEquals(listOf("la casa", "casa"), CardLogic.candidateKeys("La casa"))
        assertEquals(listOf("l'amico", "amico"), CardLogic.candidateKeys("L'amico"))
        assertEquals(listOf("isola"), CardLogic.candidateKeys("Isola"), "«i» в начале слова — не артикль")
        assertEquals("casa", CardLogic.dedupKey("La casa."))
    }

    @Test
    fun verbCardHasTranslationsAndExampleWithTranslation() {
        val r = generate("mangiare")
        assertEquals(CardStatus.READY, r.status)
        assertEquals("mangiare", r.front)
        assertEquals(setOf("есть", "съедать", "кушать"), r.back.split(", ").toSet())
        assertEquals("Мы всегда ужинаем вместе по воскресеньям.", r.alternatives.first().ru)
    }

    @Test
    fun userArticleIsKept() {
        val r = generate("la casa")
        assertEquals(CardStatus.READY, r.status)
        assertEquals("la casa", r.front)
        assertEquals("дом, здание", r.back)
    }

    @Test
    fun maxTranslationsIsRespected() {
        assertEquals("есть", generate("mangiare", GenOptions(maxTranslations = 1)).back)
    }

    @Test
    fun formFollowsLemma() {
        val r = generate("mangio")
        assertEquals(CardStatus.READY, r.status)
        assertEquals("mangiare", r.via)
        assertEquals("mangio", r.front)
        // предложение с той формой слова, что ввёл пользователь, идёт первым
        assertTrue(r.alternatives.first().ita.contains("mangio"))
    }

    @Test
    fun missingTranslationAndMissingWord() {
        assertEquals(CardStatus.NO_TRANSLATION, generate("fantasma").status)
        val nf = generate("cassa")
        assertEquals(CardStatus.NOT_FOUND, nf.status)
        assertTrue("casa" in nf.suggestions)
    }

    @Test
    fun missingAccentFindsWord() {
        val nf = generate("caffe")
        assertEquals(CardStatus.NOT_FOUND, nf.status)
        assertEquals("caffè", nf.suggestions.first())
    }

    @Test
    fun russianInputSuggestsItalian() {
        val r = generate("бег")
        assertEquals(CardStatus.NOT_FOUND, r.status)
        assertTrue(r.suggestionsAreTranslations)
        assertEquals(listOf("corsa", "correre"), r.suggestions)
    }

    @Test
    fun dictionaryExampleKeepsItsTranslationAndIsFallback() {
        val ex = CardLogic.chooseExamples(emptyList(), listOf(ExamplePair("Mi piace mangiare.", "Я люблю есть.")), "mangiare")
        assertEquals("Я люблю есть.", ex.single().ru)
        val noTr = CardLogic.chooseExamples(emptyList(), listOf(ExamplePair("Ciao!", "")), "ciao")
        assertEquals("", noTr.single().ru)
    }

    /* ───── список слов ───── */

    @Test
    fun parsesPastedText() {
        val words = WordListParser.parseText("1. mela\n- Banana; ciliegia\n# comment\n\nmela\n\"a presto\"")
        assertEquals(listOf("mela", "Banana", "ciliegia", "a presto"), words)
    }

    @Test
    fun parsesCsvWithHeaderAndQuotes() {
        val csv = "word,translation\n\"gelato artigianale\",мороженое\ncasa;дом\ncaffè\tкофе\n"
        assertEquals(listOf("gelato artigianale", "casa", "caffè"), WordListParser.parseFile("list.csv", csv))
    }

    @Test
    fun parsesJsonArraysAndDecks() {
        assertEquals(listOf("a", "b"), WordListParser.parseFile("w.json", """["a","b","A"]"""))
        assertEquals(listOf("la casa"), WordListParser.parseFile("d.json", """{"format":"flashcards-deck","cards":[{"front":"la casa","back":"x"}]}"""))
    }

    /* ───── формат колоды ───── */

    private val cards = listOf(DeckCard("mangiare", "есть", "Mi piace mangiare.", "Я люблю есть."))

    @Test
    fun createsDeckInTheSameFormat() {
        val text = DeckIo.create("Моя колода", cards)
        val o = Json.parseToJsonElement(text) as JsonObject
        assertEquals(listOf("format", "version", "language", "deck", "cards"), o.keys.toList())
        assertEquals("flashcards-deck", o["format"]!!.jsonPrimitive.content)
        assertEquals("1", o["version"]!!.jsonPrimitive.content)
        val card = (o["cards"] as JsonArray)[0] as JsonObject
        assertEquals(listOf("front", "back", "example", "example_translation"), card.keys.toList())
        assertTrue(text.contains("\"name\": \"Итальянский\"")) // кириллица не экранируется, отступ 2 пробела
        assertTrue(text.contains("\n  \"format\""))
    }

    @Test
    fun mergeKeepsExistingContentAndAppends() {
        val base = """{"format":"flashcards-deck","version":1,"language":{"name":"Итальянский","tts":"it-IT"},
            "deck":{"name":"Старая","extra":true},"cards":[{"front":"la casa","back":"дом","example":"e","example_translation":"p","tag":"x"}],"custom":5}"""
        val b = DeckIo.parse(base)
        assertNotNull(b)
        assertEquals(1, b!!.cardCount)
        assertTrue("casa" in b.keys)
        val merged = Json.parseToJsonElement(DeckIo.merge(b, "Новая", cards)) as JsonObject
        assertEquals("5", merged["custom"]!!.jsonPrimitive.content)
        assertEquals("Новая", ((merged["deck"] as JsonObject)["name"]!!).jsonPrimitive.content)
        assertEquals("true", ((merged["deck"] as JsonObject)["extra"]!!).jsonPrimitive.content)
        val list = merged["cards"] as JsonArray
        assertEquals(2, list.size)
        assertEquals("x", ((list[0] as JsonObject)["tag"]!!).jsonPrimitive.content)
        assertEquals("mangiare", ((list[1] as JsonObject)["front"]!!).jsonPrimitive.content)
    }

    @Test
    fun rejectsForeignJson() {
        assertNull(DeckIo.parse("""{"format":"other","cards":[]}"""))
        assertNull(DeckIo.parse("not json"))
        assertFalse(DeckIo.parse("""{"format":"flashcards-deck","cards":[]}""") == null)
    }
}
