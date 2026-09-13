package com.board.gamekit

import com.board.gamekit.db.GameDatabase
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplicationTest {

    private lateinit var database: GameDatabase

    @BeforeTest
    fun setUp() {
        database = newTestDatabase()
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun testRoot() = testApplication {
        installModule()
        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Hello, Ktor!", response.bodyAsText())
    }

    @Test
    fun `blank query returns 400 with serialized error body`() = testApplication {
        installModule()
        val response = client.get("/api/v1/games/search?query=")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("must not be blank"), response.bodyAsText())
    }

    @Test
    fun `too short a query returns 400`() = testApplication {
        installModule()
        val response = client.get("/api/v1/games/search?query=ca")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("at least 3 characters"), response.bodyAsText())
    }

    @Test
    fun `limit above the maximum returns 400`() = testApplication {
        installModule()
        val response = client.get("/api/v1/games/search?query=catan&limit=1000")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("between 1 and 100"), response.bodyAsText())
    }

    @Test
    fun `negative offset returns 400`() = testApplication {
        installModule()
        val response = client.get("/api/v1/games/search?query=catan&offset=-1")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("must not be negative"), response.bodyAsText())
    }

    @Test
    fun `unavailable BGG returns 502`() = testApplication {
        installModule { respondError(HttpStatusCode.Unauthorized) }
        val response = client.get("/api/v1/games/search?query=zzqqxx-no-such-game")
        assertEquals(HttpStatusCode.BadGateway, response.status)
        assertTrue(response.bodyAsText().contains("BoardGameGeek"), response.bodyAsText())
    }

    @Test
    fun `search honours the limit parameter`() = testApplication {
        val games = (1..5).map { it to "Game $it" }.toTypedArray()
        installModule { request ->
            val xml = if (request.url.encodedPath.endsWith("/thing")) thingXml(*games) else searchXml(*games)
            respond(xml, headers = headersOf(HttpHeaders.ContentType, "text/xml"))
        }
        val response = client.get("/api/v1/games/search?query=game&limit=2")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(2, Regex("\"bgg_id\"").findAll(response.bodyAsText()).count())
    }

    private fun ApplicationTestBuilder.installModule(
        handler: io.ktor.client.engine.mock.MockRequestHandler = { respondError(HttpStatusCode.ServiceUnavailable) },
    ) {
        application {
            gameKitModule(testDataSource(handler), testCache(database), testConfig)
        }
    }
}
