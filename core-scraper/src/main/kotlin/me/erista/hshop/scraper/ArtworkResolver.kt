package me.erista.hshop.scraper

import me.erista.hshop.model.ArtworkInfo
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object ArtworkResolver {

    fun normalizeGameTdbRegion(region: String?): String? {
        if (region.isNullOrBlank()) return null
        return when (region.trim().uppercase()) {
            "NTSC-U", "USA", "US" -> "US"
            "PAL", "EUR", "EUROPE", "EN", "UK" -> "EN"
            "NTSC-J", "JAP", "JAPAN", "JA", "JP" -> "JA"
            "NTSC-K", "KOR", "KOREA", "KO" -> "KO"
            "NTSC-C", "CHN", "CHINA", "TAIWAN", "ZH", "HK" -> "ZH"
            "FR", "FRANCE" -> "FR"
            "DE", "GERMANY" -> "DE"
            "IT", "ITALY" -> "IT"
            "ES", "SPAIN" -> "ES"
            "AU", "AUSTRALIA" -> "AU"
            else -> if (region.length == 2) region.uppercase() else null
        }
    }

    /**
     * Resolves artwork URLs from GameTDB and Libretro based on product code, subcategory, and title name.
     */
    fun resolveArtwork(
        name: String,
        productCode: String,
        subcategorySlug: String = "",
        overrideGameId: String? = null,
        overrideRegion: String? = null
    ): ArtworkInfo {
        val gameId = overrideGameId ?: extractGameId(productCode) ?: extractGameId(name)
        val regionCode = normalizeGameTdbRegion(overrideRegion) ?: inferGameTDBRegion(productCode, subcategorySlug)
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

        val box3dGameTdb = if (gameId != null) {
            "https://art.gametdb.com/3ds/box3d/$regionCode/$gameId.png"
        } else null

        // Only physical cartridge releases (CTR-P) have full box wrap scans
        val fullCoverGameTdb = if (gameId != null && isPhysicalCartridge) {
            "https://art.gametdb.com/3ds/coverfullHQ/$regionCode/$gameId.jpg"
        } else null

        // Generate fallbacks for alternative regions (US / EN / JA) and Libretro
        val fallbacks = mutableListOf<String>()
        if (gameId != null) {
            // High-speed CDN mirror from thor-3ds-db WebP archive
            fallbacks.add("https://cdn.jsdelivr.net/gh/nq-910/thor-3ds-db@main/covers/$gameId.webp")

            val altRegions = listOf("US", "EN", "JA", "KO").filter { it != regionCode }
            for (alt in altRegions) {
                fallbacks.add("https://art.gametdb.com/3ds/cover/$alt/$gameId.jpg")
                fallbacks.add("https://art.gametdb.com/3ds/box3d/$alt/$gameId.png")
            }
        }

        // Platform-specific Libretro thumbnails (3DS, SNES, NES, GB, GBC, GBA)
        val platformRepo = inferLibretroPlatformRepo(productCode)
        val libretroTags = when (regionCode) {
            "US" -> listOf(" (USA)", " (Europe)", " (World)", "")
            "EN" -> listOf(" (Europe)", " (USA)", " (World)", "")
            "JA" -> listOf(" (Japan)", " (USA)", "")
            "KO" -> listOf(" (Korea)", " (USA)", "")
            "ZH" -> listOf(" (Taiwan)", " (Hong Kong)", " (China)", " (USA)", "")
            else -> listOf(" (USA)", " (Europe)", " (Japan)", "")
        }
        for (tag in libretroTags) {
            val libretroName = sanitizeForLibretro(name, tag)
            fallbacks.add("https://raw.githubusercontent.com/libretro-thumbnails/$platformRepo/master/Named_Boxarts/$libretroName.png")
            if (platformRepo != "Nintendo_-_Nintendo_3DS") {
                fallbacks.add("https://raw.githubusercontent.com/libretro-thumbnails/Nintendo_-_Nintendo_3DS/master/Named_Boxarts/$libretroName.png")
            }
        }

        return ArtworkInfo(
            primaryCoverUrl = primaryGameTdb ?: fallbacks.firstOrNull(),
            thumbnailCoverUrl = thumbGameTdb,
            highResCoverUrl = hqGameTdb,
            fullCoverWrapUrl = fullCoverGameTdb,
            box3dUrl = box3dGameTdb,
            fallbackUrls = fallbacks.distinct(),
            source = if (gameId != null) "GameTDB" else "Libretro"
        )
    }

    private fun sanitizeForLibretro(name: String, regionTag: String): String {
        val cleanBaseName = name.replace(Regex("™|®|©"), "")
            .replace(Regex("(?i)\\s*\\(usa\\)"), "")
            .replace(Regex("(?i)\\s*\\(europe\\)"), "")
            .replace(Regex("(?i)\\s*\\(japan\\)"), "")
            .trim()

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
    /**
     * Extracts the 4-letter Game ID from a 3DS Product Code.
     * e.g., CTR-P-AMKJ -> AMKJ, CTR-N-RA5E -> RA5E, KTR-N-UADJ -> UADJ
     */
    fun extractGameId(productCode: String): String? {
        val clean = productCode.trim()
        if (clean.isEmpty()) return null

        val bracketMatch = Regex("\\[([A-Z0-9-]+)\\]").find(clean)
        val target = bracketMatch?.groupValues?.get(1) ?: clean

        val parts = target.split("-")
        if (parts.size >= 3) {
            val lastPart = parts.last()
            if (lastPart.length >= 4) {
                return lastPart.take(4).uppercase()
            }
        }
        val match = Regex("(?<=^|[^A-Z0-9])([A-Z0-9]{4})(?=$|[^A-Z0-9])").find(target)
        if (match != null) {
            return match.groupValues[1].uppercase()
        }
        val endMatch = Regex("[A-Z0-9]{4}$").find(target)
        return endMatch?.value?.uppercase()
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
}
