package kr.mysterydrive.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TourDataTest {
    private val assets = File("src/main/assets")
    private val tour = TourData.fromJson(File(assets, "tour.json").readText())

    @Test fun everyRealPlaceHasDetailsAndPhotos() {
        for (p in PLACES.filter { !it.charger }) {
            val t = tour[p.id]
            assertNotNull("${p.id} missing from tour.json", t)
            assertTrue("${p.id} has no photo", t!!.photos.isNotEmpty() || p.id == "chc")
        }
    }

    @Test fun everyReferencedImageIsShippedInAssets() {
        for (id in tour.ids) {
            val t = tour[id]!!
            for (path in t.photos.map { it.path } + t.near.mapNotNull { it.photoPath }) assertTrue("$id: $path not in assets", File(assets, path).isFile)
        }
    }

    @Test fun nearbySpotsAreSortedIntoKnownTypes() {
        for (id in tour.ids) for (n in tour[id]!!.near) assertTrue("${n.type} / ${n.name}", n.type in listOf("맛집", "카페", "사진") && n.name.isNotBlank())
    }

    @Test fun licenseLabelsAndCredit() {
        assertEquals("공공누리 1유형(출처표시)", licenseLabel("Type1"))
        assertNull(licenseLabel(""))
        assertEquals("사진: 한국관광공사 TourAPI · 공공누리 3유형(출처표시·변경금지)", photoCredit("Type3"))
        assertEquals("사진: 한국관광공사 TourAPI", photoCredit(""))
    }

    @Test fun noApiKeyIsShippedInAssets() { // the key must stay in local.properties, never in the APK or the repo
        val key = File("../local.properties").takeIf { it.exists() }?.readLines()?.firstOrNull { it.startsWith("tourapi.key=") }?.substringAfter("=")?.trim()
        if (key.isNullOrBlank()) return
        val decoded = java.net.URLDecoder.decode(key, "UTF-8")
        assets.walkTopDown().filter { it.isFile && it.extension in listOf("json", "txt", "xml", "properties") }.forEach {
            val text = it.readText()
            assertTrue("API key found in ${it.name}", key !in text && decoded !in text)
        }
    }
}
