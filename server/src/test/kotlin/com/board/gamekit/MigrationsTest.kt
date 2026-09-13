package com.board.gamekit

import com.board.gamekit.db.GameDatabase
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals

class MigrationsTest {

    @Test
    fun `schema version is stamped and connecting twice is idempotent`() {
        val file = Files.createTempFile("gamekit-migrations-", ".db")
        file.toFile().deleteOnExit()
        val url = "jdbc:sqlite:${file.toAbsolutePath()}"

        GameDatabase.connect(url).close()
        assertEquals(1, schemaVersion(url))

        GameDatabase.connect(url).close()
        assertEquals(1, schemaVersion(url))
    }

    private fun schemaVersion(url: String): Int =
        DriverManager.getConnection(url).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("PRAGMA user_version").use { rs ->
                    if (rs.next()) rs.getInt(1) else -1
                }
            }
        }
}
