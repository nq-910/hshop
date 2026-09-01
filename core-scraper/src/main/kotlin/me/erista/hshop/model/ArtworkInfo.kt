package me.erista.hshop.model

import kotlinx.serialization.Serializable

@Serializable
data class ArtworkInfo(
    val primaryCoverUrl: String? = null,
    val thumbnailCoverUrl: String? = null,
    val highResCoverUrl: String? = null,
    val fullCoverWrapUrl: String? = null,
    val fallbackUrls: List<String> = emptyList(),
    val source: String = "GameTDB"
)
