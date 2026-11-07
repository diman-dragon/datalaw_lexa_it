package app.datalaw.lexa.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.datalaw.lexa.LexaApp
import app.datalaw.lexa.platform.VoiceError
import app.datalaw.lexa.platform.VoiceHandlers
import app.datalaw.lexa.platform.VoicePhase
import app.datalaw.lexa.platform.VoiceRecognizer
import app.datalaw.lexa.platform.VoiceSession
import app.datalaw.lexa.store.VoiceLangPref
import app.datalaw.lexa.ui.i18n.Str
import app.datalaw.lexa.ui.i18n.t
import app.datalaw.lexa.ui.theme.L
import app.datalaw.lexa.ui.theme.Serif

private fun errorText(e: VoiceError): Str = when (e) {
    VoiceError.PERMISSION -> Str.voiceErrPermission
    VoiceError.UNAVAILABLE -> Str.voiceErrUnavailable
    VoiceError.NETWORK -> Str.voiceErrNetwork
    VoiceError.NO_SPEECH -> Str.voiceErrNoSpeech
    VoiceError.LANG -> Str.voiceErrLang
    VoiceError.BUSY -> Str.voiceErrBusy
    VoiceError.FAILED -> Str.voiceErrFailed
}

/**
 * Окно голосового ввода: шторка снизу с «дышащей» кнопкой-микрофоном, кольцами и столбиками,
 * реагирующими на громкость голоса, и бегущей строкой распознанного слова.
 * onResult — варианты распознавания (лучший первым) и признак «говорили по-русски».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSheet(onClose: () -> Unit, onResult: (alternatives: List<String>, russian: Boolean) -> Unit) {
    val c = L.c
    val ctx = LocalContext.current
    val app = LexaApp.instance
    val tap = rememberTap()
    val lang = app.store.settings.voiceLang

    var phase by remember { mutableStateOf(VoicePhase.STARTING) }
    var partial by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<VoiceError?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var rawLevel by remember { mutableFloatStateOf(0f) }
    var session by remember { mutableStateOf<VoiceSession?>(null) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            attempt++
        } else {
            phase = VoicePhase.ERROR
            error = VoiceError.PERMISSION
        }
    }

    /* Запуск прослушивания; перезапускается при смене языка и по «Повторить». */
    DisposableEffect(lang, attempt) {
        phase = VoicePhase.STARTING
        partial = ""
        error = null
        rawLevel = 0f
        var live = true
        var s: VoiceSession? = null
        if (ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permission.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            s = VoiceRecognizer.start(
                ctx,
                VoiceRecognizer.locale(lang == VoiceLangPref.RU),
                preferOffline = true,
                h = object : VoiceHandlers {
                    override fun onPhase(phase0: VoicePhase) {
                        if (live) phase = phase0
                    }

                    override fun onLevel(level: Float) {
                        if (!live) return
                        // сглаживаем: рост быстрый, спад плавный — столбики не «дёргаются»
                        rawLevel = if (level > rawLevel) rawLevel * 0.35f + level * 0.65f else rawLevel * 0.8f + level * 0.2f
                    }

                    override fun onPartial(text: String) {
                        if (live) partial = text
                    }

                    override fun onFinal(alternatives: List<String>) {
                        if (!live) return
                        live = false
                        tap(true)
                        onResult(alternatives, lang == VoiceLangPref.RU)
                    }

                    override fun onError(error0: VoiceError) {
                        if (!live) return
                        phase = VoicePhase.ERROR
                        error = error0
                        partial = ""
                    }
                },
            )
            session = s
        }
        onDispose {
            live = false
            s?.cancel()
            session = null
        }
    }

    val level by animateFloatAsState(rawLevel, tween(90), label = "level")
    val failed = phase == VoicePhase.ERROR
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheetState, containerColor = c.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Segmented(
                    options = listOf(VoiceLangPref.IT to "Italiano", VoiceLangPref.RU to "Русский"),
                    value = lang,
                    onChange = { next -> if (next != lang) app.store.update { it.copy(voiceLang = next) } },
                )
                Spacer(Modifier.weight(1f))
                IconBtn(Icons.Rounded.Close, t(Str.voiceClose), onClose)
            }

            Text(
                t(
                    when (phase) {
                        VoicePhase.STARTING -> Str.voicePreparing
                        VoicePhase.LISTENING -> Str.voiceListening
                        VoicePhase.PROCESSING -> Str.voiceProcessing
                        VoicePhase.ERROR -> Str.voiceTitle
                    },
                ),
                color = c.ink3, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp),
            )

            Box(Modifier.fillMaxWidth().height(84.dp).padding(top = 8.dp), contentAlignment = Alignment.Center) {
                if (failed) {
                    Text(t(errorText(error ?: VoiceError.FAILED)), color = c.warm, textAlign = TextAlign.Center, fontSize = 14.sp)
                } else {
                    Text(
                        partial.ifEmpty { t(Str.voiceSayWord) },
                        color = if (partial.isEmpty()) c.ink3 else c.ink,
                        fontFamily = Serif, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                    )
                }
            }

            Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
                // кольца дышат вместе с громкостью голоса
                Box(Modifier.size(150.dp).scale(1f + level * 0.55f).clip(CircleShape).background(c.accent.copy(alpha = 0.10f + 0.12f * level)))
                Box(Modifier.size(118.dp).scale(1f + level * 0.3f).clip(CircleShape).border(2.dp, c.accentLine, CircleShape))
                Box(
                    Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(if (failed) c.warm else c.accent)
                        .clickable(enabled = phase == VoicePhase.LISTENING || failed) {
                            tap(false)
                            if (failed) attempt++ else session?.stop()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (phase == VoicePhase.STARTING || phase == VoicePhase.PROCESSING) {
                        CircularProgressIndicator(Modifier.size(34.dp), color = c.accentInk, strokeWidth = 3.dp)
                    } else {
                        Icon(
                            if (phase == VoicePhase.LISTENING) Icons.Rounded.Stop else Icons.Rounded.Mic,
                            contentDescription = t(if (failed) Str.voiceRetry else Str.voiceStop),
                            tint = c.accentInk, modifier = Modifier.size(38.dp),
                        )
                    }
                }
            }

            Row(Modifier.height(44.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                for (k in listOf(0.55f, 0.9f, 1.25f, 0.9f, 0.55f)) {
                    val h = (6f + 34f * level * k).coerceIn(6f, 44f)
                    Box(Modifier.size(width = 6.dp, height = h.dp).clip(RoundedCornerShape(3.dp)).background(c.accent.copy(alpha = if (phase == VoicePhase.LISTENING) 1f else 0.35f)))
                }
            }

            Text(
                when {
                    failed -> t(Str.voiceHintRetry)
                    phase == VoicePhase.LISTENING -> t(Str.voiceHintTap)
                    else -> " "
                },
                color = c.ink3, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

