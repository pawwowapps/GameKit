package com.board.gamekit.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class GameDatabase private constructor(
    private val database: Database,
    private val dataSource: HikariDataSource,
) : AutoCloseable {

    suspend fun <T> query(block: () -> T): T =
        withContext(Dispatchers.IO) { transaction(database) { block() } }

    override fun close() = dataSource.close()

    companion object {
        fun connect(jdbcUrl: String): GameDatabase {
            val dataSource = HikariDataSource(
                HikariConfig().apply {
                    this.jdbcUrl = jdbcUrl
                    driverClassName = "org.sqlite.JDBC"
                    maximumPoolSize = 1
                    isAutoCommit = false
                    transactionIsolation = "TRANSACTION_SERIALIZABLE"
                    connectionInitSql = "PRAGMA foreign_keys = ON"
                }
            )
            val database = Database.connect(dataSource)
            transaction(database) {
                SchemaUtils.create(GamesTable, SearchQueriesTable, SearchResultsTable)
            }
            return GameDatabase(database, dataSource)
        }
    }
}
