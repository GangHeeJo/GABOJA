package kr.mysterydrive.core

/** What the user said they want. Null means "not mentioned", so it keeps the current value. */
data class ConditionsPatch(val timeMin: Int? = null, val budget: Int? = null, val mood: String? = null, val who: String? = null)

fun Conditions.merge(p: ConditionsPatch) = copy(
    timeMin = p.timeMin ?: timeMin, budget = p.budget ?: budget, mood = p.mood ?: mood, who = p.who ?: who,
)

/** Turns a spoken sentence (from SpeechToText) into conditions. */
fun parseVoice(t: String): ConditionsPatch {
    var time: Int? = Regex("(\\d+)\\s*시간").find(t)?.let { it.groupValues[1].toInt() * 60 }
    if ("반나절" in t) time = 360
    if (Regex("1박\\s*2일").containsMatchIn(t)) time = 1200
    val budget = Regex("(\\d+)\\s*만\\s*원").find(t)?.let { it.groupValues[1].toInt() * 10_000 }
    val mood = when {
        Regex("자연|풍경|산책").containsMatchIn(t) -> "자연"
        Regex("맛집|맛있").containsMatchIn(t) -> "맛집"
        Regex("조용|휴식|쉬").containsMatchIn(t) -> "휴식"
        Regex("문화|전시").containsMatchIn(t) -> "문화"
        Regex("아무|랜덤").containsMatchIn(t) -> "랜덤"
        else -> null
    }
    val who = listOf("혼자", "연인", "친구", "가족").firstOrNull { it in t }
    return ConditionsPatch(time, budget, mood, who)
}

/** A route change asked for on the way. */
sealed interface Cmd {
    data object Swap : Cmd
    data object Skip : Cmd
    data object Home : Cmd
    data object Charge : Cmd
    data class Kind(val kind: String) : Cmd
}

fun parseCmd(t: String): Cmd? = when {
    Regex("충전소|주유소").containsMatchIn(t) -> Cmd.Charge
    Regex("집|귀가").containsMatchIn(t) -> Cmd.Home
    Regex("건너|스킵").containsMatchIn(t) -> Cmd.Skip
    Regex("쉬|피곤").containsMatchIn(t) -> Cmd.Kind("휴식")
    Regex("비가|비 |실내").containsMatchIn(t) -> Cmd.Kind("문화")
    Regex("바꿔|다른").containsMatchIn(t) -> Cmd.Swap
    else -> null
}
