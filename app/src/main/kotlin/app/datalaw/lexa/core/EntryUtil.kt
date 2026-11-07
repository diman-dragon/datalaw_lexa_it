package app.datalaw.lexa.core

import java.time.LocalDate

enum class FrequencyBand(val level: Int) { VERY_COMMON(4), COMMON(3), UNCOMMON(2), RARE(1) }

object EntryUtil {
    /** Первая транскрипция статьи. У итальянского один вариант произношения. */
    fun ipaFor(entry: Entry): String? = entry.p.firstOrNull()?.ipa

    fun firstDefinition(entry: Entry?, max: Int = 90): String? {
        val d = entry?.m?.firstOrNull()?.s?.firstOrNull()?.d ?: return null
        return if (d.length > max) d.take(max - 1) + "…" else d
    }

    /**
     * Самые частые переводы — по одному из каждого значения, без повторов.
     * preferPos — если задано и есть группы этой части речи, берём только их.
     */
    fun topTranslations(groups: List<TrGroup>, limit: Int = 4, preferPos: String? = null): List<String> {
        val pool = preferPos
            ?.let { pos -> groups.filter { it.pos.equals(pos, ignoreCase = true) } }
            ?.takeIf { it.isNotEmpty() }
            ?: groups
        val seen = HashSet<String>()
        val out = ArrayList<String>()
        val rounds = pool.maxOfOrNull { it.t.size } ?: 0
        for (i in 0 until rounds) {
            if (out.size >= limit) break
            for (g in pool) {
                val term = g.t.getOrNull(i) ?: continue
                val text = term.w.trim()
                if (text.isEmpty() || !seen.add(text.lowercase())) continue
                out.add(text)
                if (out.size >= limit) break
            }
        }
        return out
    }

    /** z — Zipf × 10: 50+ — «e», «essere»; 40+ — обычная лексика; 30+ — редкая; ниже — очень редкая. */
    fun frequencyBand(z: Int?): FrequencyBand? = when {
        z == null || z <= 0 -> null
        z >= 50 -> FrequencyBand.VERY_COMMON
        z >= 40 -> FrequencyBand.COMMON
        z >= 30 -> FrequencyBand.UNCOMMON
        else -> FrequencyBand.RARE
    }

    /** Слова дня — выразительная лексика; в карточку попадают, только если есть в собранном словаре. */
    val WORDS_OF_THE_DAY: List<String> = listOf(
        "nostalgia", "meraviglia", "gioia", "tramonto", "orizzonte", "armonia", "sorriso", "fantasia",
        "coraggio", "libertà", "amicizia", "sogno", "stella", "viaggio", "nuvola", "profumo", "silenzio",
        "bellezza", "passeggiata", "abbraccio", "tenerezza", "malinconia", "speranza", "avventura",
        "scoperta", "dolcezza", "eleganza", "curiosità", "pazienza", "gentilezza", "fortuna", "ricordo",
        "ospitalità", "aurora", "brezza", "crepuscolo", "labirinto", "leggenda", "miracolo", "tesoro",
        "farfalla", "giardino", "montagna", "tempesta", "ruscello", "sentiero", "fiamma", "isola",
        "castello", "piazza", "cortile", "vento", "pioggia", "neve", "luce", "ombra", "canto", "danza",
        "poesia", "musica", "romanzo", "pennello", "tela", "sipario", "palcoscenico", "vendemmia",
        "focaccia", "tiramisù", "cappuccino", "espresso", "bottega", "trattoria", "passione", "serenità",
        "sincerità", "allegria", "fiducia", "gratitudine", "entusiasmo",
    )

    fun wordOfTheDay(offset: Int = 0, today: LocalDate = LocalDate.now()): String {
        val n = WORDS_OF_THE_DAY.size
        val idx = (((today.toEpochDay() + offset) % n) + n) % n
        return WORDS_OF_THE_DAY[idx.toInt()]
    }
}
