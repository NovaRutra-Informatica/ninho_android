package app.ninho.android

import android.Manifest
import android.os.Build
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun Welcome(model: NinhoModel) {
    val step = model.welcomeStep
    val draft = model.profileDraft
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(horizontal = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("ninho", Modifier.weight(1f), fontFamily = NinhoDisplayFont, fontSize = 30.sp, color = MaterialTheme.colorScheme.primary)
                Text(if (model.onboardingProcessing) "Seu plano" else "${step + 1} de ${WelcomeFlow.steps.size}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (model.onboardingProcessing) {
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    item { Row(verticalAlignment = Alignment.CenterVertically) {
                        WelcomeOwl(model.data.settings.reducedMotion)
                        Text(if (model.analysisBusy) "Estou organizando suas respostas para pensar em um caminho possível." else "Seu perfil está guardado. Vamos conhecer seu espaço?", Modifier.weight(1f).padding(start = 14.dp), fontSize = 15.sp, lineHeight = 23.sp)
                    } }
                    item { Heading(if (model.analysisBusy) "Preparando seu caminho…" else "Seu perfil está pronto.", "Seus objetivos ficam neste aparelho e orientam a análise local incluída.") }
                    item { Panel {
                        if (model.analysisBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Seu ponto de partida usa suas respostas. As sugestões vão se adaptar às sessões e questões que você registrar. Sem download, sem conta e sem conexão.", lineHeight = 23.sp)
                        model.analysis.plan.forEach { Text(it, lineHeight = 23.sp) }
                        Text("Análise local incluída: estatísticas e regras explicáveis, sem modelo de linguagem.", fontSize = 12.sp, lineHeight = 20.sp)
                        if (!model.analysisBusy && model.analysis.cards.isEmpty()) OutlinedButton(onClick = model::retryAnalysis) { Text("Preparar novamente") }
                    } }
                }
                Button(onClick = model::finishWelcome, enabled = !model.analysisBusy && !model.saving && model.analysis.cards.isNotEmpty(), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp).heightIn(min = 50.dp)) {
                    Text("Conhecer meu ninho")
                }
            } else {
                LinearProgressIndicator(progress = { (step + 1) / WelcomeFlow.steps.size.toFloat() }, modifier = Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 12.dp).height(5.dp))
                val body: @Composable (Int) -> Unit = { current ->
                    val question = WelcomeFlow.steps[current]
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                        item { Row(verticalAlignment = Alignment.CenterVertically) {
                            WelcomeOwl(model.data.settings.reducedMotion)
                            Surface(Modifier.weight(1f).padding(start = 12.dp), shape = RoundedCornerShape(topStart = 5.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                                Text(question.guide, Modifier.padding(15.dp), fontSize = 14.sp, lineHeight = 21.sp)
                            }
                        } }
                        item { Heading(question.title, if (current < 2) "Vamos por partes. Seu perfil fica salvo enquanto você responde." else "Responda no seu ritmo. Esta parte é opcional e pode ser ajustada depois.") }
                        if (current == 0) item { BackupControls(model, firstRun = true) }
                        item { ProfileQuestionFields(draft, question.questions, model::updateProfile) }
                        if (current == WelcomeFlow.lastIndex) {
                            item { Panel {
                                Text("${draft.name}, este é seu ponto de partida.", fontFamily = NinhoDisplayFont, fontSize = 23.sp)
                                Text(draft.goal, lineHeight = 23.sp)
                                if (draft.subjects.isNotBlank()) Text(draft.subjects, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${draft.dailyMinutes} min disponíveis por dia • sessões de ${draft.sessionMinutes} min", color = MaterialTheme.colorScheme.primary)
                                Text("Seu perfil será incluído por inteiro em cada análise. Você poderá editar qualquer resposta em Meu perfil.", fontSize = 13.sp, lineHeight = 21.sp)
                            } }
                            item { Panel {
                                Text("Sua assistente já está aqui", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                                Text("O Ninho já inclui uma análise local leve. Ela observa seus registros, explica os sinais que encontrou e sugere seu próximo passo. Você não precisa instalar nem importar nada.", lineHeight = 22.sp)
                            } }
                        }
                    }
                }
                Box(Modifier.weight(1f)) {
                    if (model.data.settings.reducedMotion) body(step)
                    else AnimatedContent(step, transitionSpec = {
                        val direction = if (targetState > initialState) 1 else -1
                        (slideInHorizontally(tween(250)) { direction * it / 8 } + fadeIn(tween(230)))
                            .togetherWith(slideOutHorizontally(tween(180)) { -direction * it / 8 } + fadeOut(tween(150)))
                    }, label = "Perguntas de boas-vindas") { body(it) }
                }
                if (!model.saving && model.profileUnsaved) TextButton(onClick = { model.updateProfile(draft) }) { Text("Tentar salvar meu perfil novamente") }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (step > 0) OutlinedButton(onClick = { model.moveWelcomeStep(step - 1) }, enabled = !model.aiBusy && !model.saving, shape = RoundedCornerShape(14.dp), modifier = Modifier.heightIn(min = 50.dp)) { Text("Voltar") }
                    Button(onClick = { if (step < WelcomeFlow.lastIndex) model.moveWelcomeStep(step + 1) else model.completeOnboarding() },
                        enabled = !model.aiBusy && !model.saving && !model.profileUnsaved && WelcomeFlow.canContinue(step, draft),
                        shape = RoundedCornerShape(14.dp), elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                        modifier = Modifier.weight(1f).heightIn(min = 50.dp)) {
                        Text(if (step < WelcomeFlow.lastIndex) "Continuar" else "Criar meu perfil")
                    }
                }
                Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (model.saving) "Salvando…" else "Seu progresso fica salvo.", Modifier.weight(1f), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { model.updateReducedMotion(!model.data.settings.reducedMotion) }, enabled = !model.saving) {
                        Text(if (model.data.settings.reducedMotion) "Ativar animações" else "Reduzir movimento", fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable private fun WelcomeOwl(reducedMotion: Boolean) {
    val scale = if (reducedMotion) 1f else {
        val transition = rememberInfiniteTransition(label = "Coruja de boas-vindas")
        val value by transition.animateFloat(1f, 1.06f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "Respiração")
        value
    }
    Image(painterResource(R.drawable.owl), "Coruja Ninho", Modifier.size(104.dp).graphicsLayer { scaleX = scale; scaleY = scale; translationY = -(scale - 1f) * 90; rotationZ = (scale - 1f) * 24 })
}

@Composable internal fun ProfilePage(model: NinhoModel, onAssistant: () -> Unit, onTutorial: () -> Unit) {
    val draft = model.profileDraft
    var showTutorialReset by rememberSaveable { mutableStateOf(false) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var clearUsage by rememberSaveable { mutableStateOf(false) }
    var notificationDenied by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        model.updateTimerReminder(granted); notificationDenied = !granted
    }
    fun enableReminder(enabled: Boolean) {
        if (!enabled) model.updateTimerReminder(false)
        else if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else if (model.canNotify()) model.updateTimerReminder(true)
        else notificationDenied = true
    }
    LazyColumn(Modifier.imePadding(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { Heading("Meu perfil", "Seus objetivos e sua rotina. Tudo salvo neste aparelho.") }
        item { Text(if (model.saving) "Salvando suas escolhas…" else if (model.profileUnsaved) "Há alterações que ainda não foram salvas." else "As alterações são salvas automaticamente.", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            if (!model.saving && model.profileUnsaved) TextButton(onClick = { model.updateProfile(draft) }) { Text("Tentar salvar meu perfil novamente") }
        }
        item { Button(onClick = onAssistant, shape = CircleShape) { Text("Minha assistente") } }
        listOf("Você e seu objetivo", "Seu caminho", "Sua rotina", "Suas preferências").forEachIndexed { index, title ->
            item { Panel { Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold); ProfileQuestions(draft, index, model::updateProfile) } }
        }
        item { Panel {
            Text("Do seu jeito", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text("Aparência")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to "Sistema", "light" to "Claro", "dark" to "Escuro").forEach { (value, label) ->
                    FilterChip(selected = model.data.settings.theme == value, onClick = { model.updateTheme(value) }, label = { Text(label) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Reduzir movimento", Modifier.weight(1f)); Switch(checked = model.data.settings.reducedMotion, onCheckedChange = model::updateReducedMotion) }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Aviso silencioso do temporizador", Modifier.weight(1f)); Switch(checked = model.data.settings.timerReminder, onCheckedChange = ::enableReminder) }
            Text("O Android avisa ao terminar, sem som, vibração ou interrupção de música e vídeo. Em repouso ou economia de energia, o sistema pode atrasar o aviso; o relógio salvo permanece correto.", fontSize = 12.sp, lineHeight = 20.sp)
            if (notificationDenied || model.data.settings.timerReminder && !model.canNotify()) {
                Text("O sistema está bloqueando as notificações. Você pode permitir o aviso nas configurações do Android.", fontSize = 12.sp, lineHeight = 20.sp)
                TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }) { Text("Abrir permissão de notificações") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Analisar meu uso do Ninho", Modifier.weight(1f)); Switch(checked = model.data.settings.appUsage, onCheckedChange = model::updateUsage) }
            Text("Guarda visitas e tempo ativo por tela por até 90 dias, apenas neste app e até 60 segundos após uma interação. Não conta como estudo e não observa outros aplicativos. Desativar interrompe os novos registros.", fontSize = 12.sp, lineHeight = 20.sp)
            TextButton(onClick = { clearUsage = true }) { Text("Apagar registros de navegação") }
            TextButton(onClick = onTutorial) { Text("Ver tutorial desta tela") }
            TextButton(onClick = { model.resetTutorials(); showTutorialReset = true }) { Text("Reiniciar tutoriais de todas as telas") }
            if (showTutorialReset) Text("Pronto. Ao visitar cada tela novamente, você verá sua explicação.", fontSize = 12.sp)
        } }
        item { WidgetOptions() }
        item { BackupControls(model) }
        item { Panel {
            TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Fechar opções avançadas" else "Opções avançadas") }
            if (advanced) AdvancedModelOptions(model)
        } }
        item { Text("O Ninho funciona offline. Desinstalar remove os dados e as cópias internas; para recuperá-los, mantenha um backup exportado ou o backup do Android disponível. Modelos e anexos não fazem parte dessas cópias.", fontSize = 12.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    if (clearUsage) AlertDialog(onDismissRequest = { clearUsage = false }, title = { Text("Apagar a navegação registrada?") }, text = { Text("Remove visitas, tempo ativo por tela e contagem de pausas. Seu perfil, sessões estudadas e questões permanecem salvos.") }, confirmButton = { TextButton(onClick = { model.clearUsage(); clearUsage = false }) { Text("Apagar registros") } }, dismissButton = { TextButton(onClick = { clearUsage = false }) { Text("Manter") } })
}

@Composable private fun ProfileQuestions(profile: StudentProfile, step: Int, onChange: (StudentProfile) -> Unit) {
    val questions = when (step) {
        0 -> listOf(ProfileQuestion.NAME, ProfileQuestion.GOAL)
        1 -> listOf(ProfileQuestion.MOTIVATION, ProfileQuestion.TARGET_DATE, ProfileQuestion.SUBJECTS, ProfileQuestion.LEVEL)
        2 -> listOf(ProfileQuestion.ROUTINE, ProfileQuestion.DAYS, ProfileQuestion.TIME, ProfileQuestion.DAILY_MINUTES, ProfileQuestion.SESSION_MINUTES)
        else -> listOf(ProfileQuestion.CHALLENGES, ProfileQuestion.PREFERENCES, ProfileQuestion.ACCESSIBILITY)
    }
    ProfileQuestionFields(profile, questions, onChange)
}

@Composable private fun ProfileQuestionFields(profile: StudentProfile, questions: List<ProfileQuestion>, onChange: (StudentProfile) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        questions.forEach { question ->
            key(question) {
                when (question) {
                    ProfileQuestion.NAME -> ProfileField("Seu nome ou apelido", profile.name, 100, "Como você gosta de ser chamado?") { onChange(profile.copy(name = it)) }
                    ProfileQuestion.GOAL -> ProfileField("Seu principal objetivo", profile.goal, 400, "Ex.: passar num concurso, concluir um curso, aprender uma profissão.") { onChange(profile.copy(goal = it)) }
                    ProfileQuestion.MOTIVATION -> ProfileField("Por que esse objetivo importa para você?", profile.motivation, 300) { onChange(profile.copy(motivation = it)) }
                    ProfileQuestion.TARGET_DATE -> ProfileField("Há uma prova ou um prazo?", profile.targetDate, 40, "Ex.: 15/12/2026, daqui a seis meses ou sem prazo.") { onChange(profile.copy(targetDate = it)) }
                    ProfileQuestion.SUBJECTS -> ProfileField("O que você precisa ou quer estudar?", profile.subjects, 300) { onChange(profile.copy(subjects = it)) }
                    ProfileQuestion.LEVEL -> ProfileField("Como você descreveria seu ponto de partida?", profile.level, 160, "Ex.: começando, retomando, já domino a base.") { onChange(profile.copy(level = it)) }
                    ProfileQuestion.ROUTINE -> ProfileField("Como é sua rotina?", profile.routine, 400, "Conte os compromissos e o que costuma limitar seu tempo.") { onChange(profile.copy(routine = it)) }
                    ProfileQuestion.DAYS -> {
                        Text("Em quais dias você costuma ter tempo?", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StudentProfile.DAYS.zip(listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom")).forEach { (day, label) ->
                                FilterChip(selected = day in profile.availableDays, shape = RoundedCornerShape(12.dp), onClick = { onChange(profile.copy(availableDays = if (day in profile.availableDays) profile.availableDays - day else profile.availableDays + day)) }, label = { Text(label) })
                            }
                        }
                    }
                    ProfileQuestion.TIME -> ProfileField("Qual horário funciona melhor?", profile.preferredTime, 160, "Ex.: manhã, depois do trabalho, intervalos variados.") { onChange(profile.copy(preferredTime = it)) }
                    ProfileQuestion.DAILY_MINUTES -> MinuteField("Tempo de estudo por dia", profile.dailyMinutes, 1440) { onChange(profile.copy(dailyMinutes = it)) }
                    ProfileQuestion.SESSION_MINUTES -> MinuteField("Duração confortável de cada sessão", profile.sessionMinutes, 240) { onChange(profile.copy(sessionMinutes = it)) }
                    ProfileQuestion.CHALLENGES -> ProfileField("O que costuma dificultar seus estudos?", profile.challenges, 400, "Ex.: manter uma rotina, lembrar depois, exercícios difíceis.") { onChange(profile.copy(challenges = it)) }
                    ProfileQuestion.PREFERENCES -> ProfileField("Quais atividades ajudam você a aprender?", profile.preferences, 300, "Ex.: questões, leitura, vídeos, explicar com suas palavras.") { onChange(profile.copy(preferences = it)) }
                    ProfileQuestion.ACCESSIBILITY -> ProfileField("Que adaptações deixariam o estudo mais confortável?", profile.accessibility, 240, "Opcional. Descreva preferências práticas; não é preciso informar diagnósticos.") { onChange(profile.copy(accessibility = it)) }
                }
            }
        }
    }
}

@Composable private fun ProfileField(label: String, value: String, limit: Int, hint: String = "", onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp)
        OutlinedTextField(value, { onChange(it.take(limit)) }, Modifier.fillMaxWidth().semantics { contentDescription = label },
            placeholder = if (hint.isNotBlank()) ({ Text(hint, fontSize = 14.sp, lineHeight = 21.sp) }) else null,
            supportingText = { Text("${value.length}/$limit", fontSize = 10.sp) },
            minLines = if (limit > 100) 3 else 1, maxLines = 6, shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surface, focusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant))
    }
}

@Composable private fun MinuteField(label: String, value: Int, maximum: Int, onChange: (Int) -> Unit) {
    var text by rememberSaveable { mutableStateOf(value.toString()) }
    val valid = text.toIntOrNull()?.let { it in 1..maximum } == true
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp)
        OutlinedTextField(text, { input ->
            text = input.filter(Char::isDigit).take(4)
            text.toIntOrNull()?.takeIf { it in 1..maximum }?.let(onChange)
        }, Modifier.fillMaxWidth().semantics { contentDescription = label }, suffix = { Text("min") },
            supportingText = { Text(if (valid) "De 1 a $maximum minutos." else "Digite de 1 a $maximum. O valor salvo continua sendo $value min.", fontSize = 11.sp) },
            isError = !valid, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surface, focusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant))
    }
}

@Composable internal fun TutorialDialog(route: String, onDone: () -> Unit) {
    val steps = ScreenTutorials.steps(route)
    var index by rememberSaveable(route) { mutableIntStateOf(0) }
    AlertDialog(onDismissRequest = onDone, icon = { Image(painterResource(R.drawable.owl), null, Modifier.size(64.dp)) },
        title = { Text(steps[index].title, fontFamily = NinhoDisplayFont) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            LinearProgressIndicator(progress = { (index + 1) / steps.size.toFloat() }, modifier = Modifier.fillMaxWidth())
            Text(steps[index].description, lineHeight = 23.sp)
            Text("${index + 1} de ${steps.size} • Você pode rever pelo botão Ajuda.", fontSize = 12.sp)
        } },
        confirmButton = { TextButton(onClick = { if (index < steps.lastIndex) index++ else onDone() }) { Text(if (index < steps.lastIndex) "Próximo" else "Vamos lá") } },
        dismissButton = { TextButton(onClick = { if (index > 0) index-- else onDone() }) { Text(if (index > 0) "Voltar" else "Ver depois") } })
}
