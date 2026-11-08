package app.datalaw.lexa.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.datalaw.lexa.LexaApp
import app.datalaw.lexa.core.EntryUtil
import app.datalaw.lexa.core.Text as TextUtil
import app.datalaw.lexa.platform.Share
import app.datalaw.lexa.store.HistoryItem
import app.datalaw.lexa.store.UiLang
import app.datalaw.lexa.ui.Nav
import app.datalaw.lexa.ui.Route
import app.datalaw.lexa.ui.components.EmptyState
import app.datalaw.lexa.ui.components.ErrorBox
import app.datalaw.lexa.ui.components.Header
import app.datalaw.lexa.ui.components.IconBtn
import app.datalaw.lexa.ui.components.Skeleton
import app.datalaw.lexa.ui.components.SrcNote
import app.datalaw.lexa.ui.components.rememberResource
import app.datalaw.lexa.ui.components.rememberTap
import app.datalaw.lexa.ui.i18n.Str
import app.datalaw.lexa.ui.i18n.t
import app.datalaw.lexa.ui.i18n.wordsCount
import app.datalaw.lexa.ui.theme.L
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
private fun WordRow(word: String, sub: String?, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    val c = L.c
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(word, color = c.ink, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            if (!sub.isNullOrEmpty()) Text(sub, color = c.ink3, fontSize = 13.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

/* ───────────── Обратный поиск: русское слово → итальянские ───────────── */

@Composable
private fun FindRow(word: String, nav: Nav) {
    val c = L.c
    val res = rememberResource("entry:$word") { LexaApp.instance.dictionary.getEntry(word) }
    val entry = res.data
    WordRow(entry?.w ?: word, if (entry != null && !entry.isForm) EntryUtil.firstDefinition(entry, 110) else null, { nav.openWord(word) }) {
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = c.ink3, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun FindScreen(nav: Nav, query: String) {
    val c = L.c
    val key = TextUtil.normalizeRu(query)
    val res = rememberResource("rev:$key") { LexaApp.instance.dictionary.getReverse(key) }
    val words = res.data ?: emptyList()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item { Header(t(Str.findTitle, "q" to query), onBack = { nav.back() }) }
        if (res.isLoading) item { Skeleton(4) }
        if (res.isFailed) item { ErrorBox(res.error, res.reload) { nav.tab(Route.Settings) } }
        if (!res.isLoading && !res.isFailed && words.isEmpty()) item { EmptyState(Icons.Rounded.Search, t(Str.findEmpty)) }
        if (words.isNotEmpty()) {
            item { Text(t(Str.findHint), color = c.ink3, modifier = Modifier.padding(bottom = 4.dp)) }
            items(words.take(40), key = { it }) { FindRow(it, nav) }
            item { SrcNote(t(Str.trSource)) }
        }
    }
}

/* ───────────── Мои слова ───────────── */

@Composable
fun SavedScreen(nav: Nav) {
    val c = L.c
    val store = LexaApp.instance.store
    val ctx = LocalContext.current
    val tap = rememberTap()
    val list = store.saved
    var filter by remember { mutableStateOf("") }
    val f = filter.trim().lowercase()
    val shown = if (f.isEmpty()) list else list.filter { it.word.contains(f) || (it.gloss ?: "").lowercase().contains(f) }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Header(t(Str.savedTitle), actions = {
                if (list.isNotEmpty()) IconBtn(Icons.Rounded.Share, t(Str.savedExport), {
                    Share.text(ctx, t(Str.savedTitle), list.joinToString("\n") { if (it.gloss != null) "${it.word} — ${it.gloss}" else it.word })
                })
            })
        }
        if (list.isEmpty()) {
            item { EmptyState(Icons.Rounded.Star, t(Str.savedEmpty)) }
        } else {
            item {
                Text(wordsCount(list.size), color = c.ink3, modifier = Modifier.padding(bottom = 8.dp))
                OutlinedTextField(
                    value = filter, onValueChange = { filter = it }, singleLine = true,
                    placeholder = { Text(t(Str.savedFilter)) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = c.surface, unfocusedContainerColor = c.surface,
                        focusedBorderColor = c.accent, unfocusedBorderColor = c.line,
                        focusedTextColor = c.ink, unfocusedTextColor = c.ink, cursorColor = c.accent,
                    ),
                )
                if (shown.isEmpty()) Text(t(Str.savedNoMatch), color = c.ink3, modifier = Modifier.padding(top = 12.dp))
            }
            items(shown, key = { it.word }) { x ->
                WordRow(x.word, x.gloss, { nav.openWord(x.word) }) {
                    IconBtn(Icons.Rounded.Star, t(Str.removeFromSaved), {
                        tap(false)
                        store.toggleSaved(x.word)
                    }, on = true)
                }
            }
        }
    }
}

/* ───────────── История ───────────── */

private fun groupByDay(items: List<HistoryItem>, lang: UiLang): List<Pair<String, List<HistoryItem>>> {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val locale = if (lang == UiLang.RU) Locale("ru") else Locale.ENGLISH
    val fmt = DateTimeFormatter.ofPattern("d MMMM", locale)
    val fmtYear = DateTimeFormatter.ofPattern("d MMMM yyyy", locale)
    return items.groupBy { java.time.Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() }.map { (day, list) ->
        val diff = java.time.temporal.ChronoUnit.DAYS.between(day, today)
        val label = when (diff) {
            0L -> t(Str.dayToday)
            1L -> t(Str.dayYesterday)
            else -> day.format(if (day.year == today.year) fmt else fmtYear) // год — иначе ключи секций совпадут
        }
        label to list
    }
}

@Composable
fun HistoryScreen(nav: Nav) {
    val c = L.c
    val store = LexaApp.instance.store
    val items = store.history
    var confirm by remember { mutableStateOf(false) }
    val groups = groupByDay(items, store.settings.uiLang)

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(t(Str.historyTitle)) },
            text = { Text(t(Str.historyClearConfirm)) },
            confirmButton = { TextButton({ store.clearHistory(); confirm = false }) { Text(t(Str.historyClear), color = c.danger) } },
            dismissButton = { TextButton({ confirm = false }) { Text(t(Str.cancel)) } },
            containerColor = c.surface,
        )
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Header(t(Str.historyTitle), actions = {
                if (items.isNotEmpty()) IconBtn(Icons.Rounded.Delete, t(Str.historyClear), { confirm = true })
            })
        }
        if (items.isEmpty()) {
            item { EmptyState(Icons.Rounded.History, t(Str.historyEmpty)) }
        } else {
            for ((label, list) in groups) {
                item(key = "h:$label") {
                    Text(label.uppercase(), color = c.ink3, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp, maxLines = 1, softWrap = false, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                }
                items(list, key = { "w:${it.word}" }) {
                    WordRow(it.word, null, { nav.openWord(it.word) }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = c.ink3, modifier = Modifier.size(18.dp))
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
