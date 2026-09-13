package com.board.gamekit

import com.board.gamekit.bgg.BggDataSource
import com.board.gamekit.bgg.BggUnavailableException
import com.board.gamekit.bgg.installBggDefaults
import com.board.gamekit.config.AppConfig
import com.board.gamekit.db.GameCache
import com.board.gamekit.db.GameDatabase
import com.board.gamekit.model.ErrorResponse
import com.board.gamekit.plugins.configureRouting
import com.board.gamekit.plugins.configureSerialization
import com.board.gamekit.repository.GameRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.module() {
    val config = AppConfig.from(environment.config)
    val httpClient = HttpClient(CIO) { installBggDefaults() }
    val database = GameDatabase.connect(config.databaseUrl)

    monitor.subscribe(ApplicationStopped) {
        httpClient.close()
        database.close()
    }

    gameKitModule(
        bggDataSource = BggDataSource(httpClient, config.bggBaseUrl),
        cache = GameCache(database, config.cacheTtlMillis),
        config = config,
    )
}

fun Application.gameKitModule(bggDataSource: BggDataSource, cache: GameCache, config: AppConfig) {
    configureSerialization()
    configureStatusPages()
    configureRouting(GameRepository(bggDataSource, cache, config.maxLimit), config)
}

private fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<BggUnavailableException> { call, cause ->
            call.application.log.warn("BoardGameGeek is unavailable", cause)
            call.respond(
                HttpStatusCode.BadGateway,
                ErrorResponse(cause.message ?: "BoardGameGeek is unavailable"),
            )
        }
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled request failure", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(cause.message ?: "Internal server error"),
            )
        }
    }
}
