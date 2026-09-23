package app.ninho.android

import android.os.Bundle
import android.content.Intent
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var model: NinhoModel
    private var resumed = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        model = ViewModelProvider(this)[NinhoModel::class.java]
        handleNotification(intent)
        setContent { NinhoApp(model) }
    }
    override fun onResume() { super.onResume(); resumed = true; model.setForeground(hasWindowFocus()) }
    override fun onPause() { model.setForeground(false); resumed = false; super.onPause() }
    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (::model.isInitialized) model.setForeground(resumed && hasFocus) }
    override fun onUserInteraction() { super.onUserInteraction(); if (::model.isInitialized) model.recordInteraction() }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleNotification(intent) }
    private fun handleNotification(intent: Intent) {
        if (intent.getBooleanExtra("open_focus", false)) { model.openFocusFromNotification(); intent.removeExtra("open_focus") }
        intent.getStringExtra("open_route")?.let { model.openRoute(it); intent.removeExtra("open_route") }
    }
}

private val Forest = Color(0xFF214D40)
private val Sage = Color(0xFFA7C7A2)
private val Ink = Color(0xFF193C35)
private val Paper = Color(0xFFF3F4EF)
private val displayFont = NinhoDisplayFont

@Composable private fun NinhoApp(model: NinhoModel) {
    val dark = when (model.data.settings.theme) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val scheme = if (dark) darkColorScheme(primary = Sage, onPrimary = Ink, background = Color(0xFF181A1F), surface = Color(0xFF24272E), onSurface = Color(0xFFF0F1F4), surfaceVariant = Color(0xFF30343D), onSurfaceVariant = Color(0xFFBEC3CD), secondaryContainer = Color(0xFF343A43), onSecondaryContainer = Color(0xFFF0F1F4), outline = Color(0xFF777E8A))
        else lightColorScheme(primary = Forest, onPrimary = Color.White, background = Paper, surface = Color.White, onSurface = Ink, secondaryContainer = Color(0xFFE6ECD9), onSecondaryContainer = Ink)
    MaterialTheme(colorScheme = scheme, typography = NinhoTypography) {
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var showSubject by rememberSaveable { mutableStateOf(false) }
        var showStudyCalendar by rememberSaveable { mutableStateOf(false) }
        var tutorial by rememberSaveable { mutableStateOf<String?>(null) }
        var enteredRoute by remember { mutableStateOf<String?>(null) }
        val route = ScreenTutorials.routes[tab.coerceIn(0, 5)]
        val welcome = model.data.profile.completedAt == null || model.onboardingProcessing
        LaunchedEffect(model.focusRequest, welcome) { if (model.focusRequest > 0 && !welcome) tab = 2 }
        LaunchedEffect(model.routeRequest, welcome) { if (model.routeRequest.second > 0 && !welcome) tab = when (model.routeRequest.first) { "focus" -> 2; "reviews" -> 3; else -> 0 } }
        LaunchedEffect(route, model.loaded, welcome) {
            model.visitRoute(if (model.loaded && !welcome) route else "")
            if (model.loaded && !welcome && enteredRoute != route) {
                enteredRoute = route
                if (route !in model.data.tutorialsSeen) tutorial = route
            }
        }
        if (!model.loaded) Surface(Modifier.fillMaxSize(), color = scheme.background) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (model.error == null) "Preparando seu ninho…" else "Seus dados precisam de atenção.")
            }
        } else if (welcome) Welcome(model)
        else Scaffold(containerColor = scheme.background, bottomBar = { BottomDock(tab) { tab = it } }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.owl), "Coruja Ninho", Modifier.size(44.dp))
                    Text("ninho", Modifier.padding(start = 7.dp).weight(1f), fontFamily = displayFont, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    StreakHeaderButton(model.data) { showStudyCalendar = true }
                    IconButton(onClick = { tab = 5 }, modifier = Modifier.size(48.dp)) { Icon(painterResource(R.drawable.ic_profile), "Meu perfil", Modifier.size(24.dp)) }
                    IconButton(onClick = { tutorial = route }, modifier = Modifier.size(48.dp)) { Icon(painterResource(R.drawable.ic_help), "Ajuda desta tela", Modifier.size(24.dp)) }
                }
                val pageContent: @Composable (Int) -> Unit = { page ->
                    when (page) {
                        0 -> Home(model, onFocus = { tab = 2 }, onSubject = { showSubject = true }, onReview = { tab = 3 }, onAssistant = { tab = 4 }, onInsight = { insight -> model.prepareInsight(insight); tab = insightTab(insight.route) })
                        1 -> Subjects(model) { showSubject = true }
                        2 -> Focus(model) { showSubject = true }
                        3 -> Review(model) { showSubject = true }
                        4 -> AssistantPage(model) { insight -> model.prepareInsight(insight); tab = insightTab(insight.route) }
                        else -> ProfilePage(model, onAssistant = { tab = 4 }, onTutorial = { tutorial = route })
                    }
                }
                if (model.data.settings.reducedMotion) pageContent(tab)
                else AnimatedContent(tab, label = "Navegação") { pageContent(it) }
            }
        }
        if (showSubject) SubjectDialog(model) { showSubject = false }
        if (!welcome && showStudyCalendar) StudyCalendarDialog(model.data) { showStudyCalendar = false }
        if (!welcome) tutorial?.let { current -> TutorialDialog(current) { tutorial = null; model.tutorialSeen(current) } }
        model.error?.let { message -> AlertDialog(onDismissRequest = model::dismissError, title = { Text("Um cuidado antes de continuar") }, text = { Text(message) }, confirmButton = { TextButton(onClick = model::dismissError) { Text("Entendi") } }) }
    }
}

@Composable private fun BottomDock(selected: Int, onSelect: (Int) -> Unit) {
    Surface(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .94f), shadowElevation = 8.dp) {
        Row(Modifier.padding(7.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("Hoje", "Estudos", "Foco", "Revisões", "Assistente").forEachIndexed { index, label ->
                val color by animateColorAsState(if (selected == index) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent, label = "Seleção")
                Column(Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(color).clickable { onSelect(index) }.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LineIcon(index, MaterialTheme.colorScheme.onSurface)
                    Text(label, fontSize = 10.sp, fontWeight = if (selected == index) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable private fun LineIcon(index: Int, color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val s = size.width / 24f
        fun line(x: Float, y: Float, x2: Float, y2: Float) = drawLine(color, Offset(x*s,y*s), Offset(x2*s,y2*s),1.8f*s,StrokeCap.Round)
        when (index) {
            0 -> { val path = Path().apply { moveTo(3*s,11*s); lineTo(12*s,3*s); lineTo(21*s,11*s); moveTo(5*s,10*s); lineTo(5*s,21*s); lineTo(19*s,21*s); lineTo(19*s,10*s) }; drawPath(path,color,style=Stroke(1.8f*s,cap=StrokeCap.Round)); line(10f,21f,10f,15f); line(10f,15f,14f,15f); line(14f,15f,14f,21f) }
            1 -> { drawRoundRect(color,Offset(4*s,3*s),Size(16*s,18*s),androidx.compose.ui.geometry.CornerRadius(2*s),style=Stroke(1.8f*s)); line(8f,3f,8f,21f); line(12f,8f,17f,8f); line(12f,12f,17f,12f) }
            2 -> { drawCircle(color,8*s,Offset(12*s,14*s),style=Stroke(1.8f*s)); line(12f,14f,12f,9f); line(9f,2f,15f,2f); line(12f,2f,12f,6f) }
            3 -> { drawArc(color,35f,285f,false,Offset(3*s,3*s),Size(18*s,18*s),style=Stroke(1.8f*s,cap=StrokeCap.Round)); line(18f,3f,19f,9f); line(19f,9f,14f,8f); line(8f,12f,11f,15f); line(11f,15f,16f,10f) }
            else -> { val path = Path().apply { moveTo(10*s,3*s); lineTo(12*s,9*s); lineTo(18*s,11*s); lineTo(12*s,13*s); lineTo(10*s,19*s); lineTo(8*s,13*s); lineTo(2*s,11*s); lineTo(8*s,9*s); close() }; drawPath(path,color,style=Stroke(1.8f*s)); line(19f,2f,19f,6f); line(17f,4f,21f,4f); line(20f,16f,20f,22f); line(17f,19f,23f,19f) }
        }
    }
}
@Composable internal fun Heading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, fontFamily = displayFont, fontSize = 34.sp, lineHeight = 39.sp); Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 21.sp) }
}
@Composable internal fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp)) { Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content) }
}
@Composable private fun SectionTitle(title: String, action: String? = null, onClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(title, Modifier.weight(1f), fontSize = 21.sp, fontWeight = FontWeight.SemiBold); if (action != null) TextButton(onClick = onClick) { Text(action) } }
}
@Composable private fun EmptyState(title: String, body: String, action: String, onClick: () -> Unit) {
    Panel { Text(title, fontWeight = FontWeight.SemiBold, fontSize = 19.sp); Text(body, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); Button(onClick = onClick, shape = CircleShape) { Text(action) } }
}
@Composable private fun Home(model: NinhoModel, onFocus: () -> Unit, onSubject: () -> Unit, onReview: () -> Unit, onAssistant: () -> Unit, onInsight: (LocalInsight) -> Unit) {
    val data = model.data
    val now = rememberStudyClock()
    val next = StudyCoach.suggestions(data, now).firstOrNull()
    LazyColumn(contentPadding = PaddingValues(start=24.dp,end=24.dp,top=14.dp,bottom=20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { Heading("Aprender, no seu ritmo.", "Um espaço tranquilo para cultivar o que você sabe.") }
        item { Surface(color = Forest, shape = RoundedCornerShape(30.dp)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Um pequeno passo\nvale muito.", color = Color.White, fontFamily = displayFont, fontSize = 29.sp, lineHeight = 35.sp)
                        Text(if (data.timer.running) "Sua sessão continua em andamento." else "Separe um tempo só para aprender.", color = Color(0xFFD9E8DE), fontSize = 14.sp, lineHeight = 21.sp)
                    }
                    Image(painterResource(R.drawable.owl), null, Modifier.size(112.dp))
                }
                Button(onClick = if (data.subjects.isEmpty()) onSubject else onFocus, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE6ECCF), contentColor = Ink), shape = CircleShape) { Text(if (data.subjects.isEmpty()) "Adicionar minha primeira matéria" else if (data.timer.running) "Voltar ao meu foco" else "Começar a estudar", Modifier.padding(vertical = 4.dp)) }
            }
        } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { Metric("${StudyCoach.secondsToday(data,now)/60} min","de cuidado hoje",Modifier.weight(1f)); Metric("${data.sessions.size}","sessões registradas",Modifier.weight(1f)) } }
        item { SectionTitle("Sua assistente percebeu", "Ver tudo", onAssistant) }
        items(model.analysis.cards.take(2), key = { "insight-${it.id}" }) { insight -> InsightCard(insight) { onInsight(insight) } }
        item { SectionTitle("Seu próximo passo", "Ver revisões", onReview) }
        item { if (next == null) EmptyState("Tudo começa com uma matéria", "Escolha o que você quer aprender. Seu histórico e suas recomendações crescem com você.", "Adicionar matéria", onSubject)
            else Panel { Text(next.subject.name,fontSize=21.sp,fontWeight=FontWeight.SemiBold); Text(next.reason,color=MaterialTheme.colorScheme.onSurfaceVariant,lineHeight=23.sp); Text("Orientação baseada nos seus registros",fontSize=11.sp,color=MaterialTheme.colorScheme.primary) } }
        if (data.sessions.isNotEmpty()) { item { SectionTitle("Últimos momentos de foco") }; items(data.sessions.takeLast(3).asReversed(),key={it.id}) { SessionRow(it,data) } }
        item { Text("Estude offline. Seus dados ficam no aparelho, com backup opcional do Android.",fontSize=12.sp,lineHeight=19.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
@Composable private fun Metric(value: String,label: String,modifier: Modifier) { Panel(modifier) { Text(value,fontFamily=displayFont,fontSize=27.sp); Text(label,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) } }
@Composable private fun SessionRow(session: StudySession,data: StudyData) {
    Panel {
        Row(verticalAlignment=Alignment.CenterVertically) { Text(data.subjects.find{it.id==session.subjectId}?.name?:"Matéria",Modifier.weight(1f),fontWeight=FontWeight.SemiBold); Text("${session.seconds/60} min",color=MaterialTheme.colorScheme.primary) }
        Text("${SimpleDateFormat("dd MMM, HH:mm",Locale.forLanguageTag("pt-BR")).format(Date(session.at))} • ${listOf("","Preciso retomar","Estou entendendo","Me sinto confiante")[session.rating.coerceIn(1,3)]}",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(session.note.isNotBlank()) Text(session.note,fontSize=14.sp)
    }
}
@Composable private fun Subjects(model: NinhoModel,onAdd:()->Unit) {
    LazyColumn(contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        item { Heading("O que você cultiva?","Cada matéria tem seu tempo. Acompanhe o seu caminho de perto.") }
        item { Button(onClick=onAdd,enabled=!model.saving,shape=CircleShape) { Text("+  Nova matéria") } }
        if(model.data.subjects.isEmpty()) item { EmptyState("Seu ninho está pronto","Adicione uma matéria para vincular suas sessões e questões.","Adicionar matéria",onAdd) }
        items(model.data.subjects,key={it.id}) { subject ->
            val sessions=model.data.sessions.filter{it.subjectId==subject.id}; val attempts=model.data.attempts.filter{it.subjectId==subject.id}
            Panel {
                Row(verticalAlignment=Alignment.CenterVertically) { Surface(shape=RoundedCornerShape(15.dp),color=MaterialTheme.colorScheme.secondaryContainer) { Text(subject.name.take(1).uppercase(),Modifier.padding(16.dp),fontSize=22.sp,fontFamily=displayFont) }; Text(subject.name,Modifier.padding(start=15.dp).weight(1f),fontSize=23.sp,fontWeight=FontWeight.SemiBold) }
                Text("${sessions.sumOf{it.seconds}/60} minutos estudados  •  ${sessions.size} sessões",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=13.sp)
                Text(if(attempts.isEmpty()) "Registre questões para acompanhar o que precisa de atenção." else "${attempts.count{it.correct}} de ${attempts.size} respostas marcadas como corretas",fontSize=13.sp,lineHeight=20.sp)
            }
        }
    }
}
@Composable private fun SubjectPicker(data: StudyData,selected:String,enabled:Boolean=true,onSelect:(String)->Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) { data.subjects.forEach { subject -> FilterChip(selected=subject.id==selected,onClick={onSelect(subject.id)},enabled=enabled,label={Text(subject.name)},shape=CircleShape) } }
}
@Composable private fun Focus(model:NinhoModel,onAdd:()->Unit) {
    val timer=model.data.timer
    var clock by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }; var wall by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(timer.running, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            do { clock=SystemClock.elapsedRealtime(); wall=System.currentTimeMillis(); if(timer.running) delay(1000) } while(timer.running)
        }
    }
    val elapsed=timer.elapsed(clock,wall,model.bootCount); val finished=!timer.stopwatch&&elapsed>=timer.targetSeconds*1000
    val editable=!timer.running&&timer.elapsedMillis==0L&&!model.saving
    var custom by rememberSaveable { mutableStateOf(false) };var finish by rememberSaveable { mutableStateOf(false) };var reset by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(finished) { if(finished&&timer.running) model.pauseTimer() }
    LazyColumn(contentPadding=PaddingValues(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(20.dp)) {
        item { Heading("Aqui, agora.","Uma coisa de cada vez. O resto pode esperar.") }
        if(model.data.subjects.isEmpty()) item { EmptyState("O que vamos estudar?","Adicione uma matéria para registrar este momento de foco.","Adicionar matéria",onAdd) }
        else item { SubjectPicker(model.data,timer.subjectId,editable) { model.configureTimer(subject=it) } }
        item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { FilterChip(selected=!timer.stopwatch,onClick={model.configureTimer(stopwatch=false)},enabled=editable,label={Text("Temporizador")}); FilterChip(selected=timer.stopwatch,onClick={model.configureTimer(stopwatch=true)},enabled=editable,label={Text("Cronômetro livre")}) } }
        item {
            val fraction by animateFloatAsState(if(timer.stopwatch)(elapsed/3_600_000f).coerceAtMost(1f) else elapsed.toFloat()/(timer.targetSeconds*1000f),label="Progresso do foco")
            val displayed=if(timer.stopwatch) elapsed/1000 else ((timer.targetSeconds*1000-elapsed).coerceAtLeast(0)+999)/1000
            val ring=MaterialTheme.colorScheme.secondaryContainer;val accent=MaterialTheme.colorScheme.primary
            Box(Modifier.sizeIn(maxWidth=300.dp).fillMaxWidth().aspectRatio(1f),contentAlignment=Alignment.Center) {
                Canvas(Modifier.fillMaxSize().padding(8.dp)) { drawCircle(ring,style=Stroke(7.dp.toPx()));drawArc(accent,-90f,360f*fraction,false,style=Stroke(7.dp.toPx(),cap=StrokeCap.Round)) }
                Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text(if(finished) "Seu tempo floresceu" else if(timer.running) "Seu momento de foco" else if(elapsed>0) "Respire. Está pausado." else "Pronto quando você estiver",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("%02d:%02d".format(Locale.ROOT,displayed/60,displayed%60),fontSize=61.sp,fontWeight=FontWeight.Light,letterSpacing=(-2).sp,modifier=Modifier.semantics{contentDescription="${displayed/60} minutos e ${displayed%60} segundos"})
                    Text(model.data.subjects.find{it.id==timer.subjectId}?.name?:"Escolha sua matéria",fontSize=14.sp)
                }
            }
        }
        if(!timer.stopwatch) item { Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf(15,25,45,60).forEach { minutes -> FilterChip(selected=timer.targetSeconds==minutes*60L,onClick={model.configureTimer(minutes=minutes)},enabled=editable,label={Text("$minutes min")},shape=CircleShape) };AssistChip(onClick={custom=true},enabled=editable,label={Text("Editar")},shape=CircleShape) } }
        item { Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Button(onClick={if(finished) finish=true else model.toggleTimer()},enabled=!model.saving&&timer.subjectId.isNotBlank(),shape=CircleShape,modifier=Modifier.fillMaxWidth().height(56.dp)) { Text(if(finished) "Salvar meu estudo" else if(timer.running) "Pausar" else if(elapsed>0) "Continuar" else "Começar meu foco",fontSize=16.sp) }
            if(elapsed>0&&!finished) TextButton(onClick={model.pauseTimer();finish=true},enabled=!model.saving) { Text("Concluir e registrar") }
            if(elapsed>0) TextButton(onClick={reset=true},enabled=!model.saving) { Text("Descartar sessão") }
            Text("O tempo continua com a tela apagada, sem interromper música ou vídeo. Ative o aviso silencioso em Perfil. O Android pode atrasar esse aviso em economia de energia; o tempo salvo permanece correto.",textAlign=TextAlign.Center,fontSize=12.sp,lineHeight=19.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        } }
    }
    if(custom) {
        var minutes by remember { mutableStateOf((timer.targetSeconds/60).toString()) };val valid=minutes.toIntOrNull()?.let{it in 1..240}==true
        AlertDialog(onDismissRequest={custom=false},title={Text("Quanto tempo você quer?")},text={OutlinedTextField(value=minutes,onValueChange={minutes=it.filter(Char::isDigit).take(3)},label={Text("Minutos (1 a 240)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true)},confirmButton={TextButton(onClick={model.configureTimer(minutes=minutes.toInt());custom=false},enabled=valid){Text("Usar esse tempo")}},dismissButton={TextButton(onClick={custom=false}){Text("Cancelar")}})
    }
    if(finish) SessionDialog(model){finish=false}
    if(reset) AlertDialog(onDismissRequest={reset=false},title={Text("Descartar este tempo?")},text={Text("Esta sessão não será adicionada ao seu histórico.")},confirmButton={TextButton(onClick={model.resetTimer();reset=false}){Text("Descartar")}},dismissButton={TextButton(onClick={reset=false}){Text("Continuar estudando")}})
}
@Composable private fun SubjectDialog(model:NinhoModel,close:()->Unit) {
    var name by rememberSaveable{mutableStateOf("")}
    AlertDialog(onDismissRequest=close,title={Text("Uma nova matéria")},text={OutlinedTextField(name,{name=it.take(80)},label={Text("O que você quer aprender?")},singleLine=true)},confirmButton={TextButton(onClick={model.addSubject(name);close()},enabled=name.isNotBlank()&&!model.saving){Text("Adicionar")}},dismissButton={TextButton(onClick=close){Text("Cancelar")}})
}
@Composable private fun SessionDialog(model:NinhoModel,close:()->Unit) {
    var rating by rememberSaveable{mutableIntStateOf(2)};var note by rememberSaveable{mutableStateOf("")}
    AlertDialog(onDismissRequest=close,title={Text("Como foi aprender hoje?")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text("Essa avaliação ajuda a escolher quando retomar a matéria.")
        listOf("Preciso retomar","Estou entendendo","Me sinto confiante").forEachIndexed { index,text -> FilterChip(selected=rating==index+1,onClick={rating=index+1},label={Text(text)}) }
        OutlinedTextField(note,{note=it.take(1000)},label={Text("Conteúdo e dificuldades (opcional)")},maxLines=4)
    }},confirmButton={TextButton(onClick={model.saveSession(rating,note);close()},enabled=!model.saving){Text("Salvar estudo")}},dismissButton={TextButton(onClick=close){Text("Voltar")}})
}
@Composable private fun Review(model:NinhoModel,onAddSubject:()->Unit) {
    var add by rememberSaveable{mutableStateOf(false)};var questionId by rememberSaveable{mutableStateOf<String?>(null)}
    val suggestions=StudyCoach.suggestions(model.data,System.currentTimeMillis())
    LazyColumn(contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
        item{Heading("Lembrar é aprender.","Volte ao que merece atenção e teste sua memória sem consultar.")}
        if(suggestions.isEmpty()) item{EmptyState("Prepare suas primeiras revisões","O Ninho combina seu tempo de estudo, suas avaliações e os erros nas questões.","Adicionar matéria",onAddSubject)}
        items(suggestions.take(6),key={it.subject.id}){suggestion->Panel{Text(suggestion.subject.name,fontWeight=FontWeight.SemiBold,fontSize=20.sp);Text(suggestion.reason,lineHeight=22.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
        item{SectionTitle("Suas questões","+ Adicionar",onClick={if(model.data.subjects.isEmpty())onAddSubject() else add=true})}
        if(model.data.questions.isEmpty())item{Text("Adicione uma pergunta e seu gabarito. Depois de responder de memória, compare e avalie sua resposta.",color=MaterialTheme.colorScheme.onSurfaceVariant,lineHeight=23.sp)}
        items(model.data.questions,key={it.id}){question->
            val attempts=model.data.attempts.filter{it.questionId==question.id}
            Panel{Text(model.data.subjects.find{it.id==question.subjectId}?.name.orEmpty(),fontSize=12.sp,color=MaterialTheme.colorScheme.primary);Text(question.prompt,fontSize=18.sp,lineHeight=25.sp);Text(if(attempts.isEmpty())"Ainda não praticada" else "${attempts.count{it.correct}} acertos em ${attempts.size} tentativas",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);OutlinedButton(onClick={questionId=question.id},enabled=!model.saving){Text("Praticar de memória")}}
        }
    }
    if(add)QuestionDialog(model){add=false}
    model.data.questions.find{it.id==questionId}?.let{question->PracticeDialog(question,onAnswer={correct,response->model.answer(question,correct,response);questionId=null},close={questionId=null})}
}
@Composable private fun QuestionDialog(model:NinhoModel,close:()->Unit) {
    var subject by rememberSaveable{mutableStateOf(model.data.subjects.firstOrNull()?.id.orEmpty())};var prompt by rememberSaveable{mutableStateOf("")};var answer by rememberSaveable{mutableStateOf("")}
    AlertDialog(onDismissRequest=close,title={Text("Uma questão para revisitar")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){SubjectPicker(model.data,subject){subject=it};OutlinedTextField(prompt,{prompt=it.take(2000)},label={Text("Pergunta")},maxLines=3);OutlinedTextField(answer,{answer=it.take(3000)},label={Text("Gabarito e explicação")},maxLines=4)}},confirmButton={TextButton(onClick={model.addQuestion(subject,prompt,answer);close()},enabled=subject.isNotBlank()&&prompt.isNotBlank()&&answer.isNotBlank()&&!model.saving){Text("Guardar questão")}},dismissButton={TextButton(onClick=close){Text("Cancelar")}})
}
@Composable private fun PracticeDialog(question:Question,onAnswer:(Boolean,String)->Unit,close:()->Unit) {
    var revealed by rememberSaveable(question.id){mutableStateOf(false)};var response by rememberSaveable(question.id){mutableStateOf("")}
    AlertDialog(onDismissRequest=close,title={Text("Puxe pela memória")},text={Column(verticalArrangement=Arrangement.spacedBy(16.dp)){Text(question.prompt,fontWeight=FontWeight.SemiBold);if(!revealed)OutlinedTextField(response,{response=it.take(3000)},label={Text("Sua resposta")},maxLines=5) else {Text("Gabarito",color=MaterialTheme.colorScheme.primary);Text(question.answer);Text("Sua resposta: $response",fontSize=13.sp);Text("Compare com o gabarito e avalie sua resposta.",fontSize=12.sp)}}},confirmButton={if(!revealed)TextButton(onClick={revealed=true},enabled=response.isNotBlank()){Text("Comparar resposta")}else TextButton(onClick={onAnswer(true,response)}){Text("Acertei")}},dismissButton={if(revealed)TextButton(onClick={onAnswer(false,response)}){Text("Preciso revisar")}else TextButton(onClick=close){Text("Voltar")}})
}
