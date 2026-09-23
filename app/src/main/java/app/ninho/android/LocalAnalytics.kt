package app.ninho.android

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

data class AppUsageDay(val day: String, val route: String, val activeSeconds: Long = 0, val visits: Int = 0, val timerPauses: Int = 0)
data class LocalInsight(val id: String, val title: String, val explanation: String, val evidence: String, val action: String, val route: String, val subjectId: String = "")
data class LocalAnalysis(val at: Long = 0, val cards: List<LocalInsight> = emptyList(), val plan: List<String> = emptyList())

object LocalAnalytics {
    const val DAY = 86_400_000L
    fun smoothedAccuracy(correct: Int, attempts: Int): Double = (correct.coerceIn(0, attempts.coerceAtLeast(0)) + 2.0) / (attempts.coerceAtLeast(0) + 4.0)
    fun confidence(count: Int) = when { count < 5 -> "amostra pequena"; count < 20 -> "confiança inicial"; else -> "amostra mais consistente" }
    fun analyze(data: StudyData, now: Long, zone: ZoneId = ZoneId.systemDefault()): LocalAnalysis {
        val cutoff = now - 90 * DAY
        val sessions = data.sessions.filter { it.at in cutoff..now && it.seconds > 0 }
        val attempts = data.attempts.filter { it.at in cutoff..now }
        val profile = data.profile
        val block = minOf(profile.sessionMinutes, profile.dailyMinutes).coerceIn(1, 240)
        val cards = mutableListOf<LocalInsight>()
        val historical = data.copy(sessions = data.sessions.filter { it.at <= now }, attempts = data.attempts.filter { it.at <= now })
        val suggested = StudyCoach.suggestions(historical, now)
        val due = suggested.filter { it.due <= now && (historical.sessions.any { s -> s.subjectId == it.subject.id } || historical.attempts.any { a -> a.subjectId == it.subject.id }) }
        if (due.isNotEmpty()) {
            val first = due.first()
            cards += LocalInsight("review", "Uma lembrança para hoje", "${first.subject.name}: ${first.reason}", "${due.size} matéria(s) com revisão sugerida pelos seus registros.", "Abrir revisões", "reviews", first.subject.id)
        }
        val difficulties = data.subjects.mapNotNull { subject ->
            val records = attempts.filter { it.subjectId == subject.id }
            val score = smoothedAccuracy(records.count { it.correct }, records.size)
            if (records.size >= 5 && score < .7) Triple(subject, records, score) else null
        }.sortedBy { it.third }
        difficulties.firstOrNull()?.let { (subject, records, score) ->
            cards += LocalInsight("difficulty", "Pratique ${subject.name} com calma", "Você marcou ${records.count { it.correct }} de ${records.size} respostas como corretas. Tente uma questão, explique a resposta e confira o gabarito.", "Estimativa suavizada: ${(score * 100).roundToInt()}% • ${confidence(records.size)}. Autoavaliação não mede domínio.", "Praticar questões", "reviews", subject.id)
        }
        val flagged = sessions.filter { it.rating == 1 }.groupBy { it.subjectId }.maxByOrNull { it.value.size }
        if (flagged != null && flagged.value.size >= 2 && cards.none { it.subjectId == flagged.key && it.id == "difficulty" }) {
            cards += LocalInsight("feedback", "Retome uma explicação", "Em ${SubjectName(data, flagged.key)}, você marcou “Preciso retomar” em ${flagged.value.size} sessões recentes. Experimente um bloco curto para refazer um exemplo.", "Base: suas avaliações dos últimos 90 dias; não é diagnóstico de capacidade.", "Separar um bloco", "focus", flagged.key)
        }
        val starts = sessions.filter { it.seconds >= 60 }.map { Instant.ofEpochMilli(it.at - it.seconds.coerceAtMost(86_400) * 1000).atZone(zone) }
        val common = starts.groupBy { it.hour / 3 }.maxByOrNull { it.value.size }
        if (starts.size >= 6 && common != null && common.value.map { it.toLocalDate() }.distinct().size >= 3 && common.value.size.toDouble() / starts.size >= .4) {
            val start = common.key * 3
            cards += LocalInsight("schedule", "Um horário que já aparece na sua rotina", "Você iniciou ${common.value.size} de ${starts.size} sessões entre ${start}h e ${start + 3}h, em pelo menos três dias. Se ainda couber na sua rotina, reserve um bloco nesse período.", "Padrão observado nos últimos 90 dias. Frequência não significa maior produtividade.", "Preparar meu foco", "focus")
        }
        val unseen = data.subjects.firstOrNull { s -> historical.sessions.none { it.subjectId == s.id } }
        if (unseen != null && cards.size < 4) cards += LocalInsight("first", "Conheça ${unseen.name}", "Ainda não há sessão registrada para esta matéria. Comece com até $block minutos e anote o que conseguiu explicar com suas palavras.", "Ausência de registro não significa ausência de estudo.", "Começar um bloco", "focus", unseen.id)
        val recentUsage = UsageLedger.prune(data.usage, now, zone)
        val pauses = recentUsage.sumOf { it.timerPauses }
        if (pauses >= 3) cards += LocalInsight("pauses", "Seu foco pode ter pausas", "Você pausou o temporizador $pauses vezes no período. Se os blocos estiverem longos para sua rotina, experimente $block minutos e ajuste depois.", "Pausas explícitas no Ninho. Isso não indica desatenção, abandono ou uso de outro aplicativo.", "Ajustar meu foco", "focus")
        if (cards.isEmpty()) cards += LocalInsight("baseline", if (data.subjects.isEmpty()) "Vamos dar forma ao seu objetivo" else "Seu próximo pequeno passo", if (data.subjects.isEmpty()) "Cadastre uma matéria relacionada a “${profile.goal.ifBlank { "seu objetivo" }}”. Sua assistente adapta as sugestões conforme surgem registros." else "Separe até $block minutos para uma matéria, registre como foi e pratique lembrar sem consultar.", "Ponto de partida baseado no seu perfil; ainda faltam registros para reconhecer padrões.", if (data.subjects.isEmpty()) "Organizar meus estudos" else "Começar meu foco", if (data.subjects.isEmpty()) "studies" else "focus")
        val plan = listOf(
            "Seu objetivo: ${profile.goal.ifBlank { "defina o que deseja aprender no perfil" }}.",
            "Comece com blocos de até $block minutos, dentro dos ${profile.dailyMinutes} minutos disponíveis por dia.",
            if (profile.availableDays.isEmpty()) "Escolha dias possíveis no perfil; deixe espaço para pausas e imprevistos." else "Organize os blocos nos ${profile.availableDays.size} dias da semana escolhidos no perfil${profile.preferredTime.takeIf { it.isNotBlank() }?.let { ": $it" } ?: "."}",
            "Antes de reler, tente explicar uma ideia. Depois pratique uma questão e registre seu retorno; as próximas sugestões usam esses novos dados.",
        )
        return LocalAnalysis(now, cards.take(6), plan)
    }
    private fun SubjectName(data: StudyData, id: String) = data.subjects.find { it.id == id }?.name ?: "uma matéria"
}

object UsageLedger {
    val routes = setOf("today", "studies", "focus", "reviews", "assistant", "profile")
    fun prune(records: List<AppUsageDay>, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<AppUsageDay> {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return records.filter { it.route in routes && runCatching { LocalDate.parse(it.day) in today.minusDays(89)..today }.getOrDefault(false) }
            .groupBy { it.day to it.route }.map { (key, values) -> AppUsageDay(key.first, key.second, values.sumOf { it.activeSeconds.coerceIn(0, 86_400) }.coerceAtMost(86_400), values.sumOf { it.visits.coerceIn(0, 10000) }.coerceAtMost(10000), values.sumOf { it.timerPauses.coerceIn(0, 10000) }.coerceAtMost(10000)) }
            .sortedWith(compareBy<AppUsageDay> { it.day }.thenBy { it.route }).takeLast(90 * routes.size)
    }
    fun add(records: List<AppUsageDay>, route: String, now: Long, seconds: Long = 0, visits: Int = 0, pauses: Int = 0, zone: ZoneId = ZoneId.systemDefault()): List<AppUsageDay> {
        if (route !in routes) return prune(records, now, zone)
        val day = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toString()
        return prune(records + AppUsageDay(day, route, seconds.coerceIn(0, 60), visits.coerceIn(0, 1), pauses.coerceIn(0, 1)), now, zone)
    }
}

class ActiveRouteClock {
    private var last = 0L
    private var interaction = 0L
    private var remainder = 0L
    fun begin(now: Long) { last = now; interaction = now; remainder = 0 }
    fun touch(now: Long) { interaction = now }
    fun takeSeconds(now: Long): Long {
        val activeUntil = minOf(now, interaction + 60_000)
        val milliseconds = (activeUntil - last).coerceIn(0, 60_000) + remainder
        val seconds = milliseconds / 1000
        remainder = milliseconds % 1000
        last = now
        return seconds
    }
}
