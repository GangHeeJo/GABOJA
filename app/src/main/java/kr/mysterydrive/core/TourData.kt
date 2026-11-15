package kr.mysterydrive.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * Place details from the Korea Tourism Organization TourAPI, collected ahead of time and shipped inside the app
 * (assets/tour.json + photos). The app works offline and needs no API key.
 * [TourPhoto.credit], when set, replaces the default Korea Tourism Organization credit (used for Wikimedia photos).
 */
data class TourPhoto(val path: String, val license: String, val credit: String? = null) {
    val caption: String get() = credit ?: photoCredit(license)
}

data class NearSpot(
    val type: String, val name: String, val distM: Int, val addr: String, val tel: String,
    val menu: String, val hours: String, val rest: String, val parking: String,
    val photoPath: String?, val license: String, val photoCredit: String?,
) {
    val caption: String get() = photoCredit ?: photoCredit(license)
    val distLabel: String get() = if (distM >= 1000) "%.1fkm".format(distM / 1000.0) else "${distM}m"
}

data class TourPlace(
    val title: String, val overview: String, val overviewSource: String, val tel: String, val addr: String,
    val usetime: String, val restdate: String, val parking: String,
    val photos: List<TourPhoto>, val near: List<NearSpot>,
)

class TourData(private val byId: Map<String, TourPlace>) {
    operator fun get(placeId: String): TourPlace? = byId[placeId]
    val ids: Set<String> get() = byId.keys

    companion object {
        fun fromJson(text: String): TourData {
            val root = JSONObject(text)
            return TourData(root.keys().asSequence().associateWith { place(root.getJSONObject(it)) })
        }

        private fun JSONObject.str(key: String) = optString(key, "")
        private fun asset(path: String?) = path?.takeIf { it.isNotBlank() && it != "null" }?.removePrefix("mapdata/")
        private fun <T> JSONArray?.map(f: (JSONObject) -> T): List<T> = if (this == null) emptyList() else List(length()) { f(getJSONObject(it)) }

        private fun place(o: JSONObject) = TourPlace(
            title = o.str("title"), overview = o.str("overview"), overviewSource = o.str("overviewSource").ifEmpty { "한국관광공사" },
            tel = o.str("tel"), addr = o.str("addr"), usetime = o.str("usetime"), restdate = o.str("restdate"), parking = o.str("parking"),
            photos = o.optJSONArray("photos").map { TourPhoto(asset(it.str("file")) ?: "", it.str("license"), it.str("credit").ifEmpty { null }) }.filter { it.path.isNotEmpty() },
            near = o.optJSONArray("near").map {
                NearSpot(it.str("type"), it.str("name"), it.optInt("dist"), it.str("addr"), it.str("tel"), it.str("menu"), it.str("hours"),
                    it.str("rest"), it.str("parking"), asset(it.optString("photo")), it.str("license"), it.str("photoCredit").ifEmpty { null })
            },
        )
    }
}

/** Korea Open Government License (공공누리) type shown under each photo. */
fun licenseLabel(code: String): String? = when (code) {
    "Type1" -> "공공누리 1유형(출처표시)"
    "Type2" -> "공공누리 2유형(출처표시·상업적 이용금지)"
    "Type3" -> "공공누리 3유형(출처표시·변경금지)"
    "Type4" -> "공공누리 4유형(출처표시·상업적 이용금지·변경금지)"
    else -> null
}

fun photoCredit(license: String) = "사진: 한국관광공사 TourAPI" + (licenseLabel(license)?.let { " · $it" } ?: "")
