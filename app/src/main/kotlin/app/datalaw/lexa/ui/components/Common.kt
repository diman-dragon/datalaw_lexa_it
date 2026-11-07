package app.datalaw.lexa.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.datalaw.lexa.LexaApp
import app.datalaw.lexa.R
import app.datalaw.lexa.data.DataUnavailableException
import app.datalaw.lexa.ui.i18n.Str
import app.datalaw.lexa.ui.i18n.t
import app.datalaw.lexa.ui.theme.L
import kotlinx.coroutines.delay

/** Тактильный отклик с учётом настройки «Вибро-отклик». true — плотнее. */
@Composable
fun rememberTap(): (Boolean) -> Unit {
    val haptic = LocalHapticFeedback.current
    return remember(haptic) {
        { strong: Boolean ->
            if (LexaApp.instance.store.settings.haptics) {
                haptic.performHapticFeedback(if (strong) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove)
            }
            Unit
        }
    }
}

/* ───────────── Карточка-контейнер ───────────── */

@Composable
fun LexaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    tint: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = L.c
    val shape = RoundedCornerShape(20.dp)
    val inner: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(16.dp), content = content)
    }
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = tint ?: c.surface, border = BorderStroke(1.dp, c.line), content = inner)
    } else {
        Surface(modifier = modifier, shape = shape, color = tint ?: c.surface, border = BorderStroke(1.dp, c.line), content = inner)
    }
}

/* ───────────── Верхняя панель ───────────── */

@Composable
fun IconBtn(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, on: Boolean = false, size: Int = 44, tint: Color? = null) {
    val c = L.c
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(if (on) c.accentSoft else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint ?: if (on) c.accent else c.ink2, modifier = Modifier.size((size * 0.5f).dp))
    }
}

@Composable
fun Header(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) IconBtn(Icons.AutoMirrored.Rounded.ArrowBack, t(Str.back), onBack)
        Text(
            title,
            style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
            color = L.c.ink,
            modifier = Modifier.weight(1f).padding(horizontal = if (onBack != null) 4.dp else 0.dp),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
        actions()
    }
}

/* ───────────── Чипы ───────────── */

enum class ChipTone { NEUTRAL, TEAL, ACCENT }

@Composable
fun Chip(text: String, modifier: Modifier = Modifier, tone: ChipTone = ChipTone.NEUTRAL, sub: String? = null, onClick: (() -> Unit)? = null) {
    val c = L.c
    val (bg, fg, line) = when (tone) {
        ChipTone.NEUTRAL -> Triple(c.surface2, c.ink2, c.line)
        ChipTone.TEAL -> Triple(c.accentSoft, c.accent, c.accentLine)
        ChipTone.ACCENT -> Triple(c.warmSoft, c.warm, c.warm.copy(alpha = 0.4f))
    }
    val body: @Composable () -> Unit = {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (sub != null) Text(sub, color = c.ink3, fontSize = 11.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp).weight(1f, fill = false))
        }
    }
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(50), color = bg, border = BorderStroke(1.dp, line), content = body)
    } else {
        Surface(modifier = modifier, shape = RoundedCornerShape(50), color = bg, border = BorderStroke(1.dp, line), content = body)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Chips(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        content()
    }
}

/* ───────────── Переключатель вкладок ───────────── */

@Composable
fun <T> Segmented(
    options: List<Pair<T, String>>,
    value: T,
    onChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    fill: Boolean = false,
) {
    val c = L.c
    val tap = rememberTap()
    Row(
        modifier
            .let { if (fill) it.fillMaxWidth() else it.horizontalScroll(rememberScrollState()) }
            .clip(RoundedCornerShape(50))
            .background(c.surface2)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for ((v, label) in options) {
            val on = v == value
            Box(
                Modifier
                    .let { if (fill) it.weight(1f) else it }
                    .clip(RoundedCornerShape(50))
                    .background(if (on) c.accent else Color.Transparent)
                    .clickable {
                        tap(false)
                        onChange(v)
                    }
                    .padding(horizontal = if (fill) 4.dp else 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (on) c.accentInk else c.ink2, fontSize = if (fill) 13.sp else 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false, textAlign = TextAlign.Center)
            }
        }
    }
}

/* ───────────── Кнопки ───────────── */

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    val c = L.c
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.accentInk, disabledContainerColor = c.surface2, disabledContentColor = c.ink3),
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(18.dp).padding(end = 0.dp))
        if (icon != null) Spacer(Modifier.size(8.dp))
        Text(text, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, danger: Boolean = false, enabled: Boolean = true) {
    val c = L.c
    val fg = if (danger) c.danger else c.ink
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, c.line),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = fg, disabledContentColor = c.ink3),
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(18.dp))
        if (icon != null) Spacer(Modifier.size(8.dp))
        Text(text, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun LinkButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(text, color = L.c.accent, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) }
}

/* ───────────── Состояния ───────────── */

@Composable
fun EmptyState(icon: ImageVector, text: String, title: String? = null, content: @Composable ColumnScope.() -> Unit = {}) {
    val c = L.c
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(c.accentSoft), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = c.accent, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(14.dp))
        if (title != null) Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleLarge, color = c.ink, textAlign = TextAlign.Center)
        Text(text, color = c.ink3, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
        content()
    }
}

@Composable
fun ErrorBox(error: Throwable?, onRetry: () -> Unit, onSettings: (() -> Unit)? = null) {
    val offline = error is DataUnavailableException
    EmptyState(
        icon = Icons.Rounded.CloudOff,
        title = t(if (offline) Str.netError else Str.loadError),
        text = t(if (offline) Str.netErrorBody else Str.loadErrorHint),
    ) {
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton(t(Str.retry), onRetry)
            if (onSettings != null) LinkButton(t(Str.toSettings), onSettings)
        }
    }
}

@Composable
fun Skeleton(lines: Int = 3) {
    val a by rememberInfiniteTransition(label = "sk").animateFloat(
        initialValue = 0.45f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "sk-a",
    )
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (i in 0 until lines) {
            Box(
                Modifier
                    .fillMaxWidth(((92 - (i * 17) % 40) / 100f))
                    .height(16.dp)
                    .alpha(a)
                    .clip(RoundedCornerShape(8.dp))
                    .background(L.c.surface2),
            )
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), color = L.c.ink3, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = modifier.padding(top = 18.dp, bottom = 8.dp))
}

@Composable
fun SrcNote(text: String, center: Boolean = false) {
    Text(text, color = L.c.ink3, fontSize = 12.sp, textAlign = if (center) TextAlign.Center else TextAlign.Start, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
}

@Composable
fun LogoMark(size: Int = 30) {
    Image(painterResource(R.drawable.logo_mask), contentDescription = null, modifier = Modifier.size(size.dp), colorFilter = ColorFilter.tint(L.c.accent))
}

@Composable
fun Eyebrow(text: String) {
    Text(text.uppercase(), color = L.c.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, maxLines = 1, softWrap = false)
}

/** Подсветка слова (и его форм с окончаниями) в предложении. */
@Composable
fun highlighted(text: String, word: String): AnnotatedString {
    val c = L.c
    val re = remember(word) { app.datalaw.lexa.core.Text.wordFormRegex(word) }
    return buildAnnotatedString {
        var last = 0
        for (m in re.findAll(text)) {
            if (m.range.first > last) append(text.substring(last, m.range.first))
            withStyle(SpanStyle(background = c.accentSoft, color = c.accent, fontWeight = FontWeight.SemiBold)) { append(m.value) }
            last = m.range.last + 1
        }
        if (last < text.length) append(text.substring(last))
    }
}

/* ───────────── Всплывающее сообщение ───────────── */

@Composable
fun ToastHost(modifier: Modifier = Modifier) {
    val store = LexaApp.instance.store
    val toast = store.toast
    LaunchedEffect(toast?.id) {
        if (toast != null) {
            delay(2400)
            store.dismissToast(toast.id)
        }
    }
    if (toast != null) {
        val c = L.c
        Surface(modifier = modifier.padding(horizontal = 20.dp), shape = RoundedCornerShape(14.dp), color = c.ink, shadowElevation = 6.dp) {
            Text(toast.text, color = c.bg, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
        }
    }
}

