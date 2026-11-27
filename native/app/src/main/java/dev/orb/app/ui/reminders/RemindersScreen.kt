package dev.orb.app.ui.reminders

import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.orb.app.data.formatDue
import dev.orb.app.ui.OrbState
import dev.orb.app.ui.components.EmptyHint
import dev.orb.app.ui.components.ScreenHeader
import dev.orb.app.ui.theme.OrbLine
import dev.orb.app.ui.theme.OrbMuted
import dev.orb.app.ui.theme.OrbPanel
import dev.orb.app.ui.theme.OrbRed
import dev.orb.app.ui.theme.OrbText

@Composable
fun RemindersScreen(state: OrbState, onLoad: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { onLoad() }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = "Lembretes", onRefresh = onLoad)

        if (!state.exactAlarms) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = OrbPanel,
                border = BorderStroke(1.dp, OrbRed.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("Alarmes exatos desligados", color = OrbText, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Sem essa permissão o lembrete pode atrasar. O Orb usa o alarme do Android para tocar com o app fechado.",
                        color = OrbMuted,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                        Uri.parse("package:${context.packageName}"),
                                    ),
                                )
                            }
                        },
                    ) { Text("Permitir alarmes exatos", color = OrbRed) }
                }
            }
        }

        if (state.remindersLoading && state.reminders.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = OrbRed)
            }
        } else if (state.reminders.isEmpty()) {
            EmptyHint("Nenhum lembrete ativo.\nPeça no chat: “me lembre de beber água às 15h”.")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.reminders, key = { it.id }) { reminder ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = OrbPanel,
                        border = BorderStroke(1.dp, OrbLine),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Alarm, contentDescription = null, tint = OrbRed)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(reminder.text, color = OrbText, fontSize = 14.sp)
                                Spacer(Modifier.height(3.dp))
                                Text(formatDue(reminder.dueAt), color = OrbMuted, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
