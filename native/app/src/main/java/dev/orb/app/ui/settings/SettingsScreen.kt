package dev.orb.app.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.orb.app.data.DEFAULT_GEMINI_TEXT_MODEL
import dev.orb.app.data.DEFAULT_GROQ_TEXT_MODEL
import dev.orb.app.data.FileAccess
import dev.orb.app.data.GEMINI_TEXT_MODELS
import dev.orb.app.data.GROQ_TEXT_MODELS
import dev.orb.app.ui.OrbState
import dev.orb.app.ui.SettingsDraft
import dev.orb.app.ui.theme.OrbBg
import dev.orb.app.ui.theme.OrbLine
import dev.orb.app.ui.theme.OrbMuted
import dev.orb.app.ui.theme.OrbPanel
import dev.orb.app.ui.theme.OrbRed
import dev.orb.app.ui.theme.OrbText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: OrbState,
    onSave: (SettingsDraft) -> Unit,
    onRemoveGeminiKey: () -> Unit,
    onRemoveGroqKey: () -> Unit,
    onTest: () -> Unit,
    onRefreshFileAccess: () -> Unit,
    onBack: () -> Unit,
) {
    var assistant by remember(state.assistantName) { mutableStateOf(state.assistantName) }
    var user by remember(state.userName) { mutableStateOf(state.userName) }
    var language by remember(state.language) { mutableStateOf(state.language) }
    var persona by remember(state.persona) { mutableStateOf(state.persona) }
    var ttsEngine by remember(state.ttsEngine) { mutableStateOf(state.ttsEngine) }
    var voice by remember(state.voice) { mutableStateOf(state.voice) }
    var provider by remember(state.textProvider) { mutableStateOf(state.textProvider) }
    var model by remember(state.textModel) { mutableStateOf(state.textModel) }
    var geminiKey by remember { mutableStateOf("") }
    var groqKey by remember { mutableStateOf("") }
    var confirmRemoveGemini by remember { mutableStateOf(false) }
    var confirmRemoveGroq by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val filePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { onRefreshFileAccess() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onRefreshFileAccess()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val models = if (provider == "groq") GROQ_TEXT_MODELS else GEMINI_TEXT_MODELS

    Column(Modifier.fillMaxSize().background(OrbBg)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar", tint = OrbText)
            }
            Text("Ajustes", color = OrbText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            SectionTitle("Modelo de texto")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = provider == "gemini",
                    onClick = {
                        provider = "gemini"
                        if (model !in GEMINI_TEXT_MODELS) model = DEFAULT_GEMINI_TEXT_MODEL
                    },
                    label = { Text("Gemini") },
                )
                FilterChip(
                    selected = provider == "groq",
                    onClick = {
                        provider = "groq"
                        if (model !in GROQ_TEXT_MODELS) model = DEFAULT_GROQ_TEXT_MODEL
                    },
                    label = { Text("Groq") },
                )
            }
            Spacer(Modifier.height(8.dp))
            ModelPicker(
                models = models,
                selected = model,
                label = "Modelo",
                onSelect = { model = it },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (provider == "groq") {
                    "Groq responde só por texto. A voz e o live continuam no Gemini ou no TTS do aparelho."
                } else {
                    "Gemini responde texto, voz e live."
                },
                color = OrbMuted,
                fontSize = 11.sp,
            )
            TextButton(onClick = onTest, enabled = !state.busy) {
                Text("Testar modelo salvo", color = OrbRed, fontSize = 12.sp)
            }

            SectionTitle("Chaves")
            KeyStatus(configured = state.apiKeySet, label = "Gemini")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = geminiKey,
                onValueChange = { geminiKey = it },
                label = { Text("Nova chave Gemini (opcional)") },
                placeholder = { Text(if (state.apiKeySet) "deixe em branco para manter" else "AIza…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = orbFieldColors(),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Obrigatória para voz e live. Fica criptografada no aparelho e é enviada só para o Google.",
                color = OrbMuted,
                fontSize = 11.sp,
            )
            if (state.apiKeySet) {
                TextButton(onClick = { confirmRemoveGemini = true }) {
                    Text("Remover chave Gemini", color = OrbRed, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(12.dp))
            KeyStatus(configured = state.groqKeySet, label = "Groq")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = groqKey,
                onValueChange = { groqKey = it },
                label = { Text("Chave Groq (opcional)") },
                placeholder = { Text(if (state.groqKeySet) "deixe em branco para manter" else "gsk_…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = orbFieldColors(),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Só para texto. Obtenha em console.groq.com/keys. Também fica criptografada no aparelho.",
                color = OrbMuted,
                fontSize = 11.sp,
            )
            if (state.groqKeySet) {
                TextButton(onClick = { confirmRemoveGroq = true }) {
                    Text("Remover chave Groq", color = OrbRed, fontSize = 12.sp)
                }
            }

            SectionTitle("Arquivos")
            Text(
                text = if (state.fileAccess) {
                    "Acesso a todos os arquivos ativado. O Orb pode listar, buscar, renomear, mover, copiar, apagar, compactar e extrair em /sdcard."
                } else {
                    "Sem acesso. Ative para o Orb poder trabalhar com arquivos em /sdcard."
                },
                color = if (state.fileAccess) OrbMuted else OrbRed,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val opened = runCatching {
                            context.startActivity(FileAccess.settingsIntent(context))
                        }.isSuccess
                        if (!opened) {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
                                )
                            }
                        }
                    } else if (!state.fileAccess) {
                        filePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    } else {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${context.packageName}"),
                                ),
                            )
                        }
                    }
                },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = OrbRed),
            ) {
                Text(if (state.fileAccess) "Revisar acesso a arquivos" else "Permitir acesso a arquivos")
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "A pasta /sdcard/Android é bloqueada por segurança. Apagar, sobrescrever e extrair pedem confirmação.",
                color = OrbMuted,
                fontSize = 11.sp,
            )

            SectionTitle("Assistente")
            OutlinedTextField(
                value = assistant,
                onValueChange = { assistant = it },
                label = { Text("Nome do assistente") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = orbFieldColors(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = user,
                onValueChange = { user = it },
                label = { Text("Seu nome") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = orbFieldColors(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = language,
                onValueChange = { language = it },
                label = { Text("Idioma (ex.: pt-BR)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = orbFieldColors(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = persona,
                onValueChange = { persona = it },
                label = { Text("Personalidade (opcional)") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = orbFieldColors(),
            )

            SectionTitle("Voz")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = ttsEngine == "device",
                    onClick = { ttsEngine = "device" },
                    label = { Text("Voz do aparelho") },
                )
                FilterChip(
                    selected = ttsEngine == "gemini",
                    onClick = { ttsEngine = "gemini" },
                    label = { Text("Voz Gemini") },
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "A voz do aparelho funciona offline. A voz Gemini é gerada direto pelo Google e exige a chave Gemini.",
                color = OrbMuted,
                fontSize = 11.sp,
            )
            if (ttsEngine == "gemini" && state.voices.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                VoicePicker(state.voices, voice) { voice = it }
            }

            Spacer(Modifier.height(22.dp))
            Button(
                onClick = {
                    onSave(
                        SettingsDraft(
                            assistantName = assistant,
                            userName = user,
                            language = language,
                            persona = persona,
                            ttsEngine = ttsEngine,
                            voice = voice,
                            textProvider = provider,
                            textModel = model,
                            geminiKey = geminiKey,
                            groqKey = groqKey,
                        ),
                    )
                },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = OrbRed),
            ) {
                if (state.busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Salvar ajustes", fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Orb 2.2.0 · Licença Orb 1.0",
                color = OrbMuted,
                fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmRemoveGemini) {
        AlertDialog(
            onDismissRequest = { confirmRemoveGemini = false },
            title = { Text("Remover a chave Gemini?") },
            text = {
                Text("O Orb para de responder e de falar até você colar uma chave nova. A chave Gemini também é usada para voz e live.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRemoveGemini = false
                        geminiKey = ""
                        onRemoveGeminiKey()
                    },
                ) { Text("Remover", color = OrbRed) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoveGemini = false }) { Text("Cancelar") }
            },
            containerColor = OrbPanel,
        )
    }

    if (confirmRemoveGroq) {
        AlertDialog(
            onDismissRequest = { confirmRemoveGroq = false },
            title = { Text("Remover a chave Groq?") },
            text = {
                Text("Os modelos Groq deixam de funcionar. O Gemini continua respondendo por texto, voz e live.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRemoveGroq = false
                        groqKey = ""
                        onRemoveGroqKey()
                    },
                ) { Text("Remover", color = OrbRed) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoveGroq = false }) { Text("Cancelar") }
            },
            containerColor = OrbPanel,
        )
    }
}

@Composable
private fun KeyStatus(configured: Boolean, label: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = OrbPanel,
        border = BorderStroke(1.dp, OrbLine),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Key,
                contentDescription = null,
                tint = if (configured) Color(0xFF35D07F) else OrbMuted,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (configured) "$label configurada no aparelho" else "$label sem chave",
                color = OrbText,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        color = OrbRed,
        fontSize = 10.sp,
        letterSpacing = 2.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPicker(models: List<String>, selected: String, label: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected,
            onValueChange = onSelect,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = orbFieldColors(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            models.forEach { model ->
                DropdownMenuItem(
                    text = { Text(model) },
                    onClick = {
                        onSelect(model)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoicePicker(voices: List<String>, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text("Voz Gemini") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            shape = RoundedCornerShape(12.dp),
            colors = orbFieldColors(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            voices.forEach { voice ->
                DropdownMenuItem(
                    text = { Text(voice) },
                    onClick = {
                        onSelect(voice)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
internal fun orbFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = OrbRed.copy(alpha = 0.7f),
    unfocusedBorderColor = OrbLine,
    focusedLabelColor = OrbRed,
    unfocusedLabelColor = OrbMuted,
    cursorColor = OrbRed,
    focusedTextColor = OrbText,
    unfocusedTextColor = OrbText,
)
