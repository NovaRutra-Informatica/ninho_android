package app.ninho.android

enum class ProfileQuestion { NAME, GOAL, MOTIVATION, TARGET_DATE, SUBJECTS, LEVEL, ROUTINE, DAYS, TIME, DAILY_MINUTES, SESSION_MINUTES, CHALLENGES, PREFERENCES, ACCESSIBILITY }

data class WelcomeStep(val title: String, val guide: String, val questions: List<ProfileQuestion>)

object WelcomeFlow {
    val steps = listOf(
        WelcomeStep("Como posso chamar você?", "Oi! Vou ajudar você a construir um plano que caiba na sua vida. Vamos começar pelo seu nome?", listOf(ProfileQuestion.NAME)),
        WelcomeStep("Onde você quer chegar?", "Um objetivo claro dá direção aos pequenos passos. Conte o que você gostaria de conquistar com seus estudos.", listOf(ProfileQuestion.GOAL)),
        WelcomeStep("O que move você?", "Quero entender por que esse caminho importa. Se houver uma prova ou um prazo, podemos nos organizar para ele.", listOf(ProfileQuestion.MOTIVATION, ProfileQuestion.TARGET_DATE)),
        WelcomeStep("O que vamos aprender?", "Pode ser uma lista de matérias, um curso ou uma habilidade nova. Esse será nosso ponto de partida.", listOf(ProfileQuestion.SUBJECTS)),
        WelcomeStep("De onde você parte?", "Tudo bem começar do zero ou retomar depois de um tempo. Conte como você se sente em relação a esses conteúdos.", listOf(ProfileQuestion.LEVEL)),
        WelcomeStep("Como é seu dia?", "Seu plano precisa respeitar a vida fora dos estudos. Conte os compromissos e os momentos que costuma ter livres.", listOf(ProfileQuestion.ROUTINE)),
        WelcomeStep("Quando cabe estudar?", "Vamos encontrar espaços possíveis, sem preencher cada minuto. Escolha os dias e o horário que funcionam melhor.", listOf(ProfileQuestion.DAYS, ProfileQuestion.TIME)),
        WelcomeStep("Qual é seu ritmo?", "Pequenos blocos também contam. Quanto tempo costuma caber no dia e quanto você gosta de estudar por vez?", listOf(ProfileQuestion.DAILY_MINUTES, ProfileQuestion.SESSION_MINUTES)),
        WelcomeStep("O que ajuda você?", "Quero conhecer o que funciona e o que costuma atrapalhar. Não existe uma resposta certa: podemos ajustar depois.", listOf(ProfileQuestion.PREFERENCES, ProfileQuestion.CHALLENGES)),
        WelcomeStep("Vamos deixar confortável?", "Estamos quase lá! Conte se alguma adaptação ajuda você. Confira seu objetivo abaixo antes de criar seu perfil.", listOf(ProfileQuestion.ACCESSIBILITY)),
    )
    val lastIndex get() = steps.lastIndex
    fun canContinue(step: Int, profile: StudentProfile): Boolean = when (step) {
        0 -> profile.name.isNotBlank()
        1 -> profile.goal.isNotBlank()
        lastIndex -> profile.name.isNotBlank() && profile.goal.isNotBlank()
        else -> step in steps.indices
    }
    fun canFinish(step: Int, profile: StudentProfile) = step == lastIndex && canContinue(step, profile)
    fun resumeAt(savedStep: Int, profile: StudentProfile): Int {
        val step = savedStep.coerceIn(0, lastIndex)
        return when {
            profile.name.isBlank() -> 0
            profile.goal.isBlank() -> minOf(step, 1)
            else -> step
        }
    }
}
