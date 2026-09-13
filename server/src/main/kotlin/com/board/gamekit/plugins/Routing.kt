package com.board.gamekit.plugins

import com.board.gamekit.config.AppConfig
import com.board.gamekit.model.ErrorResponse
import com.board.gamekit.sayHello
import com.board.gamekit.repository.GameRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

fun Application.configureRouting(gameRepository: GameRepository, config: AppConfig) {
    routing {
        get("/") {
            call.respondText(sayHello("Ktor"))
        }

        route("/api/v1/games") {
            get("/search") {
                val query = call.request.queryParameters["query"]?.trim().orEmpty()
                if (query.isEmpty()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Query parameter 'query' is required and must not be blank"),
                    )
                    return@get
                }

                if (query.length < config.minQueryLength) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Query parameter 'query' must be at least ${config.minQueryLength} characters"),
                    )
                    return@get
                }

                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: config.defaultLimit
                if (limit !in 1..config.maxLimit) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Query parameter 'limit' must be between 1 and ${config.maxLimit}"),
                    )
                    return@get
                }

                val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
                if (offset < 0) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Query parameter 'offset' must not be negative"),
                    )
                    return@get
                }

                call.respond(gameRepository.searchGames(query, limit, offset))
            }
        }
    }
}
