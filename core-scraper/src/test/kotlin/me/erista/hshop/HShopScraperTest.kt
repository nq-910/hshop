package me.erista.hshop

import kotlinx.coroutines.runBlocking
import me.erista.hshop.model.HShopCategory
import me.erista.hshop.scraper.ArtworkResolver
import me.erista.hshop.scraper.HShopScraper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HShopScraperTest {

    private val scraper = HShopScraper()

    @Test
    fun testArtworkResolverGameIdExtraction() {
        assertEquals("A2AA", ArtworkResolver.extractGameId("CTR-P-A2AA"))
        assertEquals("AMKJ", ArtworkResolver.extractGameId("CTR-P-AMKJ"))
        assertEquals("TAEE", ArtworkResolver.extractGameId("CTR-N-TAEE"))
        assertEquals("ZOLB", ArtworkResolver.extractGameId("CTR-P-ZOLB"))
    }

    @Test
    fun testArtworkResolverRegionInference() {
        assertEquals("US", ArtworkResolver.inferGameTDBRegion("CTR-P-AMKE", "north-america"))
        assertEquals("JA", ArtworkResolver.inferGameTDBRegion("CTR-P-AMKJ", "japan"))
        assertEquals("EN", ArtworkResolver.inferGameTDBRegion("CTR-P-AMKP", "europe"))
        assertEquals("KO", ArtworkResolver.inferGameTDBRegion("CTR-N-RA5Z", "korea"))
    }

    @Test
    fun testArtworkResolverUrlGeneration() {
        val artwork = ArtworkResolver.resolveArtwork(
            name = "Mario Kart 7",
            productCode = "CTR-P-AMKE",
            subcategorySlug = "north-america"
        )
        assertNotNull(artwork.primaryCoverUrl)
        assertEquals("https://art.gametdb.com/3ds/cover/US/AMKE.jpg", artwork.primaryCoverUrl)
        assertEquals("https://art.gametdb.com/3ds/coverHQ/US/AMKE.jpg", artwork.highResCoverUrl)
    }

    @Test
    fun testLiveFetchGamesSubcategories() = runBlocking {
        val subcategories = scraper.fetchSubcategories(HShopCategory.GAMES)
        assertFalse(subcategories.isEmpty(), "Subcategories list should not be empty")

        val na = subcategories.find { it.slug == "north-america" }
        assertNotNull(na, "North America subcategory should be found")
        assertTrue(na!!.titleCount > 1000, "North America should have over 1000 titles")
    }

    @Test
    fun testLiveSearchTitles() = runBlocking {
        val result = scraper.searchTitles(query = "Pokemon", count = 10)
        assertTrue(result.totalCount > 0, "Search for Pokemon should return results")
        assertFalse(result.titles.isEmpty(), "Titles list should not be empty")

        val first = result.titles.first()
        assertFalse(first.name.isEmpty())
        assertFalse(first.titleId.isEmpty())
        assertNotNull(first.artwork?.primaryCoverUrl)
    }

    @Test
    fun testLiveFetchTitleDetail() = runBlocking {
        val detail = scraper.fetchTitleDetail("632") // Pokemon Ultra Sun
        assertEquals("632", detail.id)
        assertEquals("Pokemon Ultra Sun", detail.name)
        assertEquals("00040000001B5000", detail.titleId)
        assertEquals("CTR-P-A2AA", detail.productCode)
        assertNotNull(detail.sha256)
        assertNotNull(detail.seed)
        assertFalse(detail.relatedContent.isEmpty(), "Should have related update data")
    }
}
