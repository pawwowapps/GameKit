package com.board.gamekit.db

import com.board.gamekit.model.GameDto
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.notInSubQuery
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.batchUpsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll

class GameCache(
    private val database: GameDatabase,
    private val ttlMillis: Long,
    private val now: () -> Long = System::currentTimeMillis,
) {

    suspend fun find(query: String, limit: Int, offset: Int): List<GameDto>? = database.query {
        val cachedQuery = SearchQueriesTable
            .selectAll()
            .where { SearchQueriesTable.query eq query }
            .singleOrNull()
            ?: return@query null

        if (now() - cachedQuery[SearchQueriesTable.fetchedAt] > ttlMillis) return@query null

        (SearchResultsTable innerJoin GamesTable)
            .selectAll()
            .where { SearchResultsTable.queryId eq cachedQuery[SearchQueriesTable.id] }
            .orderBy(SearchResultsTable.position to SortOrder.ASC)
            .limit(limit)
            .offset(offset.toLong())
            .map { it.toGameDto() }
    }

    suspend fun save(query: String, games: List<GameDto>): Unit = database.query {
        if (games.isNotEmpty()) {
            GamesTable.batchUpsert(games, GamesTable.bggId) { game ->
                this[GamesTable.bggId] = game.bggId
                this[GamesTable.name] = game.name
                this[GamesTable.yearPublished] = game.yearPublished
                this[GamesTable.imageUrl] = game.imageUrl
                this[GamesTable.minPlayers] = game.minPlayers
                this[GamesTable.maxPlayers] = game.maxPlayers
                this[GamesTable.playingTime] = game.playingTime
                this[GamesTable.rating] = game.rating
            }
        }

        SearchQueriesTable.deleteWhere { SearchQueriesTable.query eq query }
        val queryId = SearchQueriesTable.insert {
            it[SearchQueriesTable.query] = query
            it[fetchedAt] = now()
        }[SearchQueriesTable.id]

        SearchResultsTable.batchInsert(games.withIndex()) { (index, game) ->
            this[SearchResultsTable.queryId] = queryId
            this[SearchResultsTable.bggId] = game.bggId
            this[SearchResultsTable.position] = index
        }
    }

    suspend fun evictExpired(): Int = database.query {
        val expiredBefore = now() - ttlMillis
        val removedQueries = SearchQueriesTable.deleteWhere { fetchedAt less expiredBefore }
        val removedGames = GamesTable.deleteWhere {
            bggId notInSubQuery SearchResultsTable.select(SearchResultsTable.bggId)
        }
        removedQueries + removedGames
    }

    private fun ResultRow.toGameDto() = GameDto(
        bggId = this[GamesTable.bggId],
        name = this[GamesTable.name],
        yearPublished = this[GamesTable.yearPublished],
        imageUrl = this[GamesTable.imageUrl],
        minPlayers = this[GamesTable.minPlayers],
        maxPlayers = this[GamesTable.maxPlayers],
        playingTime = this[GamesTable.playingTime],
        rating = this[GamesTable.rating],
    )
}
