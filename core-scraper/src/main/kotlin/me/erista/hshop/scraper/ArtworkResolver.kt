package me.erista.hshop.scraper

import me.erista.hshop.model.ArtworkInfo
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object ArtworkResolver {

    /**
     * Resolves artwork URLs from GameTDB and Libretro based on product code, subcategory, and title name.
     */
    fun resolveArtwork(
        name: String,
        productCode: String,
        subcategorySlug: String
    ): ArtworkInfo {
        val gameId = extractGameId(productCode)
        val regionCode = inferGameTDBRegion(productCode, subcategorySlug)
        val isPhysicalCartridge = productCode.startsWith("CTR-P-", ignoreCase = true)

        val primaryGameTdb = if (gameId != null) {
            "https://art.gametdb.com/3ds/cover/$regionCode/$gameId.jpg"
        } else null

        val thumbGameTdb = if (gameId != null) {
            "https://art.gametdb.com/3ds/coverM/$regionCode/$gameId.jpg"
        } else null

        val hqGameTdb = if (gameId != null) {
            "https://art.gametdb.com/3ds/coverHQ/$regionCode/$gameId.jpg"
        } else null

        // Only physical cartridge releases (CTR-P) have full box wrap scans
        val fullCoverGameTdb = if (gameId != null && isPhysicalCartridge) {
            "https://art.gametdb.com/3ds/coverfullHQ/$regionCode/$gameId.jpg"
        } else null

        // Generate fallbacks for alternative regions (US / EN / JA) and Libretro
        val fallbacks = mutableListOf<String>()
        if (gameId != null) {
            val altRegions = listOf("US", "EN", "JA", "KO").filter { it != regionCode }
            for (alt in altRegions) {
                fallbacks.add("https://art.gametdb.com/3ds/cover/$alt/$gameId.jpg")
            }
        }

        // Platform-specific Libretro thumbnails (3DS, SNES, NES, GB, GBC, GBA)
        val platformRepo = inferLibretroPlatformRepo(productCode)
        val libretroName = sanitizeForLibretro(name, subcategorySlug)
        fallbacks.add("https://raw.githubusercontent.com/libretro-thumbnails/$platformRepo/master/Named_Boxarts/$libretroName.png")
        if (platformRepo != "Nintendo_-_Nintendo_3DS") {
            fallbacks.add("https://raw.githubusercontent.com/libretro-thumbnails/Nintendo_-_Nintendo_3DS/master/Named_Boxarts/$libretroName.png")
        }

        return ArtworkInfo(
            primaryCoverUrl = primaryGameTdb ?: fallbacks.firstOrNull(),
            thumbnailCoverUrl = thumbGameTdb,
            highResCoverUrl = hqGameTdb,
            fullCoverWrapUrl = fullCoverGameTdb,
            fallbackUrls = fallbacks,
            source = if (gameId != null) "GameTDB" else "Libretro"
        )
    }

    /**
     * Extracts the 4-letter Game ID from a 3DS Product Code.
     * e.g., CTR-P-AMKJ -> AMKJ, CTR-N-RA5E -> RA5E, KTR-N-UADJ -> UADJ
     */
    fun extractGameId(productCode: String): String? {
        val clean = productCode.trim()
        if (clean.isEmpty()) return null

        val parts = clean.split("-")
        if (parts.size >= 3) {
            val lastPart = parts.last()
            if (lastPart.length >= 4) {
                return lastPart.take(4).uppercase()
            }
        }
        val match = Regex("[A-Z0-9]{4}$").find(clean)
        return match?.value?.uppercase()
    }

    /**
     * Infers the GameTDB region code by checking the 4th letter of the Game ID (Nintendo convention)
     * or falling back to the hShop subcategory slug.
     */
    fun inferGameTDBRegion(productCode: String, subcategorySlug: String): String {
        val gameId = extractGameId(productCode)
        if (gameId != null && gameId.length == 4) {
            val regionChar = gameId[3]
            when (regionChar) {
                'E' -> return "US" // North America / USA
                'J' -> return "JA" // Japan
                'P', 'V', 'X', 'Y', 'S', 'D', 'F', 'I', 'U' -> return "EN" // Europe / Australia
                'K', 'Z' -> return "KO" // Korea
                'W', 'T' -> return "ZH" // Taiwan / Hong Kong / China
                'A' -> return "US" // Region Free / World
            }
        }

        return mapSubcategoryToGameTDBRegion(subcategorySlug)
    }

    fun mapSubcategoryToGameTDBRegion(subcategorySlug: String): String {
        return when (subcategorySlug.lowercase()) {
            "north-america", "usa", "us" -> "US"
            "europe", "uk", "united-kingdom" -> "EN"
            "france" -> "FR"
            "germany" -> "DE"
            "italy" -> "IT"
            "spain" -> "ES"
            "netherlands" -> "NL"
            "russia" -> "RU"
            "australia" -> "AU"
            "japan", "jp" -> "JA"
            "korea", "ko" -> "KO"
            "china", "taiwan" -> "ZH"
            else -> "US"
        }
    }

    /**
     * Infers the correct Libretro repository name for Virtual Console / native 3DS titles.
     */
    fun inferLibretroPlatformRepo(productCode: String): String {
        val gameId = extractGameId(productCode).orEmpty()
        return when {
            productCode.startsWith("KTR-N-") || gameId.startsWith("UA") || gameId.startsWith("UB") ->
                "Nintendo_-_Super_Nintendo_Entertainment_System"
            gameId.startsWith("TA") || gameId.startsWith("TB") ->
                "Nintendo_-_Nintendo_Entertainment_System"
            gameId.startsWith("RA") ->
                "Nintendo_-_Game_Boy"
            gameId.startsWith("QA") ->
                "Nintendo_-_Game_Boy_Color"
            gameId.startsWith("PA") ->
                "Nintendo_-_Game_Boy_Advance"
            gameId.startsWith("LA") ->
                "Sega_-_Game_Gear"
            else ->
                "Nintendo_-_Nintendo_3DS"
        }
    }

    private fun sanitizeForLibretro(name: String, subcategorySlug: String): String {
        val cleanBaseName = name.replace(Regex("™|®|©"), "").trim()
        val regionTag = when (subcategorySlug.lowercase()) {
            "north-america", "usa", "us" -> " (USA)"
            "europe" -> " (Europe)"
            "japan", "jp" -> " (Japan)"
            "germany" -> " (Germany)"
            "france" -> " (France)"
            "spain" -> " (Spain)"
            "italy" -> " (Italy)"
            "world" -> " (World)"
            else -> ""
        }
        val cleanName = (cleanBaseName + regionTag)
            .replace("&", "_")
            .replace(":", "_")
            .replace("/", "_")
            .replace("\\", "_")
            .replace("*", "_")
            .replace("?", "_")
            .replace("\"", "_")
            .replace("<", "_")
            .replace(">", "_")
            .replace("|", "_")
        return URLEncoder.encode(cleanName, StandardCharsets.UTF_8.toString()).replace("+", "%20")
    }
}
