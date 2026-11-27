package dev.orb.app.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import dev.orb.app.audio.SpeechRecognizerManager
import dev.orb.app.data.formatClock
import dev.orb.app.ui.OrbState
import dev.orb.app.ui.OrbStatus
import dev.orb.app.ui.UiMessage
import dev.orb.app.ui.components.OrbMark
import dev.orb.app.ui.theme.OrbBg
import dev.orb.app.ui.theme.OrbLine
import dev.orb.app.ui.theme.OrbMuted
import dev.orb.app.ui.theme.OrbPanel
import dev.orb.app.ui.theme.OrbPanelHigh
import dev.orb.app.ui.theme.OrbRed
import dev.orb.app.ui.theme.OrbText

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    state: OrbState,
    onSend: (String) -> Unit,
    onInput: (String) -> Unit,
    onToggleTts: () -> Unit,
    onClearHistory: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var listening by remember { mutableStateOf(false) }
    val currentOnSend by rememberUpdatedState(onSend)
    val currentOnInput by rememberUpdatedState(onInput)

    val speech = remember {
        SpeechRecognizerManager(
            context = context,
            onResult = { text ->
                listening = false
                currentOnInput(text)
                currentOnSend(text)
            },
            onError = { message ->
                listening = false
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            },
        )
    }

    DisposableEffect(Unit) {
        onDispose { speech.destroy() }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            listening = true
            speech.start(state.language)
        } else {
            Toast.makeText(context, "Permita o microfone para falar com o Orb.", Toast.LENGTH_SHORT).show()
        }
    }

    fun startListening() {
        if (!speech.isAvailable()) {
            Toast.makeText(context, "Reconhecimento de voz indisponível neste aparelho.", Toast.LENGTH_SHORT).show()
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            listening = true
            speech.start(state.language)
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(state.messages.size, state.busy) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem((state.messages.size - 1).coerceAtLeast(0))
        }
    }

    Column(Modifier.fillMaxSize().background(OrbBg)) {
        ChatTopBar(state, onToggleTts, onClearHistory, onOpenSettings)
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.messages.isEmpty() && !state.busy) {
                item { EmptyState(state.assistantName, onSend) }
            }
            items(state.messages, key = { it.id }) { message ->
                MessageRow(message)
            }
            if (state.busy) {
                item { ThinkingRow() }
            }
        }
        Composer(
            state = state,
            listening = listening,
            onInput = onInput,
            onSend = onSend,
            onMic = {
                if (listening) {
                    speech.stop()
                    listening = false
                } else {
                    startListening()
                }
            },
        )
    }
}

@Composable
private fun ChatTopBar(
    state: OrbState,
    onToggleTts: () -> Unit,
    onClearHistory: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OrbMark(size = 38.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = state.assistantName.uppercase(),
                color = OrbText,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 3.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(statusColor(state.status)))
                Spacer(Modifier.width(6.dp))
                Text(statusLabel(state), color = OrbMuted, fontSize = 11.sp)
            }
        }
        IconButton(onClick = onToggleTts) {
            Icon(
                imageVector = if (state.ttsEnabled) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                contentDescription = if (state.ttsEnabled) "Desligar voz" else "Ligar voz",
                tint = if (state.ttsEnabled) OrbRed else OrbMuted,
            )
        }
        IconButton(onClick = onClearHistory) {
            Icon(Icons.Filled.DeleteSweep, contentDescription = "Apagar conversa", tint = OrbMuted)
        }
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Filled.Settings, contentDescription = "Ajustes", tint = OrbMuted)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyState(name: String, onSend: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OrbMark(size = 140.dp)
        Spacer(Modifier.height(16.dp))
        Text("Sistemas online.", color = OrbText, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Pergunte, fale ou peça um lembrete.\n$name lembra do que importa.",
            color = OrbMuted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp,
        )
        Spacer(Modifier.height(18.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            listOf(
                "O que você faz?",
                "Previsão do tempo",
                "Notícias de hoje",
                "Me lembre de beber água às 15h",
            ).forEach { suggestion ->
                AssistChip(
                    onClick = { onSend(suggestion) },
                    label = { Text(suggestion, fontSize = 12.sp) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = OrbPanel,
                        labelColor = OrbText,
                    ),
                    border = BorderStroke(1.dp, OrbLine),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MessageRow(message: UiMessage) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.fromUser) Alignment.End else Alignment.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (message.fromUser) 18.dp else 4.dp,
                bottomEnd = if (message.fromUser) 4.dp else 18.dp,
            ),
            color = if (message.fromUser) OrbRed else OrbPanel,
            border = if (message.fromUser) null else BorderStroke(1.dp, OrbLine),
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                color = if (message.fromUser) Color.White else OrbText,
                fontSize = 15.sp,
                lineHeight = 21.sp,
            )
        }
        if (message.tools.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.widthIn(max = 320.dp),
            ) {
                message.tools.forEach { tool ->
                    Surface(shape = RoundedCornerShape(8.dp), color = OrbPanelHigh) {
                        Text(
                            text = tool.name,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = OrbMuted,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
        Text(
            text = formatClock(message.ts / 1000.0),
            color = OrbMuted.copy(alpha = 0.7f),
            fontSize = 9.sp,
            modifier = Modifier.padding(top = 3.dp, start = 4.dp, end = 4.dp),
        )
    }
}

@Composable
private fun ThinkingRow() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OrbMark(size = 26.dp)
        Spacer(Modifier.width(8.dp))
        Text("pensando…", color = OrbMuted, fontSize = 12.sp)
        Spacer(Modifier.width(8.dp))
        CircularProgressIndicator(
            modifier = Modifier.size(12.dp),
            color = OrbRed,
            strokeWidth = 1.5.dp,
        )
    }
}

@Composable
private fun Composer(
    state: OrbState,
    listening: Boolean,
    onInput: (String) -> Unit,
    onSend: (String) -> Unit,
    onMic: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .imePadding(),
        verticalAlignment = Alignment.Bottom,
    ) {
        IconButton(
            onClick = onMic,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (listening) OrbRed else OrbPanel),
        ) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = if (listening) "Parar" else "Falar",
                tint = if (listening) Color.White else OrbRed,
            )
        }
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(
            value = state.input,
            onValueChange = onInput,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Mensagem para ${state.assistantName}…", color = OrbMuted) },
            maxLines = 5,
            shape = RoundedCornerShape(22.dp),
            textStyle = LocalTextStyle.current.copy(color = OrbText),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = OrbRed.copy(alpha = 0.6f),
                unfocusedBorderColor = OrbLine,
                focusedContainerColor = OrbPanel,
                unfocusedContainerColor = OrbPanel,
                cursorColor = OrbRed,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend(state.input) }),
        )
        Spacer(Modifier.width(8.dp))
        FilledIconButton(
            onClick = { onSend(state.input) },
            enabled = state.input.isNotBlank() && !state.busy,
            modifier = Modifier.size(48.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = OrbRed,
                contentColor = Color.White,
                disabledContainerColor = OrbPanelHigh,
                disabledContentColor = OrbMuted,
            ),
        ) {
            Icon(Icons.Filled.Send, contentDescription = "Enviar")
        }
    }
}

private fun statusColor(status: OrbStatus): Color = when (status) {
    OrbStatus.Ready -> Color(0xFF35D07F)
    OrbStatus.MissingKey, OrbStatus.MissingGroqKey -> Color(0xFFFFB020)
}

private fun statusLabel(state: OrbState): String = when (state.status) {
    OrbStatus.Ready -> "pronto · ${state.textModel}"
    OrbStatus.MissingKey -> "falta a chave Gemini"
    OrbStatus.MissingGroqKey -> "falta a chave Groq"
}
