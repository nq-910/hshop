package me.erista.hshop.scraper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.erista.hshop.model.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import java.util.concurrent.TimeUnit

class HShopScraper(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build(),
    private val baseUrl: String = "https://hshop.erista.me"
) {

    private val userAgent = "Mozilla/5.0 (Linux; Android 13; AYN Thor) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

    /**
     * Fetches all subcategories for a given category (e.g. "games", "updates", "dlc").
     */
    suspend fun fetchSubcategories(category: HShopCategory): List<HShopSubcategory> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/c/${category.slug}"
        val doc = getDocument(url)
        val list = mutableListOf<HShopSubcategory>()

        val entries = doc.select(".elements > a.list-entry")
        for (entry in entries) {
            val href = entry.attr("href")
            val slug = href.substringAfterLast("/")
            val name = entry.selectFirst(".base-info h3")?.text().orEmpty()
            val desc = entry.selectFirst(".base-info h4")?.text().orEmpty()

            var sizeStr = ""
            var titleCount = 0
            var officialCount = 0
            var downloadCount = 0L

            for (meta in entry.select(".meta-content")) {
                val label = meta.select("span").last()?.text()?.lowercase().orEmpty()
                val value = meta.select("span").first()?.text().orEmpty()

                when {
                    label.contains("size") -> sizeStr = value
                    label.contains("titles") -> titleCount = value.replace(",", "").toIntOrNull() ?: 0
                    label.contains("official") -> officialCount = value.replace(",", "").toIntOrNull() ?: 0
                    label.contains("downloads") -> downloadCount = value.replace(",", "").toLongOrNull() ?: 0L
                }
            }

            list.add(
                HShopSubcategory(
                    slug = slug,
                    name = name,
                    description = desc,
                    categorySlug = category.slug,
                    sizeString = sizeStr,
                    titleCount = titleCount,
                    officialCount = officialCount,
                    downloadCount = downloadCount
                )
            )
        }
        list
    }

    /**
     * Fetches titles within a specific category and subcategory.
     */
    suspend fun fetchCategoryTitles(
        categorySlug: String,
        subcategorySlug: String,
        count: Int = 25,
        offset: Int = 0
    ): List<HShopTitleSummary> = withContext(Dispatchers.IO) {
        val urlBuilder = "$baseUrl/c/$categorySlug/s/$subcategorySlug".toHttpUrl().newBuilder()
        if (count != 25) urlBuilder.addQueryParameter("count", count.toString())
        if (offset > 0) urlBuilder.addQueryParameter("offset", offset.toString())

        val doc = getDocument(urlBuilder.build().toString())
        parseTitleSummaries(doc, defaultCategory = categorySlug, defaultSubcategory = subcategorySlug)
    }

    /**
     * Searches for titles matching a query with optional filters and sorting.
     */
    suspend fun searchTitles(
        query: String,
        queryType: String = "Text", // "Text", "TitleID", "ProductCode", "UniqueID"
        categorySlug: String? = null,
        subcategorySlug: String? = null,
        sort: String? = null, // "size", "name", "category", "added_date", "downloads"
        order: String? = null, // "ascending", "descending"
        contentType: String? = null, // "standard", "legit", "piratelegit"
        count: Int = 25,
        offset: Int = 0
    ): HShopSearchResult = withContext(Dispatchers.IO) {
        val normalizedCount = when {
            count <= 10 -> 10
            count <= 25 -> 25
            count <= 50 -> 50
            else -> 100
        }

        val urlBuilder = "$baseUrl/search/results".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("qt", queryType)
            .addQueryParameter("count", normalizedCount.toString())

        if (offset > 0) urlBuilder.addQueryParameter("offset", offset.toString())
        
        if (!categorySlug.isNullOrEmpty() && categorySlug != "none") {
            val filter = if (!subcategorySlug.isNullOrEmpty() && subcategorySlug != "none") {
                "$categorySlug.$subcategorySlug"
            } else {
                "$categorySlug.*"
            }
            urlBuilder.addQueryParameter("i", filter)
        }
        
        if (!sort.isNullOrEmpty() && sort != "none") urlBuilder.addQueryParameter("sb", sort)
        if (!order.isNullOrEmpty() && order != "none") urlBuilder.addQueryParameter("sd", order)
        if (!contentType.isNullOrEmpty() && contentType != "none") urlBuilder.addQueryParameter("p", contentType)

        val doc = getDocument(urlBuilder.build().toString())

        var totalCount = 0
        val countText = doc.selectFirst(".next-container > span")?.text().orEmpty()
        val totalMatch = Regex("of\\s+([0-9,]+)").find(countText)
        if (totalMatch != null) {
            totalCount = totalMatch.groupValues[1].replace(",", "").toIntOrNull() ?: 0
        }

        val titles = parseTitleSummaries(doc, defaultCategory = categorySlug.orEmpty(), defaultSubcategory = subcategorySlug.orEmpty())
        val nextLink = doc.selectFirst(".next a[href*='offset=']")
        val nextOffset = if (nextLink != null) {
            val href = nextLink.attr("href")
            Regex("offset=([0-9]+)").find(href)?.groupValues?.get(1)?.toIntOrNull()
        } else null

        HShopSearchResult(
            query = query,
            totalCount = if (totalCount > 0) totalCount else titles.size,
            offset = offset,
            count = count,
            titles = titles,
            nextOffset = nextOffset
        )
    }

    /**
     * Scrapes full detail for a single title (/t/{id}).
     */
    suspend fun fetchTitleDetail(id: String): HShopTitleDetail = withContext(Dispatchers.IO) {
        val cleanId = id.trim().removePrefix("/t/")
        val url = "$baseUrl/t/$cleanId"
        val doc = getDocument(url)

        val name = doc.selectFirst(".title-name h2")?.text().orEmpty()
        val breadcrumbs = doc.select(".title-name h3 a")
        val categorySlug = if (breadcrumbs.isNotEmpty()) breadcrumbs[0].attr("href").substringAfterLast("/") else ""
        val subcategorySlug = if (breadcrumbs.size > 1) breadcrumbs[1].attr("href").substringAfterLast("/") else ""

        var titleId = ""
        var productCode = ""
        var version = ""
        var sizeString = ""
        var contentType = ""
        var addedDate = ""
        var updatedDate = ""
        var downloadCount = 0L
        var seed: String? = null
        var sha256: String? = null

        val basicMetaParas = doc.select(".basic-meta p")
        for (p in basicMetaParas) {
            val text = p.text()
            when {
                text.startsWith("Title ID:") -> titleId = p.select("span").text().trim()
                text.startsWith("Product Code:") -> productCode = p.select("span").text().trim()
                text.startsWith("Version:") -> version = p.select("span").text().trim()
                text.startsWith("Size:") -> sizeString = p.select("span").text().trim()
                text.startsWith("Content Type:") -> contentType = p.select("span").text().trim()
                text.startsWith("Added:") -> addedDate = p.select("span").text().trim()
                text.startsWith("Updated:") -> updatedDate = p.select("span").text().trim()
                text.startsWith("Downloads:") -> downloadCount = p.select("span").text().replace(",", "").toLongOrNull() ?: 0L
                text.startsWith("Seed:") -> seed = p.select("span").text().trim()
                text.startsWith("SHA-256 Hash:") -> sha256 = p.select("span").text().trim()
            }
        }

        val desc = doc.selectFirst(".description-container p")?.text()?.takeIf { !it.contains("No description is available", ignoreCase = true) }.orEmpty()

        val relatedList = mutableListOf<RelatedContentSummary>()
        for (rel in doc.select(".related a.list-entry")) {
            val relId = rel.attr("href").substringAfterLast("/")
            val relName = rel.selectFirst(".base-info h3")?.text().orEmpty()
            val relType = rel.selectFirst(".base-info .meta-content span")?.text()?.replace("Relation:", "")?.trim().orEmpty()

            var rTitleId = ""
            var rProdCode = ""
            var rVersion = ""
            var rSize = ""
            var rContentType = ""

            for (m in rel.select(".meta-content")) {
                val label = m.select("span").last()?.text()?.lowercase().orEmpty()
                val value = m.select("span").first()?.text().orEmpty()
                when {
                    label.contains("title id") -> rTitleId = value
                    label.contains("product code") -> rProdCode = value
                    label.contains("version") -> rVersion = value
                    label.contains("size") -> rSize = value
                    label.contains("content type") -> rContentType = value
                }
            }

            relatedList.add(
                RelatedContentSummary(
                    id = relId,
                    name = relName,
                    relationType = relType,
                    titleId = rTitleId,
                    productCode = rProdCode,
                    version = rVersion,
                    sizeString = rSize,
                    contentType = rContentType
                )
            )
        }

        val artwork = ArtworkResolver.resolveArtwork(name, productCode, subcategorySlug)

        HShopTitleDetail(
            id = cleanId,
            name = name,
            categorySlug = categorySlug,
            subcategorySlug = subcategorySlug,
            titleId = titleId,
            productCode = productCode,
            version = version,
            sizeString = sizeString,
            contentType = contentType,
            addedDate = addedDate,
            updatedDate = updatedDate,
            downloadCount = downloadCount,
            seed = seed,
            sha256 = sha256,
            description = desc,
            relatedContent = relatedList,
            artwork = artwork
        )
    }

    private fun parseTitleSummaries(
        doc: Document,
        defaultCategory: String = "",
        defaultSubcategory: String = ""
    ): List<HShopTitleSummary> {
        val list = mutableListOf<HShopTitleSummary>()
        // Only select entries from the first .elements container (main list) to avoid duplicate "Top content"
        val mainElementsContainer = doc.selectFirst(".elements") ?: doc
        val entries = mainElementsContainer.select("a.list-entry")

        for (entry in entries) {
            val id = entry.attr("href").substringAfterLast("/")
            val name = entry.selectFirst(".base-info h3")?.text()?.replace(Regex("^[0-9]+\\.\\s*"), "").orEmpty()

            var cat = defaultCategory
            var subcat = defaultSubcategory

            val pathInfo = entry.selectFirst(".base-info h4")?.text().orEmpty()
            if (pathInfo.contains("content in", ignoreCase = true)) {
                val spans = entry.select(".base-info h4 span.bold")
                if (spans.size >= 2) {
                    cat = spans[0].text().trim()
                    subcat = spans[1].text().trim()
                }
            }

            var titleId = ""
            var productCode = ""
            var version = ""
            var sizeString = ""
            var contentType = ""

            for (m in entry.select(".meta > .meta-content")) {
                val label = m.select("span").last()?.text()?.lowercase().orEmpty()
                val value = m.select("span").first()?.text().orEmpty()

                when {
                    label.contains("title id") -> titleId = value
                    label.contains("product code") -> productCode = value
                    label.contains("version") -> version = value
                    label.contains("size") -> sizeString = value
                    label.contains("content type") -> contentType = value
                }
            }

            val artwork = ArtworkResolver.resolveArtwork(name, productCode, subcat)

            list.add(
                HShopTitleSummary(
                    id = id,
                    name = name,
                    categorySlug = cat,
                    subcategorySlug = subcat,
                    titleId = titleId,
                    productCode = productCode,
                    version = version,
                    sizeString = sizeString,
                    contentType = contentType,
                    artwork = artwork
                )
            )
        }
        return list
    }

    private fun getDocument(url: String): Document {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("Failed to fetch $url: HTTP ${response.code}")
        }
        val html = response.body?.string().orEmpty()
        return Jsoup.parse(html, baseUrl)
    }
}
