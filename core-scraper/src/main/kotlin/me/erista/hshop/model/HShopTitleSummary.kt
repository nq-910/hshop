package me.erista.hshop.model

import kotlinx.serialization.Serializable

@Serializable
data class HShopTitleSummary(
    val id: String,
    val name: String,
    val categorySlug: String,
    val subcategorySlug: String,
    val titleId: String,
    val productCode: String,
    val version: String,
    val sizeString: String,
    val contentType: String,
    val artwork: ArtworkInfo? = null
)

@Serializable
data class RelatedContentSummary(
    val id: String,
    val name: String,
    val relationType: String,
    val titleId: String,
    val productCode: String,
    val version: String,
    val sizeString: String,
    val contentType: String
)

@Serializable
data class HShopTitleDetail(
    val id: String,
    val name: String,
    val categorySlug: String,
    val subcategorySlug: String,
    val titleId: String,
    val productCode: String,
    val version: String,
    val sizeString: String,
    val contentType: String,
    val addedDate: String,
    val updatedDate: String,
    val downloadCount: Long,
    val seed: String? = null,
    val sha256: String? = null,
    val description: String = "",
    val relatedContent: List<RelatedContentSummary> = emptyList(),
    val artwork: ArtworkInfo? = null,
    val gameTdb: GameTdbMetadata? = null
)

@Serializable
data class GameTdbMetadata(
    val gameId: String,
    val titleId: String = "",
    val name: String = "",
    val title: String = "",
    val synopsis: String = "",
    val developer: String = "",
    val publisher: String = "",
    val releaseDate: String = "",
    val genre: String = "",
    val ratingType: String = "",
    val ratingValue: String = "",
    val ratingDescriptors: String = "",
    val players: String = "",
    val wifiFeatures: String = "",
    val languages: String = "",
    val region: String = "",
    val firmware: String = "",
    val trimmedSize: Long = 0L,
    val card: String = ""
)
