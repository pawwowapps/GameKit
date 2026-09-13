package com.board.gamekit.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GameDto(
    @SerialName("bgg_id") val bggId: Int,
    val name: String,
    @SerialName("year_published") val yearPublished: Int? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("min_players") val minPlayers: Int? = null,
    @SerialName("max_players") val maxPlayers: Int? = null,
    @SerialName("playing_time") val playingTime: Int? = null,
    val rating: Double? = null,
)
