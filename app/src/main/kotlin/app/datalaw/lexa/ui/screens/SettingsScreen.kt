package app.datalaw.lexa.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.datalaw.lexa.BuildConfig
import app.datalaw.lexa.LexaApp
import app.datalaw.lexa.data.Manifest
import app.datalaw.lexa.data.PackInfo
import app.datalaw.lexa.data.Progress
import app.datalaw.lexa.store.ThemeMode
import app.datalaw.lexa.store.UiLang
import app.datalaw.lexa.ui.components.GhostButton
import app.datalaw.lexa.ui.components.LogoMark
import app.datalaw.lexa.ui.components.PrimaryButton
import app.datalaw.lexa.ui.components.Segmented
import app.datalaw.lexa.ui.components.rememberResource
import app.datalaw.lexa.ui.components.rememberSay
import app.datalaw.lexa.ui.components.rememberTap
import app.datalaw.lexa.ui.i18n.Str
import app.datalaw.lexa.ui.i18n.t
import app.datalaw.lexa.ui.theme.L
import app.datalaw.lexa.ui.theme.Serif
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

/** Какой подэкран настроек открыть (помним между заходами; извне можно попросить нужный). */
object SettingsNav {
    const val LOOK = 0
    const val LANG = 1
    const val DATA = 2
    const val SOURCES = 3
    const val ABOUT = 4
    var page: Int = LOOK
}

/* ───────────── Строительные блоки ───────────── */

@Composable
private fun Group(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = L.c
    Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        if (title != null) Text(title.uppercase(), color = c.ink3, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        Surface(shape = RoundedCornerShape(18.dp), color = c.surface, border = BorderStroke(1.dp, c.line)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        }
    }
}

@Composable
private fun SetRow(title: String, sub: String? = null, trailing: @Composable () -> Unit = {}) {
    val c = L.c
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = c.ink, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, color = c.ink3, fontSize = 13.sp, maxLines = if (sub.length > 40) 3 else 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

@Composable
fun LexaSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = L.c
    Switch(
        checked = checked, onCheckedChange = onChange,
        colors = SwitchDefaults.colors(checkedTrackColor = c.accent, checkedThumbColor = c.accentInk, uncheckedTrackColor = c.surface2, uncheckedThumbColor = c.ink3, uncheckedBorderColor = c.line),
    )
}

@Composable
private fun Note(text: String) {
    Text(text, color = L.c.ink3, fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp))
}

private fun mb(bytes: Long) = maxOf(1L, Math.round(bytes / 1048576.0))

/* ───────────── Подэкраны ───────────── */

@Composable
private fun PageLook() {
    val store = LexaApp.instance.store
    val s = store.settings
    Group(t(Str.setTheme)) {
        Segmented(
            listOf(ThemeMode.AUTO to t(Str.themeAuto), ThemeMode.LIGHT to t(Str.themeLight), ThemeMode.AMOLED to t(Str.themeAmoled)),
            s.theme, { v -> store.update { it.copy(theme = v) } }, fill = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ThemePreview(Modifier.weight(1f), t(Str.themeAmoledName), androidx.compose.ui.graphics.Color(0xFF000000), androidx.compose.ui.graphics.Color(0xFFD9B48A)) {
                store.update { it.copy(theme = ThemeMode.AMOLED) }
            }
            ThemePreview(Modifier.weight(1f), t(Str.themeLightName), androidx.compose.ui.graphics.Color(0xFFECDCC4), androidx.compose.ui.graphics.Color(0xFF8A5A2C)) {
                store.update { it.copy(theme = ThemeMode.LIGHT) }
            }
        }
    }
    Group(t(Str.setBehavior)) {
        SetRow(t(Str.setHaptics), t(Str.setHapticsHint)) { LexaSwitch(s.haptics) { v -> store.update { it.copy(haptics = v) } } }
        SetRow(t(Str.setOfflineHint)) { LexaSwitch(s.offlineHint) { v -> store.update { it.copy(offlineHint = v) } } }
    }
}

@Composable
private fun ThemePreview(modifier: Modifier, name: String, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(bg).border(1.dp, L.c.line, RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.fillMaxWidth(0.8f).height(8.dp).clip(CircleShape).background(fg))
        Box(Modifier.fillMaxWidth(0.55f).height(8.dp).clip(CircleShape).background(fg.copy(alpha = 0.5f)))
        Box(Modifier.fillMaxWidth(0.7f).height(8.dp).clip(CircleShape).background(fg.copy(alpha = 0.3f)))
        Text(name, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun PageLang() {
    val c = L.c
    val store = LexaApp.instance.store
    val s = store.settings
    val say = rememberSay()
    Group(t(Str.setLanguage)) {
        SetRow(t(Str.setUiLang), t(Str.setUiLangHint)) {
            Segmented(listOf(UiLang.RU to "RU", UiLang.EN to "EN"), s.uiLang, { v -> store.update { it.copy(uiLang = v) } })
        }
    }
    Group(t(Str.setPronunciation)) {
        GhostButton(t(Str.setTestIt), { say("Ciao, dizionario", "it-IT") }, Modifier.fillMaxWidth(), Icons.Rounded.VolumeUp)
        GhostButton(t(Str.setTestRu), { say("Привет, словарь", "ru-RU") }, Modifier.fillMaxWidth(), Icons.Rounded.VolumeUp)
    }
    Text(t(Str.setTtsNote), color = c.ink3, fontSize = 13.sp)
}

@Composable
private fun PageData() {
    val c = L.c
    val app = LexaApp.instance
    val store = app.store
    val scope = rememberCoroutineScope()
    val tap = rememberTap()
    val lang = store.settings.uiLang
    val nf = remember(lang) { NumberFormat.getInstance(if (lang == UiLang.RU) Locale("ru", "RU") else Locale.US) }
    val meta = rememberResource("meta") { app.dictionary.loadMeta() }.data

    var info by remember { mutableStateOf<PackInfo?>(null) }
    var remote by remember { mutableStateOf<Manifest?>(null) }
    var progress by remember { mutableStateOf<Progress?>(null) }
    var cached by remember { mutableStateOf(0) }
    var job by remember { mutableStateOf<Job?>(null) }
    var confirmRemove by remember { mutableStateOf(false) }

    suspend fun refresh() {
        info = app.pack.info()
        cached = app.pack.cacheCount()
        remote = try {
            app.pack.fetchManifest()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null // нет сети — просто не знаем размер
        }
    }
    LaunchedEffect(Unit) { refresh() }

    fun download() {
        tap(false)
        job = scope.launch {
            progress = Progress(0, 1, 0, remote?.total ?: 1)
            try {
                app.pack.install { progress = it }
                app.dictionary.reset()
                store.showToast(t(Str.toastDownloaded))
            } catch (e: CancellationException) {
                store.showToast(t(Str.toastDownloadCancelled))
            } catch (e: Exception) {
                store.showToast(t(Str.toastDownloadFailed))
            } finally {
                progress = null
                job = null
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { refresh() }
            }
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            text = { Text(t(Str.dataRemoveConfirm)) },
            confirmButton = {
                TextButton({
                    confirmRemove = false
                    scope.launch {
                        app.pack.remove()
                        app.dictionary.reset()
                        store.showToast(t(Str.toastRemovedPack))
                        refresh()
                    }
                }) { Text(t(Str.dataRemove), color = c.danger) }
            },
            dismissButton = { TextButton({ confirmRemove = false }) { Text(t(Str.cardsCancel)) } },
            containerColor = c.surface,
        )
    }

    val installed = info?.installed == true
    val hasUpdate = installed && remote != null && remote?.version != info?.version
    val size = remote?.total ?: info?.bytes
    val p = progress

    Group(t(Str.setData)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CloudOff, null, tint = c.accent, modifier = Modifier.size(20.dp))
            Text(t(if (installed) Str.dataModeOffline else Str.dataModeOnline), color = c.ink, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).padding(start = 8.dp))
            if (hasUpdate) {
                Text(t(Str.dataUpdate), color = c.accentInk, fontSize = 11.sp, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(50)).background(c.accent).padding(horizontal = 8.dp, vertical = 2.dp))
            }
        }
        Text(t(if (installed) Str.dataModeOfflineBody else Str.dataModeOnlineBody), color = c.ink2, fontSize = 14.sp)
        if (meta != null) {
            Column {
                Text(t(Str.dataInfo, "words" to nf.format(meta.words), "forms" to nf.format(meta.forms)), color = c.ink3, fontSize = 13.sp, maxLines = 1, softWrap = false)
                Text(t(Str.dataInfo2, "ru" to nf.format(meta.ruTerms), "sent" to nf.format(meta.sentences)), color = c.ink3, fontSize = 13.sp, maxLines = 1, softWrap = false)
                Text(t(Str.dataBuilt, "date" to (info?.version ?: meta.built).take(10)), color = c.ink3, fontSize = 13.sp, maxLines = 1, softWrap = false)
            }
        }
        if (p != null) {
            LinearProgressIndicator(progress = { if (p.totalBytes > 0) (p.bytes.toFloat() / p.totalBytes).coerceIn(0f, 1f) else 0f }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color = c.accent, trackColor = c.surface2)
            Text(t(Str.dataDownloading, "done" to mb(p.bytes), "total" to mb(p.totalBytes)), color = c.ink3, fontSize = 13.sp, maxLines = 1, softWrap = false)
            GhostButton(t(Str.dataCancel), { job?.cancel() }, Modifier.fillMaxWidth())
        } else if (!installed || hasUpdate) {
            PrimaryButton(
                text = if (hasUpdate) t(Str.dataUpdateAction) else if (size != null) t(Str.dataDownloadSize, "mb" to mb(size)) else t(Str.dataDownload),
                onClick = ::download, modifier = Modifier.fillMaxWidth(), icon = Icons.Rounded.CloudDownload,
                enabled = installed || remote != null,
            )
        } else {
            Text(t(Str.dataUpToDate), color = c.ink3, fontSize = 13.sp)
        }
    }
    if (installed && p == null) {
        Group { GhostButton(t(Str.dataRemove), { confirmRemove = true }, Modifier.fillMaxWidth(), Icons.Rounded.Delete, danger = true) }
    }
    if (!installed && cached > 0) {
        Group {
            SetRow(t(Str.dataCache, "n" to nf.format(cached))) {
                GhostButton(t(Str.dataCacheClear), {
                    scope.launch {
                        app.pack.clearCache()
                        store.showToast(t(Str.toastCacheCleared))
                        cached = app.pack.cacheCount()
                    }
                })
            }
        }
    }
}

@Composable
private fun PageSources() {
    val c = L.c
    Group(t(Str.aboutSources)) {
        SetRow("Викисловарь · kaikki.org", t(Str.aboutWikt))
        SetRow("wordfreq", t(Str.aboutFreq))
        SetRow("Tatoeba", t(Str.aboutTat))
    }
    Note(t(Str.aboutLicenseNote))
    Group(t(Str.aboutPrivacyTitle)) { Text(t(Str.aboutPrivacy), color = c.ink2, fontSize = 14.sp) }
}

@Composable
private fun PageAbout() {
    val c = L.c
    Column(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = RoundedCornerShape(24.dp), color = c.surface, border = BorderStroke(1.dp, c.accentLine)) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                LogoMark(60)
                Text(t(Str.aboutMadeBy).uppercase(), color = c.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, maxLines = 1, softWrap = false, modifier = Modifier.padding(top = 14.dp))
                Text("DataLaw DesignLab", fontFamily = Serif, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = c.ink, maxLines = 1, softWrap = false, modifier = Modifier.padding(top = 4.dp))
                Text(t(Str.aboutMade), color = c.ink3, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
                Text(t(Str.setVersion, "v" to BuildConfig.VERSION_NAME), color = c.accent, fontSize = 12.sp, maxLines = 1, softWrap = false, modifier = Modifier.padding(top = 12.dp).clip(RoundedCornerShape(50)).background(c.accentSoft).padding(horizontal = 12.dp, vertical = 4.dp))
            }
        }
        Text(t(Str.aboutLegal), color = c.ink3, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp))
    }
}

/* ───────────── Экран с горизонтальным пейджером ───────────── */

private class PageDef(val icon: ImageVector, val label: Str)

private val PAGES = listOf(
    PageDef(Icons.Rounded.Palette, Str.setPageLook), PageDef(Icons.Rounded.Translate, Str.setPageLang),
    PageDef(Icons.Rounded.Storage, Str.setPageData), PageDef(Icons.Rounded.Book, Str.setPageSources),
    PageDef(Icons.Rounded.Info, Str.setPageAbout),
)

@Composable
fun SettingsScreen() {
    val c = L.c
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(initialPage = SettingsNav.page.coerceIn(0, PAGES.lastIndex)) { PAGES.size }
    LaunchedEffect(pager.currentPage) { SettingsNav.page = pager.currentPage }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(t(Str.settingsTitle), style = MaterialTheme.typography.titleLarge, color = c.ink, maxLines = 1, softWrap = false)
            Text(t(Str.settingsSub), color = c.ink3, fontSize = 13.sp, maxLines = 1, softWrap = false)
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PAGES.forEachIndexed { i, p ->
                val on = i == pager.currentPage
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(if (on) c.accent else c.surface2)
                        .clickable { scope.launch { pager.animateScrollToPage(i) } }.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(p.icon, null, tint = if (on) c.accentInk else c.ink2, modifier = Modifier.size(18.dp))
                    Text(t(p.label), color = if (on) c.accentInk else c.ink2, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                when (page) {
                    SettingsNav.LOOK -> PageLook()
                    SettingsNav.LANG -> PageLang()
                    SettingsNav.DATA -> PageData()
                    SettingsNav.SOURCES -> PageSources()
                    else -> PageAbout()
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.Center) {
            for (i in PAGES.indices) {
                Box(Modifier.padding(horizontal = 3.dp).size(if (i == pager.currentPage) 8.dp else 6.dp).clip(CircleShape).background(if (i == pager.currentPage) c.accent else c.line))
            }
        }
    }
}
