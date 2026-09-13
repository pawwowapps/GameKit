package com.board.gamekit

import com.board.gamekit.bgg.installBggDefaults
import com.board.gamekit.bgg.userAgent
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BggHttpClientTest {

    @Test
    fun `contact is omitted from the user agent when not configured`() {
        assertEquals("GameKit/1.0", userAgent(""))
        assertEquals("GameKit/1.0", userAgent("   "))
    }

    @Test
    fun `configured contact is added to the user agent`() {
        assertEquals("GameKit/1.0 (contact: team@example.com)", userAgent("team@example.com"))
    }

    @Test
    fun `token is sent as bearer authorization header`() = runBlocking {
        var authorization: String? = null
        var agent: String? = null
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    authorization = request.headers[HttpHeaders.Authorization]
                    agent = request.headers[HttpHeaders.UserAgent]
                    respondOk()
                }
            }
            installBggDefaults("test-token", "team@example.com")
        }

        client.get("https://boardgamegeek.com/xmlapi2/search")

        assertEquals("Bearer test-token", authorization)
        assertEquals("GameKit/1.0 (contact: team@example.com)", agent)
    }

    @Test
    fun `blank token sends no authorization header`() = runBlocking {
        var authorization: String? = null
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    authorization = request.headers[HttpHeaders.Authorization]
                    respondOk()
                }
            }
            installBggDefaults()
        }

        client.get("https://boardgamegeek.com/xmlapi2/search")

        assertNull(authorization)
    }
}
