package app.ninho.android

import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable internal fun rememberStudyClock(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) { now = System.currentTimeMillis(); delay(60_000 - now % 60_000) }
        }
    }
    return now
}

@Composable internal fun StreakHeaderButton(data: StudyData, onClick: () -> Unit) {
    val now = rememberStudyClock()
    val zone = ZoneId.systemDefault()
    val streak = remember(data.sessions, data.attempts, now, zone) { StudyStreak.summary(data, now, zone) }
    val description = "Sequência de ${streak.days} ${if (streak.days == 1) "dia" else "dias"}. Abrir calendário de estudos"
    TextButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp)
        .semantics { contentDescription = description }, contentPadding = PaddingValues(horizontal = 4.dp)) {
        Image(painterResource(R.drawable.ic_flame), null, Modifier.size(25.dp))
        Text("${streak.days}", Modifier.padding(start = 3.dp).clearAndSetSemantics {}, fontSize = 17.sp,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

@Composable internal fun StudyCalendarDialog(data: StudyData, onDismiss: () -> Unit) {
    val now = rememberStudyClock()
    val zone = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val studied = remember(data.sessions, data.attempts, now, zone) { StudyStreak.studyDays(data, now, zone) }
    val streak = remember(studied, today) { StudyStreak.summary(studied, today) }
    var monthValue by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    val month = YearMonth.parse(monthValue)
    val calendar = remember(month, studied) { StudyMonth.from(month, studied) }
    val locale = Locale.forLanguageTag("pt-BR")
    val monthLabel = "${month.month.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.titlecase(locale) }} ${month.year}"
    val dateFormat = remember { DateTimeFormatter.ofPattern("d 'de' MMMM 'de' uuuu", Locale.forLanguageTag("pt-BR")) }
    val todayColor = Color(0xFFE79639)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.widthIn(max = 440.dp).fillMaxWidth().padding(horizontal = 18.dp, vertical = 24.dp),
            shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Image(painterResource(R.drawable.ic_flame), null, Modifier.size(36.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Seus dias de estudo", fontFamily = NinhoDisplayFont, fontSize = 26.sp, lineHeight = 31.sp)
                        Text("${streak.days} ${if (streak.days == 1) "dia" else "dias"} de sequência", fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    CalendarMonthButton(previous = true, onClick = { monthValue = month.minusMonths(1).toString() })
                    Text(monthLabel, Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    CalendarMonthButton(previous = false, onClick = { monthValue = month.plusMonths(1).toString() })
                }
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom").forEach { day ->
                            Text(day, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    calendar.cells.chunked(7).forEach { week ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            week.forEach { date ->
                                Box(Modifier.weight(1f).heightIn(min = 44.dp), contentAlignment = Alignment.Center) {
                                    if (date != null) {
                                        val didStudy = date in calendar.studied
                                        val isToday = date == today
                                        val label = "${date.format(dateFormat)}${if (isToday) ", hoje" else ""}: ${if (didStudy) "estudo registrado" else if (date > today) "dia futuro" else "sem estudo registrado"}"
                                        Surface(Modifier.fillMaxWidth().aspectRatio(1f).clearAndSetSemantics { contentDescription = label },
                                            shape = CircleShape,
                                            border = if (isToday) BorderStroke(2.dp, todayColor) else null,
                                            color = if (didStudy) MaterialTheme.colorScheme.primary else Color.Transparent) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text("${date.dayOfMonth}", fontSize = 15.sp,
                                                    fontWeight = if (didStudy || isToday) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (didStudy) MaterialTheme.colorScheme.onPrimary
                                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = if (date > today) .4f else 1f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                        Text("Estudou", fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(12.dp).border(2.dp, todayColor, CircleShape))
                        Text("Hoje", fontSize = 12.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { monthValue = YearMonth.from(today).toString() },
                        modifier = Modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 6.dp)) { Text("Ir para hoje", fontSize = 12.sp) }
                }
                Text(if (calendar.studied.isEmpty()) "Nenhum estudo registrado neste mês. Cada pequeno passo aparece aqui."
                    else "${calendar.studied.size} ${if (calendar.studied.size == 1) "dia de estudo registrado" else "dias de estudo registrados"} neste mês.",
                    fontSize = 14.sp, lineHeight = 21.sp)
                Text("Uma sessão salva ou uma questão respondida acende o dia. Seu histórico continua aqui, mesmo quando a sequência recomeça.",
                    fontSize = 12.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = CircleShape) { Text("Concluir") }
            }
        }
    }
}

@Composable private fun CalendarMonthButton(previous: Boolean, onClick: () -> Unit) {
    val color = MaterialTheme.colorScheme.onSurface
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp).semantics {
        contentDescription = if (previous) "Mês anterior" else "Próximo mês"
    }) {
        Canvas(Modifier.size(20.dp)) {
            val x1 = size.width * if (previous) .65f else .35f
            val x2 = size.width - x1
            val path = Path().apply { moveTo(x1, size.height * .2f); lineTo(x2, size.height * .5f); lineTo(x1, size.height * .8f) }
            drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable internal fun WidgetOptions() {
    val context = LocalContext.current
    var notice by remember { mutableStateOf<String?>(null) }
    Panel {
        Text("Seu ninho na tela inicial", fontFamily = NinhoDisplayFont, fontSize = 25.sp)
        Text("Leve sua sequência, sua meta ou um atalho de foco para a tela inicial do celular.", lineHeight = 22.sp)
        NinhoWidgetKind.entries.forEach { kind ->
            Text(kind.title, fontWeight = FontWeight.SemiBold)
            Text(kind.description, fontSize = 13.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = {
                notice = try {
                    if (NinhoWidgets.requestPin(context, kind)) "Confirme a adição na tela do Android."
                    else "Mantenha pressionado um espaço vazio na tela inicial, escolha Widgets e procure Ninho."
                } catch (_: Exception) { "Adicione pela tela inicial: pressione um espaço vazio, toque em Widgets e procure Ninho." }
            }) { Text("Adicionar ${kind.title.lowercase(Locale.forLanguageTag("pt-BR"))}") }
        }
        notice?.let { Text(it, fontSize = 13.sp, lineHeight = 21.sp) }
        Text("Também funciona pelo menu Widgets do celular. Mantenha pressionado o ícone do Ninho para abrir Foco ou Revisões. Os widgets mostram somente contagens, sem nome, objetivos ou respostas. O Android controla o horário das atualizações em repouso.", fontSize = 12.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
