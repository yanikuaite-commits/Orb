package dev.orb.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.orb.app.ui.OrbState
import dev.orb.app.ui.components.OrbMark
import dev.orb.app.ui.theme.OrbBg
import dev.orb.app.ui.theme.OrbMuted
import dev.orb.app.ui.theme.OrbRed
import dev.orb.app.ui.theme.OrbText

@Composable
fun SetupScreen(state: OrbState, onConnect: (String, String) -> Unit) {
    var server by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OrbBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(28.dp))
        OrbMark(size = 150.dp)
        Spacer(Modifier.height(20.dp))
        Text("Conectar ao servidor", color = OrbText, fontSize = 24.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "O Orb é o rosto; o cérebro roda no seu FastAPI + Gemini. Informe o endereço do backend para começar.",
            color = OrbMuted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = server,
            onValueChange = { server = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Endereço do servidor") },
            placeholder = { Text("https://orb.seudominio.com") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Next,
            ),
            colors = orbFieldColors(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Token (opcional)") },
            placeholder = { Text("ORB_TOKEN, se você configurou") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            colors = orbFieldColors(),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Dica: em rede local use http://192.168.0.10:8000. Na internet, use HTTPS.",
            color = OrbMuted,
            fontSize = 11.sp,
        )
        state.snackbar?.let { message ->
            Spacer(Modifier.height(12.dp))
            Text(message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { onConnect(server, token) },
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
                Text("Conectar", fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = "A chave Gemini é configurada depois, em Ajustes.",
            color = OrbMuted,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(28.dp))
    }
}
