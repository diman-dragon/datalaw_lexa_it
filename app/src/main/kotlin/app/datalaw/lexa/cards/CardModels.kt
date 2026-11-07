package app.datalaw.lexa.cards

/** Карточка колоды в формате flashcards-deck. */
data class DeckCard(
    val front: String,
    val back: String,
    val example: String,
    val exampleTranslation: String,
)

/** Пример: итальянское предложение и его перевод (перевода может не быть). */
data class ExamplePair(val ita: String, val ru: String)

enum class CardStatus {
    /** ждёт перевода */
    QUEUED,
    WORKING,

    /** карточка собрана */
    READY,

    /** слова нет в словаре */
    NOT_FOUND,

    /** слово есть, но русского перевода в словаре нет */
    NO_TRANSLATION,

    /** такая карточка уже есть в колоде или выше в списке (данные сохранены, в экспорт не идёт) */
    DUPLICATE,

    /** не удалось прочитать данные (нет сети и словарь не скачан) */
    ERROR,
}

data class CardItem(
    val id: Long,
    /** что ввёл пользователь */
    val input: String,
    val status: CardStatus = CardStatus.QUEUED,
    val front: String = input,
    val back: String = "",
    val example: String = "",
    val exampleTranslation: String = "",
    val alternatives: List<ExamplePair> = emptyList(),
    val altIndex: Int = 0,
    /** лемма, если введена форма слова («mangio» → «mangiare») */
    val via: String? = null,
    /** варианты замены при NOT_FOUND */
    val suggestions: List<String> = emptyList(),
    /** suggestions — итальянские эквиваленты русского ввода (а не исправления опечатки) */
    val suggestionsAreTranslations: Boolean = false,
) {
    /** данные карточки есть (READY либо отброшена как дубликат) */
    val hasData: Boolean get() = status == CardStatus.READY || status == CardStatus.DUPLICATE
    val exportable: Boolean get() = status == CardStatus.READY
    val needsAttention: Boolean
        get() = status == CardStatus.NOT_FOUND || status == CardStatus.NO_TRANSLATION || status == CardStatus.ERROR

    fun toCard() = DeckCard(front, back, example, exampleTranslation)
}
