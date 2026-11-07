package app.datalaw.lexa.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.datalaw.lexa.LexaApp
import app.datalaw.lexa.core.Entry
import app.datalaw.lexa.core.EntryUtil
import app.datalaw.lexa.core.FrequencyBand
import app.datalaw.lexa.core.RelationKey
import app.datalaw.lexa.core.Search
import app.datalaw.lexa.core.Text
import app.datalaw.lexa.platform.Share
import app.datalaw.lexa.ui.Nav
import app.datalaw.lexa.ui.Route
import app.datalaw.lexa.ui.components.Chip
import app.datalaw.lexa.ui.components.ChipTone
import app.datalaw.lexa.ui.components.Chips
import app.datalaw.lexa.ui.components.EmptyState
import app.datalaw.lexa.ui.components.ErrorBox
import app.datalaw.lexa.ui.components.Header
import app.datalaw.lexa.ui.components.IconBtn
import app.datalaw.lexa.ui.components.LexaCard
import app.datalaw.lexa.ui.components.LinkButton
import app.datalaw.lexa.ui.components.Segmented
import app.datalaw.lexa.ui.components.Skeleton
import app.datalaw.lexa.ui.components.SrcNote
import app.datalaw.lexa.ui.components.highlighted
import app.datalaw.lexa.ui.components.rememberItIndex
import app.datalaw.lexa.ui.components.rememberResource
import app.datalaw.lexa.ui.components.rememberSay
import app.datalaw.lexa.ui.components.rememberTap
import app.datalaw.lexa.ui.components.sayNow
import app.datalaw.lexa.ui.i18n.Str
import app.datalaw.lexa.ui.i18n.markLabel
import app.datalaw.lexa.ui.i18n.posLabel
import app.datalaw.lexa.ui.i18n.t
import app.datalaw.lexa.ui.i18n.tagLabel
import app.datalaw.lexa.ui.theme.L
import app.datalaw.lexa.ui.theme.Serif
import kotlinx.coroutines.launch

private enum class WordTab { DEFS, TR, THES, EX }

private fun freqLabel(b: FrequencyBand): Str = when (b) {
    FrequencyBand.VERY_COMMON -> Str.freqVeryCommon
    FrequencyBand.COMMON -> Str.freqCommon
    FrequencyBand.UNCOMMON -> Str.freqUncommon
    FrequencyBand.RARE -> Str.freqRare
}

/** Итальянское слово-ссылка: кликабельно, только если оно есть в словаре. */
@Composable
private fun WordChip(word: String, known: (String) -> Boolean, nav: Nav, tone: ChipTone = ChipTone.TEAL) {
    val key = Text.normalizeIt(word)
    val ok = known(key)
    Chip(word, tone = if (ok) tone else ChipTone.NEUTRAL, onClick = if (ok) ({ nav.openWord(key) }) else null)
}

@Composable
private fun rememberKnown(): (String) -> Boolean {
    val ix = rememberItIndex().data
    return remember(ix) { { k: String -> ix?.has(k) == true } }
}

@Composable
private fun NotFound(word: String, nav: Nav) {
    val ix = rememberItIndex().data
    val options = remember(ix, word) { if (ix != null) Search.suggestSimilar(ix, word, 8) else emptyList() }
    EmptyState(Icons.Rounded.Search, text = if (options.isNotEmpty()) t(Str.wordNotFoundHint) else "", title = t(Str.wordNotFound)) {
        if (options.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Chips { for (h in options) Chip(h.label, tone = ChipTone.TEAL, onClick = { nav.openWord(h.key, replace = true) }) }
        }
    }
}

@Composable
fun WordScreen(nav: Nav, word: String) {
    val c = L.c
    val app = LexaApp.instance
    val store = app.store
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val tap = rememberTap()
    val key = Text.normalizeIt(word)

    val res = rememberResource("entry:$key") { app.dictionary.getEntry(key) }
    val entry = res.data
    var tab by remember(key) { mutableStateOf(WordTab.DEFS) }
    var playing by remember { mutableStateOf(false) }

    LaunchedEffect(entry) { if (entry != null) store.pushHistory(key) }
    DisposableEffect(key) { onDispose { app.speaker.stop() } }

    val display = entry?.w ?: word
    val isFav = store.isSaved(key)
    val band = EntryUtil.frequencyBand(entry?.z)
    val ipa = entry?.let { EntryUtil.ipaFor(it) }
    val gloss = entry?.let { EntryUtil.topTranslations(it.tr, 4).joinToString(" · ") }.orEmpty()
    val locale = "it-IT"

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
        Header(
            title = "",
            onBack = { nav.back() },
            actions = {
                IconBtn(Icons.Rounded.Share, t(Str.share), {
                    val lines = ArrayList<String>()
                    lines.add(display + (ipa?.let { "  $it" } ?: ""))
                    if (gloss.isNotEmpty()) lines.add(gloss)
                    for (m in entry?.m?.take(3) ?: emptyList()) m.s.firstOrNull()?.d?.let { lines.add("${m.t}: $it") }
                    Share.text(ctx, display, lines.joinToString("\n"))
                })
                IconBtn(if (isFav) Icons.Rounded.Star else Icons.Rounded.StarBorder, t(if (isFav) Str.removeFromSaved else Str.addToSaved), {
                    tap(true)
                    val now = store.toggleSaved(key, EntryUtil.firstDefinition(entry))
                    store.showToast(t(if (now) Str.toastSaved else Str.toastRemoved))
                }, on = isFav)
            },
        )

        Text(display, fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, color = c.ink, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(if (playing) c.accent else c.accentSoft),
                contentAlignment = Alignment.Center,
            ) {
                IconBtn(Icons.Rounded.VolumeUp, t(Str.pronounce), {
                    tap(false)
                    scope.launch {
                        playing = true
                        try {
                            sayNow(display, locale)
                        } finally {
                            playing = false
                        }
                    }
                }, tint = if (playing) c.accentInk else c.accent)
            }
            if (ipa != null) Text(ipa, color = c.ink3, fontFamily = FontFamily.Monospace, fontSize = 15.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        }
        if (gloss.isNotEmpty()) Text(gloss, color = c.accent, fontSize = 20.sp, modifier = Modifier.padding(top = 8.dp))
        if (band != null) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                for (i in 1..4) {
                    Box(Modifier.padding(end = 3.dp).size(7.dp).clip(CircleShape).background(if (i <= band.level) c.accent else c.line))
                }
                Text(t(freqLabel(band)), color = c.ink3, fontSize = 12.sp, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 6.dp))
            }
        }
        Spacer(Modifier.height(12.dp))

        if (res.isLoading) Skeleton(6)
        if (res.isFailed) ErrorBox(res.error, res.reload) { nav.tab(Route.Settings) }
        if (!res.isLoading && !res.isFailed && entry == null) NotFound(key, nav)

        if (entry != null && entry.isForm) {
            LexaCard(tint = c.accentSoft) {
                Text(t(Str.formOf), fontWeight = FontWeight.SemiBold, color = c.ink)
                if (entry.rg != null) Text(entry.rg, color = c.ink2, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
                Spacer(Modifier.height(8.dp))
                Chips { for (l in entry.r) Chip(l, tone = ChipTone.TEAL, onClick = { nav.openWord(l, replace = true) }) }
            }
        }

        if (entry != null && !entry.isForm) {
            Segmented(
                listOf(
                    WordTab.DEFS to t(Str.tabDefinitions), WordTab.TR to t(Str.tabTranslations),
                    WordTab.THES to t(Str.tabThesaurus), WordTab.EX to t(Str.tabExamples),
                ),
                tab, { tab = it }, fill = true,
            )
            Spacer(Modifier.height(10.dp))
            when (tab) {
                WordTab.DEFS -> Definitions(entry, nav)
                WordTab.TR -> Translations(entry)
                WordTab.THES -> Thesaurus(entry, nav)
                WordTab.EX -> Examples(key, entry)
            }
        }
    }
}

/* ───────────── Значения ───────────── */

@Composable
private fun Definitions(entry: Entry, nav: Nav) {
    val c = L.c
    val known = rememberKnown()
    val ant = entry.rel[RelationKey.ANT] ?: emptyList()
    Column {
        entry.m.forEachIndexed { index, m ->
            var all by remember(entry.w, m.t) { mutableStateOf(false) }
            val limit = 5
            val senses = if (all) m.s else m.s.take(limit)
            Text(posLabel(m.t).uppercase(), color = c.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, maxLines = 1, softWrap = false, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
            senses.forEachIndexed { i, s ->
                Row(Modifier.padding(vertical = 6.dp)) {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(c.accentSoft), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", color = c.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        if (s.g.isNotEmpty()) Text(s.g.joinToString(" · ") { tagLabel(it) }, color = c.warm, fontSize = 12.sp)
                        Text(s.d, color = c.ink, fontSize = 16.sp)
                        s.e.firstOrNull()?.let { ex ->
                            Text("“$ex”", color = c.ink3, fontStyle = FontStyle.Italic, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
                            s.x.firstOrNull()?.takeIf { it.isNotEmpty() }?.let { Text(it, color = c.ink3, fontSize = 13.sp) }
                        }
                        if (s.y.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Chips { for (w in s.y.take(6)) WordChip(w, known, nav) }
                        }
                    }
                }
            }
            if (m.s.size > limit && !all) LinkButton("${t(Str.seeAll)} · ${m.s.size - limit}") { all = true }
            // антонимы статьи показываем под первой частью речи, чтобы не дублировать
            if (index == 0 && ant.isNotEmpty()) {
                Text(t(Str.sectionAntonyms), color = c.ink3, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
                Chips { for (w in ant.take(10)) WordChip(w, known, nav, ChipTone.ACCENT) }
            }
        }
        if (entry.et != null) {
            Text(t(Str.etymology), color = c.ink3, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 18.dp, bottom = 4.dp))
            Text(entry.et, color = c.ink2, fontSize = 14.sp)
        }
        SrcNote(t(Str.trSource))
    }
}

/* ───────────── Переводы ───────────── */

@Composable
private fun Translations(entry: Entry) {
    val c = L.c
    val say = rememberSay()
    if (entry.tr.isEmpty()) {
        Text(t(Str.trNone), color = c.ink3, modifier = Modifier.padding(vertical = 12.dp))
        return
    }
    Column {
        Text(t(Str.trGroupsTitle), color = c.ink3, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
        for (g in entry.tr) {
            Row(Modifier.padding(top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                val head = listOfNotNull(g.pos?.let { posLabel(it) }, g.g?.let { t(Str.trMeaning, "n" to it) }).joinToString(" · ")
                if (head.isNotEmpty()) Text(head, color = c.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
            Chips {
                for (term in g.t) {
                    val sub = listOfNotNull(term.r, term.x?.let { markLabel(it) }).joinToString(" · ").ifEmpty { null }
                    Chip(term.w, tone = ChipTone.TEAL, sub = sub, onClick = { say(term.w, "ru-RU") })
                }
            }
        }
        SrcNote("${t(Str.trTapHear)} · ${t(Str.trSource)}")
    }
}

/* ───────────── Тезаурус ───────────── */

private val REL_ORDER = listOf(RelationKey.SYN, RelationKey.ANT, RelationKey.HYPER, RelationKey.HYPO, RelationKey.REL, RelationKey.DER, RelationKey.COORD)

private fun relLabel(k: RelationKey): Str = when (k) {
    RelationKey.SYN -> Str.relSyn
    RelationKey.ANT -> Str.relAnt
    RelationKey.HYPER -> Str.relHyper
    RelationKey.HYPO -> Str.relHypo
    RelationKey.REL -> Str.relRel
    RelationKey.DER -> Str.relDer
    RelationKey.COORD -> Str.relCoord
}

/** Синонимы и антонимы значений тоже попадают в общий список. */
private fun collectRelations(entry: Entry): Map<RelationKey, List<String>> {
    val out = LinkedHashMap<RelationKey, List<String>>()
    for (k in REL_ORDER) {
        val words = ArrayList(entry.rel[k] ?: emptyList())
        if (k == RelationKey.SYN || k == RelationKey.ANT) {
            for (m in entry.m) for (s in m.s) for (w in if (k == RelationKey.SYN) s.y else s.a) if (w !in words) words.add(w)
        }
        if (words.isNotEmpty()) out[k] = words
    }
    return out
}

@Composable
private fun Thesaurus(entry: Entry, nav: Nav) {
    val c = L.c
    val known = rememberKnown()
    val rels = remember(entry) { collectRelations(entry) }
    val available = rels.keys.toList()
    if (available.isEmpty()) {
        Text(t(Str.relEmpty), color = c.ink3, modifier = Modifier.padding(vertical = 12.dp))
        return
    }
    var rel by remember(entry.w) { mutableStateOf(available.first()) }
    if (rel !in available) rel = available.first()
    Column {
        Segmented(available.map { it to t(relLabel(it)) }, rel, { rel = it })
        Spacer(Modifier.height(12.dp))
        Chips { for (w in rels[rel] ?: emptyList()) WordChip(w, known, nav, if (rel == RelationKey.ANT) ChipTone.ACCENT else ChipTone.TEAL) }
        SrcNote(t(Str.trSource))
    }
}

/* ───────────── Примеры ───────────── */

@Composable
private fun Examples(key: String, entry: Entry) {
    val c = L.c
    val app = LexaApp.instance
    val say = rememberSay()
    val locale = "it-IT"
    val sentences = rememberResource("sent:$key") { app.dictionary.getSentences(key) }
    val fromDict = remember(entry) {
        entry.m.flatMap { m -> m.s.flatMap { s -> s.e.mapIndexed { i, text -> text to s.x.getOrNull(i) } } }
            .distinctBy { it.first }.take(8)
    }
    val pairs = sentences.data ?: emptyList()

    @Composable
    fun ExampleRow(ita: String, ru: String?) {
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(highlighted(ita, key), color = c.ink, fontSize = 16.sp)
                if (!ru.isNullOrEmpty()) Text(ru, color = c.ink3, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
            }
            IconBtn(Icons.Rounded.VolumeUp, t(Str.pronounce), { say(ita, locale) }, size = 36)
        }
    }

    Column {
        if (fromDict.isNotEmpty()) {
            Text(t(Str.exDict), color = c.ink3, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 6.dp))
            for ((ex, tr) in fromDict) ExampleRow(ex, tr)
        }
        if (sentences.isLoading) Skeleton(3)
        if (pairs.isNotEmpty()) {
            Text(t(Str.exSentences), color = c.ink3, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
            for (p in pairs) ExampleRow(p.ita, p.ru)
            SrcNote(t(Str.exSource))
        }
        if (sentences.isFailed) {
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t(Str.exLoadFailed), color = c.ink3)
                LinkButton(t(Str.retry), sentences.reload)
            }
        }
        if (fromDict.isEmpty() && !sentences.isLoading && !sentences.isFailed && pairs.isEmpty()) {
            Text(t(Str.exNone), color = c.ink3, modifier = Modifier.padding(vertical = 12.dp))
        }
    }
}
