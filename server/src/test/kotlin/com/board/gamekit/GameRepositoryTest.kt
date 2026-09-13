package com.board.gamekit

import com.board.gamekit.bgg.BggUnavailableException
import com.board.gamekit.db.GameDatabase
import com.board.gamekit.repository.GameRepository
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GameRepositoryTest {

    private lateinit var database: GameDatabase

    @BeforeTest
    fun setUp() {
        database = newTestDatabase()
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    private val xmlHeaders = headersOf(HttpHeaders.ContentType, "text/xml")

    @Test
    fun `empty cache fetches from BGG, stores and returns full data`() = runBlocking {
        var requests = 0
        val repository = repository { request ->
            requests++
            respond(if (request.url.encodedPath.endsWith("/thing")) THING_XML else SEARCH_XML, headers = xmlHeaders)
        }

        val result = repository.searchGames("catan", limit = 30, offset = 0)

        assertEquals(2, requests, "expected calls to /search and /thing")
        with(result.single()) {
            assertEquals(13, bggId)
            assertEquals("CATAN", name)
            assertEquals(1995, yearPublished)
            assertEquals("https://cf.geekdo-images.com/original.jpg", imageUrl)
            assertEquals(3, minPlayers)
            assertEquals(4, maxPlayers)
            assertEquals(120, playingTime)
            assertEquals(7.06925, rating)
        }
    }

    @Test
    fun `repeating the same query is served from cache`() = runBlocking {
        var requests = 0
        val repository = repository { request ->
            requests++
            respond(if (request.url.encodedPath.endsWith("/thing")) THING_XML else SEARCH_XML, headers = xmlHeaders)
        }

        repository.searchGames("catan", limit = 30, offset = 0)
        val afterWarmUp = requests
        val cached = repository.searchGames("CATAN", limit = 30, offset = 0)

        assertEquals(afterWarmUp, requests, "same query must not hit the network twice")
        assertEquals(listOf(13), cached.map { it.bggId })
    }

    @Test
    fun `a different query is fetched even when cached names match it`() = runBlocking {
        val served = mutableListOf<String>()
        val repository = repository { request ->
            val query = request.url.parameters["query"].orEmpty()
            if (request.url.encodedPath.endsWith("/thing")) {
                respond(thingXml(822 to "Carcassonne", 3955 to "Cartagena"), headers = xmlHeaders)
            } else {
                served += query
                val games = when (query) {
                    "carcassonne" -> arrayOf(822 to "Carcassonne")
                    else -> arrayOf(822 to "Carcassonne", 3955 to "Cartagena")
                }
                respond(searchXml(*games), headers = xmlHeaders)
            }
        }

        repository.searchGames("carcassonne", limit = 30, offset = 0)
        val broader = repository.searchGames("car", limit = 30, offset = 0)

        assertEquals(listOf("carcassonne", "car"), served, "'car' must reach BGG, not be masked by the cache")
        assertEquals(listOf(822, 3955), broader.map { it.bggId })
    }

    @Test
    fun `empty result is cached so the query is not repeated`() = runBlocking {
        var requests = 0
        val repository = repository {
            requests++
            respond(EMPTY_SEARCH_XML, headers = xmlHeaders)
        }

        assertTrue(repository.searchGames("nosuchgame", limit = 30, offset = 0).isEmpty())
        assertEquals(1, requests)

        assertTrue(repository.searchGames("nosuchgame", limit = 30, offset = 0).isEmpty())
        assertEquals(1, requests, "an empty result must be cached too")
    }

    @Test
    fun `expired cache entry is refetched`() = runBlocking {
        var requests = 0
        var clock = 1_000L
        val repository = GameRepository(
            testDataSource { request ->
                requests++
                respond(if (request.url.encodedPath.endsWith("/thing")) THING_XML else SEARCH_XML, headers = xmlHeaders)
            },
            testCache(database, ttlMillis = 60_000, now = { clock }),
            testConfig.maxResults,
        )

        repository.searchGames("catan", limit = 30, offset = 0)
        val afterWarmUp = requests

        clock += 60_001
        repository.searchGames("catan", limit = 30, offset = 0)

        assertTrue(requests > afterWarmUp, "expired entry must be refetched")
    }

    @Test
    fun `limit and offset page the results`() = runBlocking {
        val games = (1..5).map { it to "Game $it" }.toTypedArray()
        val repository = repository { request ->
            if (request.url.encodedPath.endsWith("/thing")) {
                respond(thingXml(*games), headers = xmlHeaders)
            } else {
                respond(searchXml(*games), headers = xmlHeaders)
            }
        }

        val firstPage = repository.searchGames("game", limit = 2, offset = 0)
        val secondPage = repository.searchGames("game", limit = 2, offset = 2)

        assertEquals(listOf(1, 2), firstPage.map { it.bggId })
        assertEquals(listOf(3, 4), secondPage.map { it.bggId })
    }

    @Test
    fun `duplicate ids from BGG are collapsed`() = runBlocking {
        val duplicated = """<?xml version="1.0" encoding="utf-8"?><items total="3">
            <item type="boardgame" id="13"><name type="primary" value="CATAN"/></item>
            <item type="boardgame" id="13"><name type="primary" value="Settlers of Catan"/></item>
            <item type="boardgame" id="822"><name type="primary" value="Carcassonne"/></item>
        </items>"""
        val repository = repository { request ->
            if (request.url.encodedPath.endsWith("/thing")) {
                respond(thingXml(13 to "CATAN", 822 to "Carcassonne"), headers = xmlHeaders)
            } else {
                respond(duplicated, headers = xmlHeaders)
            }
        }

        val result = repository.searchGames("catan", limit = 30, offset = 0)

        assertEquals(listOf(13, 822), result.map { it.bggId })
    }

    @Test
    fun `blank query does not hit the network`() = runBlocking {
        var requests = 0
        val repository = repository {
            requests++
            respond(SEARCH_XML, headers = xmlHeaders)
        }

        assertTrue(repository.searchGames("   ", limit = 30, offset = 0).isEmpty())
        assertEquals(0, requests)
    }

    @Test
    fun `upstream failure surfaces as BggUnavailableException`() = runBlocking {
        val repository = repository { respondError(HttpStatusCode.Unauthorized) }

        assertFailsWith<BggUnavailableException> { repository.searchGames("catan", 30, 0) }
        Unit
    }

    @Test
    fun `network error surfaces as BggUnavailableException`() = runBlocking {
        val repository = repository { throw IOException("connection refused") }

        assertFailsWith<BggUnavailableException> { repository.searchGames("catan", 30, 0) }
        Unit
    }

    @Test
    fun `failing details request degrades to search results`() = runBlocking {
        val repository = repository { request ->
            if (request.url.encodedPath.endsWith("/thing")) {
                respondError(HttpStatusCode.ServiceUnavailable)
            } else {
                respond(SEARCH_XML, headers = xmlHeaders)
            }
        }

        val result = repository.searchGames("catan", 30, 0)

        assertEquals(listOf(13), result.map { it.bggId })
        assertEquals(null, result.single().imageUrl)
    }

    @Test
    fun `concurrent identical queries trigger a single upstream fetch`() = runBlocking {
        val searchRequests = AtomicInteger()
        val repository = repository { request ->
            if (request.url.encodedPath.endsWith("/thing")) {
                respond(THING_XML, headers = xmlHeaders)
            } else {
                searchRequests.incrementAndGet()
                delay(150)
                respond(SEARCH_XML, headers = xmlHeaders)
            }
        }

        val results = coroutineScope {
            (1..5).map { async { repository.searchGames("catan", limit = 30, offset = 0) } }.awaitAll()
        }

        assertEquals(1, searchRequests.get(), "parallel identical queries must share one fetch")
        results.forEach { assertEquals(listOf(13), it.map { game -> game.bggId }) }
    }

    @Test
    fun `expired rows are evicted together with orphaned games`() = runBlocking {
        var clock = 1_000L
        val cache = testCache(database, ttlMillis = 60_000, now = { clock })
        val repository = GameRepository(
            testDataSource { request ->
                respond(if (request.url.encodedPath.endsWith("/thing")) THING_XML else SEARCH_XML, headers = xmlHeaders)
            },
            cache,
            testConfig.maxResults,
        )

        repository.searchGames("catan", limit = 30, offset = 0)
        clock += 60_001

        assertTrue(cache.evictExpired() > 0)
        assertEquals(null, cache.find("catan", 30, 0), "evicted query must be gone, not just stale")
    }

    private fun repository(handler: io.ktor.client.engine.mock.MockRequestHandler) = GameRepository(
        testDataSource(handler),
        testCache(database),
        testConfig.maxResults,
    )
}
