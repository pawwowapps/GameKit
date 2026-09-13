package com.board.gamekit.db

import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory

class Migration(val version: Int, val apply: () -> Unit)

object Migrations {

    private val logger = LoggerFactory.getLogger(Migrations::class.java)

    private val all: List<Migration> = listOf(
        Migration(1) {
            SchemaUtils.create(GamesTable, SearchQueriesTable, SearchResultsTable)
        },
    )

    fun applyTo(database: Database) {
        val current = transaction(database) { readSchemaVersion() }
        val pending = all.filter { it.version > current }.sortedBy { it.version }
        if (pending.isEmpty()) return

        logger.info("Applying {} database migration(s) from schema version {}", pending.size, current)
        pending.forEach { migration ->
            transaction(database) {
                migration.apply()
                exec("PRAGMA user_version = ${migration.version}", explicitStatementType = StatementType.UPDATE)
            }
        }
    }

    private fun org.jetbrains.exposed.v1.jdbc.JdbcTransaction.readSchemaVersion(): Int {
        var version = 0
        exec("PRAGMA user_version") { rs ->
            if (rs.next()) version = rs.getInt(1)
        }
        return version
    }
}
