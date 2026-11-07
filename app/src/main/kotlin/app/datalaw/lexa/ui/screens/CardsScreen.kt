package app.datalaw.lexa.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.datalaw.lexa.LexaApp
import app.datalaw.lexa.cards.CardItem
import app.datalaw.lexa.cards.CardStatus
import app.datalaw.lexa.cards.CardsViewModel
import app.datalaw.lexa.cards.WordListParser
import app.datalaw.lexa.platform.Share
import app.datalaw.lexa.ui.components.Chip
import app.datalaw.lexa.ui.components.ChipTone
import app.datalaw.lexa.ui.components.Chips
import app.datalaw.lexa.ui.components.EmptyState
import app.datalaw.lexa.ui.components.GhostButton
import app.datalaw.lexa.ui.components.Header
import app.datalaw.lexa.ui.components.IconBtn
import app.datalaw.lexa.ui.components.LexaCard
import app.datalaw.lexa.ui.components.LinkButton
import app.datalaw.lexa.ui.components.PrimaryButton
import app.datalaw.lexa.ui.components.Segmented
import app.datalaw.lexa.ui.components.SrcNote
import app.datalaw.lexa.ui.components.rememberTap
import app.datalaw.lexa.ui.i18n.Str
import app.datalaw.lexa.ui.i18n.t
import app.datalaw.lexa.ui.theme.L
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = L.c.surface, unfocusedContainerColor = L.c.surface,
    focusedBorderColor = L.c.accent, unfocusedBorderColor = L.c.line,
    focusedTextColor = L.c.ink, unfocusedTextColor = L.c.ink, cursorColor = L.c.accent,
    focusedLabelColor = L.c.accent, unfocusedLabelColor = L.c.ink3,
    focusedPlaceholderColor = L.c.ink3, unfocusedPlaceholderColor = L.c.ink3,
)

/* ───────────── Диалог правки карточки ───────────── */

@Composable
private fun EditDialog(item: CardItem, vm: CardsViewModel, onClose: () -> Unit) {
    val c = L.c
    var front by remember { mutableStateOf(item.front) }
    var back by remember { mutableStateOf(item.back) }
    var ex by remember { mutableStateOf(item.example) }
    var exTr by remember { mutableStateOf(item.exampleTranslation) }
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = c.surface,
        title = { Text(t(Str.cardsEdit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(front, { front = it }, label = { Text(t(Str.cardsFront)) }, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                OutlinedTextField(back, { back = it }, label = { Text(t(Str.cardsBack)) }, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                OutlinedTextField(ex, { ex = it }, label = { Text(t(Str.cardsExample)) }, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                OutlinedTextField(exTr, { exTr = it }, label = { Text(t(Str.cardsExampleTr)) }, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
            }
        },
        confirmButton = {
            TextButton({
                vm.edit(item.id, front, back, ex, exTr)
                onClose()
            }) { Text(t(Str.cardsSave), color = c.accent) }
        },
        dismissButton = { TextButton(onClose) { Text(t(Str.cardsCancel), color = c.ink2) } },
    )
}

/* ───────────── Строка списка ───────────── */

@Composable
private fun CardRow(item: CardItem, vm: CardsViewModel, onEdit: () -> Unit) {
    val c = L.c
    val tap = rememberTap()
    val statusText: String? = when (item.status) {
        CardStatus.QUEUED -> t(Str.cardsStatusQueued)
        CardStatus.NOT_FOUND -> t(Str.cardsStatusNotFound)
        CardStatus.NO_TRANSLATION -> t(Str.cardsStatusNoTr)
        CardStatus.DUPLICATE -> t(Str.cardsStatusDup)
        CardStatus.ERROR -> t(Str.cardsStatusError)
        else -> null
    }
    val bad = item.needsAttention

    LexaCard(Modifier.padding(vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(if (item.hasData) item.front else item.input, color = c.ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                if (item.via != null) Text(t(Str.cardsFormOf, "w" to item.via), color = c.ink3, fontSize = 12.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                if (statusText != null) Text(statusText, color = if (bad) c.warm else c.ink3, fontSize = 13.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
            when (item.status) {
                CardStatus.WORKING -> CircularProgressIndicator(Modifier.size(22.dp), color = c.accent, strokeWidth = 2.5.dp)
                CardStatus.READY -> Icon(Icons.Rounded.Check, null, tint = c.accent, modifier = Modifier.size(22.dp))
                else -> Unit
            }
        }

        if (item.hasData || item.status == CardStatus.NO_TRANSLATION) {
            if (item.back.isNotEmpty()) Text(item.back, color = c.accent, fontSize = 16.sp, modifier = Modifier.padding(top = 6.dp))
            if (item.example.isNotEmpty()) {
                Text(item.example, color = c.ink2, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                if (item.exampleTranslation.isNotEmpty()) Text(item.exampleTranslation, color = c.ink3, fontSize = 13.sp)
                else Text(t(Str.cardsNoExampleTr), color = c.warm, fontSize = 12.sp)
            } else if (item.hasData) {
                Text(t(Str.cardsNoExample), color = c.warm, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }

        if (item.suggestions.isNotEmpty()) {
            Text(t(if (item.suggestionsAreTranslations) Str.cardsItalianFor else Str.cardsDidYouMean), color = c.ink3, fontSize = 12.sp, maxLines = 1, softWrap = false, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
            Chips { for (s in item.suggestions) Chip(s, tone = ChipTone.TEAL, onClick = { tap(false); vm.replaceWord(item.id, s) }) }
        }

        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (item.alternatives.size > 1) {
                TextButton({ tap(false); vm.nextExample(item.id) }) { Text(t(Str.cardsAnotherExample), color = c.accent, fontSize = 13.sp, maxLines = 1, softWrap = false) }
            }
            if (item.status == CardStatus.ERROR) IconBtn(Icons.Rounded.Refresh, t(Str.cardsRetryFailed), { vm.translate() }, size = 40)
            if (item.status != CardStatus.WORKING && item.status != CardStatus.QUEUED) IconBtn(Icons.Rounded.Edit, t(Str.cardsEdit), onEdit, size = 40)
            IconBtn(Icons.Rounded.Delete, t(Str.cardsRemove), { vm.remove(item.id) }, size = 40)
        }
    }
}

/* ───────────── Экран ───────────── */

@Composable
fun CardsScreen() {
    val c = L.c
    val app = LexaApp.instance
    val store = app.store
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val tap = rememberTap()
    val vm: CardsViewModel = viewModel()
    val s = store.settings

    var input by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<CardItem?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var pendingJson by remember { mutableStateOf<String?>(null) }

    fun addFromText(text: String) {
        val words = WordListParser.parseText(text)
        if (words.isEmpty()) return
        val n = vm.addWords(words)
        store.showToast(t(Str.cardsAddedN, "n" to n))
        input = ""
    }

    // список слов из файла
    val pickList = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val words = withContext(Dispatchers.IO) {
                    WordListParser.parseFile(Share.fileName(ctx, uri), Share.readText(ctx, uri))
                }
                if (words.isEmpty()) store.showToast(t(Str.cardsFileEmpty))
                else store.showToast(t(Str.cardsAddedN, "n" to vm.addWords(words)))
            } catch (e: Exception) {
                store.showToast(t(Str.cardsLoadFailed))
            }
        }
    }

    // существующая колода
    val pickBase = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val text = withContext(Dispatchers.IO) { Share.readText(ctx, uri) }
                if (vm.loadBase(text)) {
                    val b = vm.base
                    if (b != null) store.showToast(t(Str.cardsBaseLoaded, "name" to b.name, "n" to b.cardCount))
                } else {
                    store.showToast(t(Str.cardsBaseBad))
                }
            } catch (e: Exception) {
                store.showToast(t(Str.cardsLoadFailed))
            }
        }
    }

    // сохранение результата в файл
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val json = pendingJson
        pendingJson = null
        if (uri == null || json == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                withContext(Dispatchers.IO) { Share.writeText(ctx, uri, json) }
                store.showToast(t(Str.cardsSavedOk))
            } catch (e: Exception) {
                store.showToast(t(Str.cardsSaveFailed))
            }
        }
    }

    editing?.let { item -> EditDialog(item, vm) { editing = null } }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = c.surface,
            text = { Text(t(Str.cardsClearConfirm)) },
            confirmButton = { TextButton({ vm.clear(); confirmClear = false }) { Text(t(Str.cardsClear), color = c.danger) } },
            dismissButton = { TextButton({ confirmClear = false }) { Text(t(Str.cardsCancel), color = c.ink2) } },
        )
    }

    val ready = vm.readyCount
    val bad = vm.attentionCount
    val defaultName = t(Str.cardsDeckDefault)

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
        item {
            Header(t(Str.cardsTitle))
            Text(t(Str.cardsSub), color = c.ink3, modifier = Modifier.padding(bottom = 12.dp))
        }

        /* Ввод слов */
        item {
            LexaCard {
                OutlinedTextField(
                    value = input, onValueChange = { input = it }, modifier = Modifier.fillMaxWidth(), minLines = 1, maxLines = 5,
                    placeholder = { Text(t(Str.cardsInputHint)) }, shape = RoundedCornerShape(14.dp), colors = fieldColors(),
                )
                Text(t(Str.cardsInputNote), color = c.ink3, fontSize = 12.sp, maxLines = 2, modifier = Modifier.padding(top = 6.dp, bottom = 10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton(t(Str.cardsAdd), { tap(false); addFromText(input) }, Modifier.weight(1f), Icons.Rounded.Add, enabled = input.isNotBlank())
                    GhostButton(t(Str.cardsLoadFile), { pickList.launch(arrayOf("text/*", "application/json", "*/*")) }, Modifier.weight(1f), Icons.Rounded.UploadFile)
                }
            }
        }

        /* Колода и параметры */
        item {
            Spacer(Modifier.height(10.dp))
            LexaCard {
                OutlinedTextField(
                    value = vm.deckName, onValueChange = { vm.deckName = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(t(Str.cardsDeckName)) }, placeholder = { Text(vm.base?.name?.ifEmpty { null } ?: defaultName) },
                    shape = RoundedCornerShape(14.dp), colors = fieldColors(),
                )
                Spacer(Modifier.height(10.dp))
                val base = vm.base
                if (base == null) {
                    GhostButton(t(Str.cardsBaseDeck), { pickBase.launch(arrayOf("application/json", "text/*", "*/*")) }, Modifier.fillMaxWidth(), Icons.Rounded.FolderOpen)
                    Text(t(Str.cardsBaseHint), color = c.ink3, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.FolderOpen, null, tint = c.accent, modifier = Modifier.size(20.dp))
                        Text(t(Str.cardsBaseLoaded, "name" to base.name, "n" to base.cardCount), color = c.ink, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                        LinkButton(t(Str.cardsBaseClear)) { vm.detachBase() }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t(Str.cardsMaxTr), color = c.ink, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f))
                    Segmented((1..4).map { it to it.toString() }, s.cardsMaxTr.coerceIn(1, 4), { v -> store.update { it.copy(cardsMaxTr = v) } })
                }
            }
        }

        /* Перевод */
        item {
            Spacer(Modifier.height(12.dp))
            if (vm.running) {
                Text(t(Str.cardsWorking, "done" to vm.done, "total" to vm.total), color = c.ink2, maxLines = 1, softWrap = false, modifier = Modifier.padding(bottom = 6.dp))
                LinearProgressIndicator(
                    progress = { if (vm.total > 0) vm.done.toFloat() / vm.total else 0f },
                    modifier = Modifier.fillMaxWidth().height(6.dp), color = c.accent, trackColor = c.surface2,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val queued = vm.queuedCount
                    PrimaryButton(
                        if (queued > 0) "${t(Str.cardsTranslate)} ($queued)" else t(Str.cardsTranslate),
                        { tap(true); vm.translate() }, Modifier.weight(1f), Icons.Rounded.Translate, enabled = queued > 0,
                    )
                    if (vm.items.isNotEmpty()) GhostButton(t(Str.cardsClear), { confirmClear = true }, Modifier, Icons.Rounded.Delete)
                }
            }
            if (vm.items.isNotEmpty()) {
                Text(t(Str.cardsSummary, "ready" to ready, "bad" to bad), color = c.ink3, fontSize = 13.sp, maxLines = 1, softWrap = false, modifier = Modifier.padding(top = 10.dp))
            }
        }

        if (vm.items.isEmpty()) {
            item { EmptyState(Icons.Rounded.Translate, t(Str.cardsEmpty)) }
        } else {
            items(vm.items, key = { it.id }) { item -> CardRow(item, vm) { editing = item } }
        }

        /* Экспорт */
        if (ready > 0) {
            item {
                Spacer(Modifier.height(12.dp))
                LexaCard(tint = c.accentSoft) {
                    Text(t(Str.cardsExportTitle), style = MaterialTheme.typography.titleMedium, color = c.ink)
                    Text(t(Str.cardsExportN, "n" to ready), color = c.ink2, maxLines = 1, softWrap = false, modifier = Modifier.padding(top = 2.dp, bottom = if (vm.base != null) 0.dp else 10.dp))
                    if (vm.base != null) Text(t(Str.cardsExportNew), color = c.ink3, fontSize = 13.sp, maxLines = 1, softWrap = false, modifier = Modifier.padding(bottom = 10.dp))
                    PrimaryButton(t(Str.cardsSaveFile), {
                        tap(true)
                        pendingJson = vm.exportJson(defaultName)
                        saveFile.launch(vm.exportName(defaultName).replace(Regex("[\\\\/:*?\"<>|]"), "_") + ".json")
                    }, Modifier.fillMaxWidth(), Icons.Rounded.Save)
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GhostButton(t(Str.cardsCopy), {
                            Share.copy(ctx, "deck", vm.exportJson(defaultName))
                            store.showToast(t(Str.toastCopied))
                        }, Modifier.weight(1f), Icons.Rounded.ContentCopy)
                        GhostButton(t(Str.cardsShare), { Share.text(ctx, vm.exportName(defaultName), vm.exportJson(defaultName)) }, Modifier.weight(1f), Icons.Rounded.Share)
                    }
                }
                SrcNote(t(Str.cardsSourceNote))
            }
        }
    }
}
