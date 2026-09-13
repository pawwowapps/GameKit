package com.board.gamekit

import com.board.gamekit.bgg.BggDataSource
import com.board.gamekit.bgg.installBggDefaults
import com.board.gamekit.config.AppConfig
import com.board.gamekit.db.GameCache
import com.board.gamekit.db.GameDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import java.nio.file.Files

const val TEST_BASE_URL = "https://bgg.test/xmlapi2"

val testConfig = AppConfig(
    databaseUrl = "",
    bggBaseUrl = TEST_BASE_URL,
    bggContact = "",
    cacheTtlMillis = 60_000,
    defaultLimit = 30,
    maxLimit = 100,
    maxResults = 300,
    minQueryLength = 3,
)

fun newTestDatabase(): GameDatabase {
    val file = Files.createTempFile("gamekit-test-", ".db")
    file.toFile().deleteOnExit()
    return GameDatabase.connect("jdbc:sqlite:${file.toAbsolutePath()}")
}

fun testCache(
    database: GameDatabase,
    ttlMillis: Long = testConfig.cacheTtlMillis,
    now: () -> Long = System::currentTimeMillis,
) = GameCache(database, ttlMillis, now)

fun testDataSource(handler: MockRequestHandler) = BggDataSource(
    HttpClient(MockEngine) {
        engine { addHandler(handler) }
        installBggDefaults()
    },
    TEST_BASE_URL,
)

const val SEARCH_XML = """
<?xml version="1.0" encoding="utf-8"?>
<items total="1">
  <item type="boardgame" id="13">
    <name type="primary" value="CATAN"/>
    <yearpublished value="1995"/>
  </item>
</items>
"""

const val THING_XML = """
<?xml version="1.0" encoding="utf-8"?>
<items>
  <item type="boardgame" id="13">
    <thumbnail>https://cf.geekdo-images.com/thumb.jpg</thumbnail>
    <image>https://cf.geekdo-images.com/original.jpg</image>
    <name type="primary" sortindex="1" value="CATAN"/>
    <yearpublished value="1995"/>
    <minplayers value="3"/>
    <maxplayers value="4"/>
    <playingtime value="120"/>
    <statistics page="1">
      <ratings>
        <average value="7.06925"/>
      </ratings>
    </statistics>
  </item>
</items>
"""

const val EMPTY_SEARCH_XML = """
<?xml version="1.0" encoding="utf-8"?>
<items total="0"></items>
"""

fun searchXml(vararg games: Pair<Int, String>) = buildString {
    append("""<?xml version="1.0" encoding="utf-8"?><items total="${games.size}">""")
    games.forEach { (id, name) ->
        append("""<item type="boardgame" id="$id"><name type="primary" value="$name"/></item>""")
    }
    append("</items>")
}

fun thingXml(vararg games: Pair<Int, String>) = buildString {
    append("""<?xml version="1.0" encoding="utf-8"?><items>""")
    games.forEach { (id, name) ->
        append("""<item type="boardgame" id="$id"><name type="primary" value="$name"/></item>""")
    }
    append("</items>")
}
