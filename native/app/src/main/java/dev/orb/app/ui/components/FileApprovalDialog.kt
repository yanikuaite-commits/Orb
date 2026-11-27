package dev.orb.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.orb.app.agent.FileApproval
import dev.orb.app.ui.theme.OrbMuted
import dev.orb.app.ui.theme.OrbRed
import dev.orb.app.ui.theme.OrbText

@Composable
fun FileApprovalDialog(
    approval: FileApproval,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDeny,
        containerColor = dev.orb.app.ui.theme.OrbPanel,
        title = {
            Text(
                text = approval.title,
                color = OrbText,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column {
                Text(
                    text = approval.detail,
                    color = OrbText,
                    fontSize = 13.sp,
                )
                if (approval.paths.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = approval.paths.take(6).joinToString("\n"),
                        color = OrbMuted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Risco: ${approval.risk}. O Orb só executa depois da sua autorização.",
                    color = if (approval.risk == "ALTO") OrbRed else OrbMuted,
                    fontSize = 11.sp,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onApprove,
                colors = ButtonDefaults.buttonColors(containerColor = OrbRed),
            ) {
                Text("Permitir")
            }
        },
        dismissButton = {
            TextButton(onClick = onDeny) {
                Text("Cancelar", color = OrbMuted)
            }
        },
    )
}
