package app.ninho.android

/** Canonical, bounded facts about the student. Never replace these with a model summary. */
data class StudentProfile(
    val name: String = "", val goal: String = "", val motivation: String = "", val targetDate: String = "",
    val subjects: String = "", val level: String = "", val routine: String = "",
    val availableDays: List<String> = emptyList(), val preferredTime: String = "",
    val dailyMinutes: Int = 60, val sessionMinutes: Int = 25,
    val challenges: String = "", val preferences: String = "", val accessibility: String = "",
    val completedAt: Long? = null, val updatedAt: Long? = null, val revision: Int = 0,
    val plan: String = "", val planStatus: String = "none", val planProfileRevision: Int = 0,
) {
    fun normalized() = copy(
        name = name.take(100), goal = goal.take(400), motivation = motivation.take(300), targetDate = targetDate.take(40),
        subjects = subjects.take(300), level = level.take(160), routine = routine.take(400),
        availableDays = availableDays.filter { it in DAYS }.distinct().take(7), preferredTime = preferredTime.take(160),
        dailyMinutes = dailyMinutes.coerceIn(1, 1440), sessionMinutes = sessionMinutes.coerceIn(1, 240),
        challenges = challenges.take(400), preferences = preferences.take(300), accessibility = accessibility.take(240),
        revision = revision.coerceAtLeast(0), plan = plan.take(16000),
        planStatus = planStatus.takeIf { it in setOf("none", "pending", "ready", "error") } ?: "none",
    )

    fun edited(draft: StudentProfile, now: Long): StudentProfile = draft.normalized().copy(
        completedAt = completedAt, updatedAt = now, revision = revision + 1,
        plan = plan, planStatus = if (planStatus == "pending") "none" else planStatus,
        planProfileRevision = planProfileRevision,
    )

    fun withPlan(result: String, forRevision: Int): StudentProfile =
        if (revision != forRevision) this else copy(plan = result.take(16000), planStatus = "ready", planProfileRevision = forRevision)

    companion object { val DAYS = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun") }
}

data class UserSettings(val theme: String = "system", val reducedMotion: Boolean = false, val timerReminder: Boolean = false, val appUsage: Boolean = true)

data class TutorialStep(val title: String, val description: String)
object ScreenTutorials {
    val routes = listOf("today", "studies", "focus", "reviews", "assistant", "profile")
    fun steps(route: String) = when (route) {
        "today" -> listOf(TutorialStep("Este é o seu ninho", "A barra inferior leva a Hoje, Estudos, Foco, Revisões e Assistente. Perfil fica no topo. O foguinho ao lado mostra sua sequência; toque para ver os dias estudados no calendário. Seus dados ficam neste aparelho."), TutorialStep("Um passo de cada vez", "O cartão verde inicia uma sessão. Os números abaixo mostram minutos de hoje e sessões registradas; a assistente apresenta pistas explicadas a partir dos seus registros."), TutorialStep("Seu histórico", "Os últimos momentos de foco mostram matéria, tempo, data e sua autoavaliação. Eles ajudam a acompanhar o que você realmente estudou."))
        "studies" -> listOf(TutorialStep("Organize suas matérias", "Nova matéria cria um assunto para vincular sessões e questões. Cada cartão mostra o tempo registrado e os acertos que você marcou."), TutorialStep("Construa seu histórico", "As matérias aparecem no seletor do Foco e das questões. Cadastre uma antes de começar sua primeira sessão."))
        "focus" -> listOf(TutorialStep("Hora de focar", "Escolha uma matéria e use Temporizador ou Cronômetro livre. Os atalhos e a duração personalizada ajustam seu tempo antes de começar."), TutorialStep("Pausar e registrar", "O tempo continua contando com a tela apagada. Ao terminar, registre a sessão e diga como foi: sua avaliação ajuda a escolher quando revisar."))
        "reviews" -> listOf(TutorialStep("Lembre antes de consultar", "As sugestões de revisão usam intervalos de 1, 3 ou 7 dias, conforme sua autoavaliação. Uma questão errada antecipa a revisão."), TutorialStep("Pratique com suas questões", "Cadastre pergunta e gabarito. Escreva sua resposta, confira a explicação e marque se acertou. O Ninho guarda essa tentativa."))
        "assistant" -> listOf(TutorialStep("Minha assistente", "A análise local já vem incluída. Ela combina seu perfil, sessões, questões e avaliações para apresentar sugestões automaticamente, sem importar nada."), TutorialStep("Cada sugestão tem um motivo", "Os cartões explicam os registros e a amostra que sustentam a recomendação. Poucos dados não viram conclusões fortes; navegação nunca conta como estudo."), TutorialStep("Você mantém o controle", "Seus objetivos ficam salvos no Perfil e podem mudar a qualquer momento. O Ninho não observa outros aplicativos. Preferências e registros de navegação também ficam sob seu controle no Perfil."))
        else -> listOf(TutorialStep("Meu perfil", "Edite objetivos, rotina e preferências aqui. Cada alteração é salva automaticamente, sem conta ou conexão."), TutorialStep("Um caminho que acompanha você", "O perfil é a memória permanente da assistente. As sugestões incluídas acompanham novos objetivos e novos registros de estudo."), TutorialStep("Do seu jeito", "Escolha tema, movimento, aviso silencioso e análise da navegação aqui. Opções avançadas guardam o modelo de linguagem opcional. Ajuda repete os tutoriais."))
    }
}
