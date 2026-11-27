package dev.orb.app.ui.memory

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.orb.app.ui.OrbState
import dev.orb.app.ui.components.EmptyHint
import dev.orb.app.ui.components.ScreenHeader
import dev.orb.app.ui.theme.OrbLine
import dev.orb.app.ui.theme.OrbMuted
import dev.orb.app.ui.theme.OrbPanel
import dev.orb.app.ui.theme.OrbRed
import dev.orb.app.ui.theme.OrbText

@Composable
fun MemoryScreen(
    state: OrbState,
    onLoad: () -> Unit,
    onDelete: (String) -> Unit,
    onClear: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { onLoad() }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Memória",
            onRefresh = onLoad,
            actions = {
                IconButton(
                    onClick = { confirmClear = true },
                    enabled = state.memory.isNotEmpty(),
                ) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = "Apagar tudo", tint = OrbMuted)
                }
            },
        )
        if (state.memoryLoading && state.memory.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = OrbRed)
            }
        } else if (state.memory.isEmpty()) {
            EmptyHint("Nada guardado ainda.\nConverse com o Orb e ele lembra do que importa.")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.memory, key = { it.id }) { entry ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = OrbPanel,
                        border = BorderStroke(1.dp, OrbLine),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = entry.category.uppercase(),
                                    color = OrbRed,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 1.sp,
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(entry.key, color = OrbText, fontSize = 14.sp)
                                Spacer(Modifier.height(2.dp))
                                Text(entry.value, color = OrbMuted, fontSize = 12.sp, lineHeight = 17.sp)
                            }
                            IconButton(onClick = { onDelete(entry.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Apagar", tint = OrbMuted)
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Apagar a memória?") },
            text = { Text("O Orb vai esquecer tudo o que guardou sobre você.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        onClear()
                    },
                ) { Text("Apagar", color = OrbRed) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancelar") }
            },
            containerColor = OrbPanel,
        )
    }
}
