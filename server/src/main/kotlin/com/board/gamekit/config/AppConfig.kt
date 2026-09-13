package com.board.gamekit.config

import io.ktor.server.config.ApplicationConfig

data class AppConfig(
    val databaseUrl: String,
    val bggBaseUrl: String,
    val cacheTtlMillis: Long,
    val defaultLimit: Int,
    val maxLimit: Int,
    val maxResults: Int,
    val minQueryLength: Int,
) {
    companion object {
        fun from(config: ApplicationConfig) = AppConfig(
            databaseUrl = config.property("gamekit.database.url").getString(),
            bggBaseUrl = config.property("gamekit.bgg.baseUrl").getString(),
            cacheTtlMillis = config.property("gamekit.cache.ttlMinutes").getString().toLong() * 60_000L,
            defaultLimit = config.property("gamekit.search.defaultLimit").getString().toInt(),
            maxLimit = config.property("gamekit.search.maxLimit").getString().toInt(),
            maxResults = config.property("gamekit.search.maxResults").getString().toInt(),
            minQueryLength = config.property("gamekit.search.minQueryLength").getString().toInt(),
        )
    }
}
