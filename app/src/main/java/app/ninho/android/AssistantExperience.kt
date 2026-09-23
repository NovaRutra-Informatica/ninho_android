package app.ninho.android

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun insightTab(route: String) = when (route) { "studies" -> 1; "focus" -> 2; "reviews" -> 3; "profile" -> 5; else -> 4 }

@Composable internal fun InsightCard(insight: LocalInsight, onClick: () -> Unit) {
    Panel {
        Text(insight.title, fontFamily = NinhoDisplayFont, fontSize = 23.sp, lineHeight = 29.sp)
        Text(insight.explanation, lineHeight = 23.sp)
        Text(insight.evidence, fontSize = 12.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = onClick) { Text(insight.action) }
    }
}

@Composable internal fun AssistantPage(model: NinhoModel, onInsight: (LocalInsight) -> Unit) {
    val analysis = model.analysis
    var models by rememberSaveable { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { Heading("Minha assistente", "Pequenas pistas para cuidar do seu próximo passo.") }
        item { Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.owl), "Coruja Ninho", Modifier.size(88.dp))
                Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Eu sou a Íris", fontFamily = NinhoDisplayFont, fontSize = 25.sp)
                    Text("Assistente do Ninho", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Text("Suas sessões, respostas e avaliações viram sugestões automaticamente. Seu perfil orienta o ponto de partida. Tudo funciona offline, desde a instalação.", lineHeight = 23.sp)
            Text("Em uso: Ninho LocalAnalytics • motor estatístico incluído. Não é um modelo de linguagem.", fontSize = 13.sp, lineHeight = 21.sp, color = MaterialTheme.colorScheme.primary)
            Text(if (model.modelName == null) "Modelo de linguagem: nenhum importado." else "Modelo de linguagem opcional: ${model.modelName}. Executa somente quando você pede um plano.", fontSize = 12.sp, lineHeight = 20.sp)
            OutlinedButton(onClick = { models = !models }) { Text(if (models) "Fechar modelos e importação" else "Modelos e importação") }
            if (model.analysisBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (analysis.at > 0) Text("Atualizado às ${SimpleDateFormat("HH:mm", Locale.forLanguageTag("pt-BR")).format(Date(analysis.at))}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        if (models) item { Panel { AdvancedModelOptions(model) } }
        items(analysis.cards, key = { it.id }) { insight -> InsightCard(insight) { onInsight(insight) } }
        item { Panel {
            Text("Um caminho possível", fontFamily = NinhoDisplayFont, fontSize = 25.sp)
            analysis.plan.forEachIndexed { index, step -> Text("${index + 1}. $step", lineHeight = 23.sp) }
            Text("Ajuste objetivos, rotina e preferências no Perfil. As sugestões acompanham suas alterações.", fontSize = 12.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        item { Panel {
            Text("Como cheguei até aqui", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text("Este é um motor estatístico pequeno incluído no aplicativo, sem modelo de linguagem. A estimativa de acertos usa (acertos + 2) ÷ (tentativas + 4), evitando conclusões fortes com poucas respostas. Dificuldades só aparecem a partir de 5 tentativas; horários recorrentes exigem 6 sessões e pelo menos 3 dias distintos na mesma faixa.", lineHeight = 22.sp, fontSize = 13.sp)
            Text("Revisões usam suas avaliações (1, 3 ou 7 dias) e questões erradas. As sugestões explicam suas evidências. Esses sinais não medem inteligência, produtividade ou atenção.", lineHeight = 22.sp, fontSize = 13.sp)
        } }
        item { Panel {
            Text("Seu uso do Ninho", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            val records = UsageLedger.prune(model.data.usage, System.currentTimeMillis())
            Text("${records.sumOf { it.activeSeconds } / 60} min de navegação ativa • ${records.sumOf { it.visits }} visitas a telas nos últimos 90 dias", lineHeight = 23.sp)
            Text("Esse tempo não conta como estudo. Só observamos esta janela enquanto está em primeiro plano e até 60 segundos após uma interação. Não acessamos outros aplicativos, notificações, câmera ou microfone. Você pode desativar e apagar esses registros no Perfil.", fontSize = 12.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            records.groupBy { it.route }.forEach { (route, days) ->
                val label = when (route) { "today" -> "Hoje"; "studies" -> "Estudos"; "focus" -> "Foco"; "reviews" -> "Revisões"; "assistant" -> "Assistente"; else -> "Perfil" }
                Text("$label: ${days.sumOf { it.activeSeconds } / 60} min • ${days.sumOf { it.visits }} visitas", fontSize = 12.sp)
            }
        } }
    }
}

@Composable internal fun AdvancedModelOptions(model: NinhoModel) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(model::importModel) }
    var remove by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Modelo de linguagem opcional", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        Text("A Íris já acompanha seus estudos com o motor incluído. Um modelo de linguagem é opcional e serve para gerar um plano em texto; ele não substitui as evidências das sugestões.", fontSize = 13.sp, lineHeight = 22.sp)
        Text("Motor opcional: Google LiteRT-LM 0.17.1 • CPU. Formato: .litertlm de texto compatível com esse runtime e backend.", fontSize = 13.sp, lineHeight = 22.sp)
        Text("1. Obtenha um modelo .litertlm de texto de uma fonte confiável, confira sua licença e as instruções do autor para Android/CPU.\n2. Salve o arquivo no celular (por exemplo, em Downloads).\n3. Toque em Importar e selecione o arquivo. O Ninho copia o modelo para seu armazenamento privado.\n4. Toque em Gerar plano para testar. Só a primeira execução confirma a compatibilidade.", fontSize = 13.sp, lineHeight = 22.sp)
        Text("Arquivos .gguf do LM Studio, .task, .tflite avulsos e ZIP não são aceitos. Renomear a extensão não converte um modelo. Limite: 3 GiB por arquivo; mantenha espaço para a cópia e memória livre. O tamanho do arquivo não equivale à RAM necessária. Modelos menores/quantizados preparados para celular tendem a exigir menos memória; confirme os requisitos do autor.", fontSize = 12.sp, lineHeight = 21.sp)
        Text("Não há download automático. A geração consome mais memória e só executa com o app aberto; sair cancela a solicitação. O Ninho não envia o arquivo nem seu perfil para a internet.", fontSize = 12.sp, lineHeight = 21.sp)
        Text(model.modelName?.let { "Arquivo: $it • ${model.modelBytes / (1024 * 1024)} MiB" } ?: "Nenhum modelo importado", color = MaterialTheme.colorScheme.primary)
        Text(model.aiStatus, fontSize = 12.sp, lineHeight = 20.sp)
        if (model.aiBusy) { LinearProgressIndicator(Modifier.fillMaxWidth()); OutlinedButton(onClick = model::cancelAi) { Text("Cancelar") } }
        else {
            OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text(if (model.modelName == null) "Importar modelo opcional" else "Trocar modelo opcional") }
            if (model.modelName != null) {
                OutlinedButton(onClick = { model.generate(plan = true) }) { Text("Gerar plano com este modelo") }
                TextButton(onClick = { remove = true }) { Text("Remover modelo") }
            }
        }
        val profile = model.data.profile
        if (profile.plan.isNotBlank()) {
            if (profile.planProfileRevision != profile.revision) Text("Seu perfil mudou. Este texto usa as respostas anteriores.", color = MaterialTheme.colorScheme.error)
            Text(profile.plan, lineHeight = 24.sp)
            Text("Texto gerado pelo modelo opcional. Pode conter erros; não altera seus objetivos nem as evidências da análise incluída.", fontSize = 12.sp, lineHeight = 20.sp)
        }
    }
    if (remove) AlertDialog(onDismissRequest = { remove = false }, title = { Text("Remover o modelo opcional?") }, text = { Text("Libera o espaço do modelo. Seu perfil e seus estudos permanecem salvos.") }, confirmButton = { TextButton(onClick = { model.removeModel(); remove = false }) { Text("Remover") } }, dismissButton = { TextButton(onClick = { remove = false }) { Text("Manter") } })
}
