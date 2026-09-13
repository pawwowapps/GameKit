package com.board.gamekit.bgg

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.bearerAuth

const val BGG_TOKEN_ENV = "BGG_API_TOKEN"

fun bggApiToken(): String = System.getenv(BGG_TOKEN_ENV).orEmpty().trim()

fun userAgent(contact: String): String =
    if (contact.isBlank()) "GameKit/1.0" else "GameKit/1.0 (contact: $contact)"

fun HttpClientConfig<*>.installBggDefaults(token: String = "", contact: String = "") {
    install(UserAgent) { agent = userAgent(contact) }
    install(HttpTimeout) {
        requestTimeoutMillis = 15_000
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 15_000
    }
    if (token.isNotBlank()) {
        defaultRequest { bearerAuth(token) }
    }
}
