package me.erista.hshop.model

import kotlinx.serialization.Serializable

@Serializable
data class HShopSearchResult(
    val query: String,
    val totalCount: Int,
    val offset: Int,
    val count: Int,
    val titles: List<HShopTitleSummary>,
    val nextOffset: Int? = null
)
