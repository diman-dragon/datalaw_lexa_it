package app.datalaw.lexa.cards

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** Существующая колода, к которой дописываются новые карточки. */
class BaseDeck(
    val root: JsonObject,
    val name: String,
    val cardCount: Int,
    /** ключи (CardLogic.dedupKey) уже имеющихся карточек */
    val keys: Set<String>,
)

/** Чтение и запись колоды в формате flashcards-deck (version 1) — как в приложенном файле. */
@OptIn(ExperimentalSerializationApi::class)
object DeckIo {
    const val FORMAT = "flashcards-deck"
    private const val VERSION = 1
    private const val LANG_NAME = "Итальянский"
    private const val LANG_TTS = "it-IT"

    private val pretty = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    private fun card(c: DeckCard): JsonObject = buildJsonObject {
        put("front", c.front)
        put("back", c.back)
        put("example", c.example)
        put("example_translation", c.exampleTranslation)
    }

    private fun render(root: JsonObject): String = pretty.encodeToString(JsonElement.serializer(), root) + "\n"

    /** Новая колода. */
    fun create(deckName: String, cards: List<DeckCard>): String = render(
        buildJsonObject {
            put("format", FORMAT)
            put("version", VERSION)
            put("language", buildJsonObject {
                put("name", LANG_NAME)
                put("tts", LANG_TTS)
            })
            put("deck", buildJsonObject { put("name", deckName) })
            put("cards", JsonArray(cards.map(::card)))
        },
    )

    private fun prim(e: JsonElement?): String? = (e as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    /** null — это не файл колоды flashcards-deck. */
    fun parse(text: String): BaseDeck? {
        val root = runCatching { Json.parseToJsonElement(text.trimStart('\uFEFF')) as? JsonObject }.getOrNull() ?: return null
        if (prim(root["format"]) != FORMAT) return null
        val cards = root["cards"] as? JsonArray ?: return null
        val keys = cards.mapNotNull { prim((it as? JsonObject)?.get("front")) }.map(CardLogic::dedupKey).toSet()
        val name = prim((root["deck"] as? JsonObject)?.get("name")).orEmpty()
        return BaseDeck(root, name, cards.size, keys)
    }

    /**
     * Дописывает карточки в существующую колоду. Всё остальное (порядок ключей, неизвестные поля колоды
     * и карточек) сохраняется как было; меняется только название колоды.
     */
    fun merge(base: BaseDeck, deckName: String, cards: List<DeckCard>): String {
        val old = base.root["cards"] as? JsonArray ?: JsonArray(emptyList())
        val deck = (base.root["deck"] as? JsonObject)
            ?.let { JsonObject(it + ("name" to JsonPrimitive(deckName))) }
            ?: buildJsonObject { put("name", deckName) }
        val root = LinkedHashMap<String, JsonElement>(base.root)
        root["deck"] = deck
        root["cards"] = JsonArray(old + cards.map(::card))
        return render(JsonObject(root))
    }
}
