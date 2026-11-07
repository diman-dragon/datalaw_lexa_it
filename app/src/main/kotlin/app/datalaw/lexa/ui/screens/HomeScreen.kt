package app.datalaw.lexa.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.datalaw.lexa.LexaApp
import app.datalaw.lexa.core.EntryUtil
import app.datalaw.lexa.core.Hit
import app.datalaw.lexa.core.Search
import app.datalaw.lexa.core.Text
import app.datalaw.lexa.platform.VoiceRecognizer
import app.datalaw.lexa.ui.Nav
import app.datalaw.lexa.ui.Route
import app.datalaw.lexa.ui.components.Chip
import app.datalaw.lexa.ui.components.Chips
import app.datalaw.lexa.ui.components.ErrorBox
import app.datalaw.lexa.ui.components.Eyebrow
import app.datalaw.lexa.ui.components.IconBtn
import app.datalaw.lexa.ui.components.LexaCard
import app.datalaw.lexa.ui.components.LinkButton
import app.datalaw.lexa.ui.components.LogoMark
import app.datalaw.lexa.ui.components.PrimaryButton
import app.datalaw.lexa.ui.components.SectionTitle
import app.datalaw.lexa.ui.components.Skeleton
import app.datalaw.lexa.ui.components.SrcNote
import app.datalaw.lexa.ui.components.VoiceSheet
import app.datalaw.lexa.ui.components.rememberItIndex
import app.datalaw.lexa.ui.components.rememberResource
import app.datalaw.lexa.ui.components.rememberRuIndex
import app.datalaw.lexa.ui.components.rememberTap
import app.datalaw.lexa.ui.i18n.Str
import app.datalaw.lexa.ui.i18n.t
import app.datalaw.lexa.ui.theme.L
import app.datalaw.lexa.ui.theme.Serif
import kotlinx.coroutines.delay

@Composable
private fun WordOfTheDay(nav: Nav) {
    val c = L.c
    val app = LexaApp.instance
    val tap = rememberTap()
    var offset by remember { mutableIntStateOf(0) }
    val word = EntryUtil.wordOfTheDay(offset)
    val res = rememberResource("entry:$word") { app.dictionary.getEntry(word) }
    val entry = res.data
    // слова нет в собранном словаре (например, образец данных) — не показываем пустую карточку
    if (!res.isLoading && !res.isFailed && entry == null) return

    val ipa = entry?.let { EntryUtil.ipaFor(it) }
    val ru = entry?.let { EntryUtil.topTranslations(it.tr, 3).joinToString(" · ") }.orEmpty()
    val def = EntryUtil.firstDefinition(entry, 140)

    LexaCard(onClick = { nav.openWord(word) }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = c.accent, modifier = Modifier.size(14.dp))
            Spacer(Modifier.size(6.dp))
            Eyebrow(t(Str.homeWotd))
            Spacer(Modifier.weight(1f))
            IconBtn(Icons.Rounded.Shuffle, t(Str.homeRandom), {
                tap(false)
                offset += 1 + (Math.random() * (EntryUtil.WORDS_OF_THE_DAY.size - 1)).toInt()
            }, size = 36)
        }
        Text(word, fontFamily = Serif, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = c.ink, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        if (ipa != null) Text(ipa, color = c.ink3, fontSize = 14.sp, fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        if (res.isLoading) Skeleton(2)
        if (ru.isNotEmpty()) Text(ru, color = c.accent, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        if (def != null) Text(def, color = c.ink2, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun HomeScreen(nav: Nav) {
    val c = L.c
    val app = LexaApp.instance
    val store = app.store
    val tap = rememberTap()
    var text by rememberSaveable { mutableStateOf("") }
    val q = text.trim()
    val script = Text.detectScript(q)
    val key = if (script == Text.Script.CYRILLIC) Text.normalizeRu(q) else Text.normalizeIt(q)
    val debounced by produceState(key, key) {
        delay(60)
        value = key
    }

    val itIx = rememberItIndex()
    val ruIx = rememberRuIndex()

    val hits: List<Hit> = remember(debounced, key, script, itIx.data, ruIx.data) {
        if (debounced.isEmpty() || debounced != key) emptyList()
        else when {
            script == Text.Script.CYRILLIC && ruIx.data != null -> Search.prefixSearch(ruIx.data!!, debounced, 8)
            script == Text.Script.LATIN && itIx.data != null -> Search.prefixSearch(itIx.data!!, debounced, 8)
            else -> emptyList()
        }
    }

    fun go(hit: String) {
        tap(false)
        if (script == Text.Script.CYRILLIC) nav.push(Route.Find(hit)) else nav.openWord(hit)
    }

    fun submit() {
        if (key.isEmpty()) return
        val ix = if (script == Text.Script.CYRILLIC) ruIx.data else itIx.data
        val exact = ix?.has(key) == true
        go(if (exact) key else (hits.firstOrNull()?.key ?: key))
    }

    var voiceOpen by remember { mutableStateOf(false) }
    val voiceOn = VoiceRecognizer.available(androidx.compose.ui.platform.LocalContext.current)
    if (voiceOpen) {
        VoiceSheet(
            onClose = { voiceOpen = false },
            onResult = { alts, ru ->
                voiceOpen = false
                /* слово из словаря открываем сразу, иначе кладём в строку поиска с подсказками */
                val ix = if (ru) ruIx.data else itIx.data
                val pick = VoiceRecognizer.pickAlternative(VoiceRecognizer.cleanAlternatives(alts, ru)) { k -> ix?.has(k) == true }
                if (pick == null) {
                    store.showToast(t(Str.voiceErrNoSpeech))
                } else {
                    text = pick.text
                    if (pick.exact) {
                        if (ru) nav.push(Route.Find(pick.text)) else nav.openWord(pick.text)
                    }
                }
            },
        )
    }

    val failed = if (itIx.isFailed) itIx else if (ruIx.isFailed) ruIx else null
    var packed by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { packed = app.pack.info().installed }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            LogoMark(30)
            Spacer(Modifier.size(8.dp))
            Text(t(Str.appName), fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = c.ink)
            Spacer(Modifier.weight(1f))
            IconBtn(Icons.Rounded.Tune, t(Str.openSettings), { nav.tab(Route.Settings) })
        }
        Text(t(Str.homeHeadline), style = MaterialTheme.typography.headlineMedium, color = c.ink, modifier = Modifier.padding(top = 12.dp))
        Text(t(Str.homeSub), color = c.ink3, modifier = Modifier.padding(top = 6.dp, bottom = 14.dp))

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            placeholder = { Text(t(Str.searchPlaceholder), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(Icons.Rounded.Search, null, tint = c.ink3) },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (text.isNotEmpty()) IconBtn(Icons.Rounded.Close, t(Str.searchClear), { text = "" }, size = 40)
                    if (voiceOn) IconBtn(Icons.Rounded.Mic, t(Str.voiceMic), {
                        tap(false)
                        voiceOpen = true
                    }, size = 44, on = true)
                }
            },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Search, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onSearch = { submit() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = c.surface, unfocusedContainerColor = c.surface,
                focusedBorderColor = c.accent, unfocusedBorderColor = c.line,
                focusedTextColor = c.ink, unfocusedTextColor = c.ink, cursorColor = c.accent,
                focusedPlaceholderColor = c.ink3, unfocusedPlaceholderColor = c.ink3,
            ),
        )

        if (hits.isNotEmpty()) {
            LexaCard(Modifier.padding(top = 8.dp)) {
                for (h in hits) {
                    Row(
                        Modifier.fillMaxWidth().clickable { go(h.key) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (script == Text.Script.CYRILLIC) Icons.Rounded.Translate else Icons.Rounded.Search, null, tint = c.ink3, modifier = Modifier.size(18.dp))
                        Text(h.label, color = c.ink, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 12.dp))
                        Icon(Icons.Rounded.NorthWest, null, tint = c.ink3, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        if (failed != null) {
            ErrorBox(failed.error, onRetry = {
                if (itIx.isFailed) itIx.reload()
                if (ruIx.isFailed) ruIx.reload()
            }, onSettings = { nav.tab(Route.Settings) })
        }

        if (q.isEmpty() && failed == null) {
            Spacer(Modifier.height(16.dp))
            if (!packed && store.settings.offlineHint) {
                LexaCard {
                    Text(t(Str.offlineHintTitle), style = MaterialTheme.typography.titleMedium, color = c.ink)
                    Text(t(Str.offlineHintBody), color = c.ink2, modifier = Modifier.padding(vertical = 6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        PrimaryButton(t(Str.offlineHintAction), {
                            SettingsNav.page = SettingsNav.DATA
                            nav.tab(Route.Settings)
                        })
                        LinkButton(t(Str.offlineHintDismiss)) { store.update { it.copy(offlineHint = false) } }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            WordOfTheDay(nav)

            if (store.history.isNotEmpty()) {
                SectionTitle(t(Str.homeRecent))
                Chips {
                    for (h in store.history.take(10)) Chip(h.word, onClick = { nav.openWord(h.word) })
                }
            }

            Spacer(Modifier.height(16.dp))
            LexaCard(tint = c.accentSoft) {
                Text(t(Str.homeTipTitle), style = MaterialTheme.typography.titleMedium, color = c.ink)
                Text(t(Str.homeTipBody), color = c.ink2, modifier = Modifier.padding(top = 4.dp))
            }
            SrcNote(t(Str.attribution), center = true)
        }
    }
}
