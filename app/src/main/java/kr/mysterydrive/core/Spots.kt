package kr.mysterydrive.core

/** One thing to do near a stop: a restaurant, a cafe or a sight. */
data class Spot(
    val type: String, val name: String, val note: String, val desc: String, val minutes: Int,
    val photoPath: String?, val caption: String?, val real: Boolean,
    val hours: String = "", val rest: String = "", val parking: String = "", val tel: String = "", val addr: String = "",
)

/** The internal type "사진" is shown to people as "명소". */
val KIND_LABEL = mapOf("맛집" to "맛집", "카페" to "카페", "사진" to "명소")
const val NEAR_TYPES = "맛집,카페,사진"
private val EST_MIN = mapOf("맛집" to 50, "카페" to 30, "사진" to 30) // TourAPI has no visit length, these are rough per-type guesses

private val STATION_SPOTS = listOf(
    Spot("맛집", "휴게소 푸드코트", "도보 1분", "쉬는 동안 식사하기 좋아요.", 30, null, null, false),
    Spot("카페", "휴게소 카페", "도보 1분", "쉬면서 커피 한 잔 할 수 있어요.", 20, null, null, false),
    Spot("사진", "전망 쉼터", "도보 3분", "잠깐 쉬어 가는 전망 쉼터예요.", 10, null, null, false),
)

/** Real neighbours from the shipped TourAPI data; stations get a small labelled sample list. */
fun spotsFor(placeId: String, tour: TourData): List<Spot> {
    val near = tour[placeId]?.near.orEmpty()
    if (near.isNotEmpty()) return near.map {
        Spot(it.type, it.name, it.distLabel, if (it.menu.isNotBlank()) "대표 메뉴: ${it.menu}" else it.addr, EST_MIN[it.type] ?: 30,
            it.photoPath, if (it.photoPath != null) it.caption else null, true, it.hours, it.rest, it.parking, it.tel, it.addr)
    }
    return if (PLACES.any { it.id == placeId && it.charger }) STATION_SPOTS else emptyList()
}
