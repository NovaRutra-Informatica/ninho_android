package app.ninho.android

import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun AcquisitionScreen(result: AcquisitionResult, verify: () -> Unit) {
    val context = LocalContext.current
    val checking = result.status == AcquisitionStatus.CHECKING
    val configuration = result.status == AcquisitionStatus.CONFIGURATION_ERROR
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = 480.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(22.dp)) {
                NinhoMascot(112.dp, reduced = false)
                Text(if (checking) "Preparando seu ninho" else "Um ninho para chamar de seu", fontFamily = NinhoDisplayFont, fontSize = 32.sp, lineHeight = 38.sp, textAlign = TextAlign.Center)
                Text(when (result.status) {
                    AcquisitionStatus.CHECKING -> "Só um instante enquanto conferimos sua compra neste aparelho."
                    AcquisitionStatus.NOT_LICENSED -> "A Google Play não confirmou a compra nesta conta. Abra a loja com a conta usada para comprar o Ninho e tente novamente."
                    AcquisitionStatus.UNAVAILABLE -> "Ainda não conseguimos confirmar sua compra. Confira a conexão, a data do aparelho e se a Google Play está disponível. Seus estudos continuam guardados."
                    AcquisitionStatus.CONFIGURATION_ERROR -> "Esta versão não está pronta para confirmar compras. Instale a versão oficial do Ninho pela Google Play. Seus estudos continuam guardados."
                    else -> "Sua compra é única, sem assinatura e sem código de ativação. Confirme-a pela Google Play uma vez neste aparelho; depois, seus estudos funcionam offline."
                }, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                if (checking) CircularProgressIndicator()
                else {
                    if (!configuration) Button(onClick = verify, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Confirmar minha compra") }
                    OutlinedButton(onClick = {
                        val page = "https://play.google.com/store/apps/details?id=${context.packageName}".toUri()
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, page)) }
                    }, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Abrir na Google Play") }
                    Text("A primeira confirmação usa a conexão da Google Play. Reinstalar ou trocar de aparelho pode pedir outra confirmação. Perfil, materiais e progresso não são enviados para verificar a compra.", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
