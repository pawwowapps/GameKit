package com.board.gamekit.bgg

import com.board.gamekit.model.GameDto
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import org.slf4j.LoggerFactory

class BggDataSource(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) {
    private val logger = LoggerFactory.getLogger(BggDataSource::class.java)

    suspend fun search(query: String, maxResults: Int): List<GameDto> {
        val searchXml = fetchXml("$baseUrl/search") {
            parameter("type", "boardgame")
            parameter("query", query)
        }

        val found = runCatching { BggXmlParser.parseSearch(searchXml) }
            .getOrElse { throw BggUnavailableException("Malformed BoardGameGeek /search response", it) }
            .distinctBy { it.bggId }
            .take(maxResults)
        if (found.isEmpty()) return emptyList()

        val details = fetchDetails(found.map { it.bggId })
        return found.map { details[it.bggId] ?: it }
    }

    private suspend fun fetchDetails(ids: List<Int>): Map<Int, GameDto> = runCatching {
        val xml = fetchXml("$baseUrl/thing") {
            parameter("type", "boardgame")
            parameter("stats", "1")
            parameter("id", ids.joinToString(","))
        }
        BggXmlParser.parseThings(xml)
    }.getOrElse {
        logger.warn("Returning BGG search results without details", it)
        emptyMap()
    }

    private suspend fun fetchXml(url: String, block: HttpRequestBuilder.() -> Unit): String {
        val response = runCatching { httpClient.get(url, block) }
            .getOrElse { throw BggUnavailableException("Cannot reach BoardGameGeek", it) }

        if (response.status == HttpStatusCode.Accepted) {
            throw BggUnavailableException("BoardGameGeek queued the request, retry shortly")
        }
        if (!response.status.isSuccess()) {
            throw BggUnavailableException("BoardGameGeek responded ${response.status}")
        }
        return response.bodyAsText()
    }
}
