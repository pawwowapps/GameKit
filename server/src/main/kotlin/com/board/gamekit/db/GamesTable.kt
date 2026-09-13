package com.board.gamekit.db

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

object GamesTable : Table("games") {
    val bggId = integer("bgg_id")
    val name = varchar("name", 512)
    val yearPublished = integer("year_published").nullable()
    val imageUrl = varchar("image_url", 1024).nullable()
    val minPlayers = integer("min_players").nullable()
    val maxPlayers = integer("max_players").nullable()
    val playingTime = integer("playing_time").nullable()
    val rating = double("rating").nullable()

    override val primaryKey = PrimaryKey(bggId)
}

object SearchQueriesTable : Table("search_queries") {
    val id = integer("id").autoIncrement()
    val query = varchar("query", 256).uniqueIndex()
    val fetchedAt = long("fetched_at")

    override val primaryKey = PrimaryKey(id)
}

object SearchResultsTable : Table("search_results") {
    val queryId = integer("query_id")
        .references(SearchQueriesTable.id, onDelete = ReferenceOption.CASCADE)
    val bggId = integer("bgg_id")
        .references(GamesTable.bggId, onDelete = ReferenceOption.CASCADE)
    val position = integer("position")

    override val primaryKey = PrimaryKey(queryId, bggId)
}
