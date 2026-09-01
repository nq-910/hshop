package me.erista.hshop.model

import kotlinx.serialization.Serializable

@Serializable
enum class HShopCategory(val slug: String, val displayName: String, val description: String) {
    GAMES("games", "Games", "Nintendo 3DS games"),
    UPDATES("updates", "Updates", "Update data for Nintendo 3DS titles"),
    DLC("dlc", "DLC", "Downloadable content (DLC)"),
    DSIWARE("dsiware", "DSiWare", "Nintendo DSiWare for the 3DS"),
    VIDEOS("videos", "Videos", "Videos, trailers or movies released on the 3DS"),
    EXTRAS("extras", "Extras", "Miscellaneous content (Homebrew, ROM Hacks, etc.)");

    companion object {
        fun fromSlug(slug: String): HShopCategory? =
            entries.find { it.slug.equals(slug, ignoreCase = true) }
    }
}

@Serializable
data class HShopSubcategory(
    val slug: String,
    val name: String,
    val description: String,
    val categorySlug: String,
    val sizeString: String = "",
    val titleCount: Int = 0,
    val officialCount: Int = 0,
    val downloadCount: Long = 0
)
