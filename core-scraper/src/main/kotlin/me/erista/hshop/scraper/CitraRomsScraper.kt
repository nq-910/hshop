package me.erista.hshop.scraper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.IOException

@Serializable
data class CitraRomItem(
    val title: String,
    val pageUrl: String,
    val imageUrl: String? = null,
    val cartSize: String? = null,
    val version: String? = null,
    val region: String? = null,
    val genre: String? = null,
    val publisher: String? = null,
    val developer: String? = null,
    val releaseDate: String? = null,
    val description: String? = null
)

class CitraRomsScraper(
    private val client: OkHttpClient = OkHttpClient.Builder().build(),
    private val baseUrl: String = "https://citra-emulator.com"
) {

    suspend fun fetchRomList(pageUrl: String = "$baseUrl/3ds-roms"): List<CitraRomItem> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(pageUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; AYN Thor) AppleWebKit/537.36")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IOException("Unexpected HTTP response: ${response.code}")
            }

            val html = response.body?.string().orEmpty()
            val doc = Jsoup.parse(html, baseUrl)
            val items = mutableListOf<CitraRomItem>()

            // Extract articles / rom cards
            val elements = doc.select("article, .post, h3 > a[href*='/3ds-roms/']")
            for (el in elements) {
                val titleAnchor = if (el.tagName() == "a") el else el.selectFirst("h2 a, h3 a, a[href*='/3ds-roms/']")
                if (titleAnchor != null) {
                    val title = titleAnchor.text().trim()
                    val href = titleAnchor.absUrl("href")
                    if (title.isNotEmpty() && href.contains("/3ds-roms/") && href != "$baseUrl/3ds-roms" && href != "$baseUrl/3ds-roms/") {
                        val img = el.selectFirst("img")?.absUrl("src")
                        items.add(CitraRomItem(title = title, pageUrl = href, imageUrl = img))
                    }
                }
            }

            items.distinctBy { it.pageUrl }
        }

    suspend fun fetchRomDetail(pageUrl: String): CitraRomItem = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(pageUrl)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; AYN Thor) AppleWebKit/537.36")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("Unexpected HTTP response: ${response.code}")
        }

        val html = response.body?.string().orEmpty()
        val doc = Jsoup.parse(html, pageUrl)

        val title = doc.selectFirst("h1, title")?.text()?.replace(" ROM (3DS)", "")?.trim().orEmpty()
        val image = doc.selectFirst("article img, .entry-content img")?.absUrl("src")

        var cartSize: String? = null
        var version: String? = null
        var region: String? = null
        var genre: String? = null
        var publisher: String? = null
        var developer: String? = null
        var releaseDate: String? = null

        // Parse key-value specifications from text/paragraphs
        val paragraphs = doc.select("p, div, li")
        for (p in paragraphs) {
            val text = p.text()
            when {
                text.startsWith("Cart Size", ignoreCase = true) -> cartSize = text.substringAfter("Cart Size").trim()
                text.startsWith("Version", ignoreCase = true) -> version = text.substringAfter("Version").trim()
                text.startsWith("Region", ignoreCase = true) -> region = text.substringAfter("Region").trim()
                text.startsWith("Genre", ignoreCase = true) -> genre = text.substringAfter("Genre").trim()
                text.startsWith("Publisher", ignoreCase = true) -> publisher = text.substringAfter("Publisher").trim()
                text.startsWith("Developer", ignoreCase = true) -> developer = text.substringAfter("Developer").trim()
                text.startsWith("Release Date", ignoreCase = true) -> releaseDate = text.substringAfter("Release Date").trim()
            }
        }

        val description = doc.select("article p, .entry-content p")
            .filter { it.text().length > 40 && !it.text().contains("Download", ignoreCase = true) }
            .joinToString("\n\n") { it.text().trim() }

        CitraRomItem(
            title = title,
            pageUrl = pageUrl,
            imageUrl = image,
            cartSize = cartSize,
            version = version,
            region = region,
            genre = genre,
            publisher = publisher,
            developer = developer,
            releaseDate = releaseDate,
            description = description.ifEmpty { null }
        )
    }
}
