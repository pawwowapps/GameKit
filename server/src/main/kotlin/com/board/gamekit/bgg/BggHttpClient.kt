package com.board.gamekit.bgg

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header

fun HttpClientConfig<*>.installBggDefaults() {
    install(UserAgent) { agent = "GameKit/1.0 (contact: paw.wow.apps@gmail.com)" }
    install(HttpTimeout) {
        requestTimeoutMillis = 15_000
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 15_000
    }

    defaultRequest {
        header("X-BGG-Client-ID", "GameKit")
    }
}
