package com.board.gamekit.repository

import com.board.gamekit.bgg.BggDataSource
import com.board.gamekit.db.GameCache
import com.board.gamekit.model.GameDto
import org.slf4j.LoggerFactory

class GameRepository(
    private val bggDataSource: BggDataSource,
    private val cache: GameCache,
    private val maxResults: Int,
) {
    private val logger = LoggerFactory.getLogger(GameRepository::class.java)

    suspend fun searchGames(query: String, limit: Int, offset: Int): List<GameDto> {
        val normalized = query.trim().lowercase()
        if (normalized.isEmpty()) return emptyList()

        cache.find(normalized, limit, offset)?.let { cached ->
            logger.debug("Cache hit: {} games for query '{}'", cached.size, normalized)
            return cached
        }

        val fetched = bggDataSource.search(normalized, maxResults)
        cache.save(normalized, fetched)
        logger.info("Cached {} games fetched from BGG for query '{}'", fetched.size, normalized)

        return fetched.drop(offset).take(limit)
    }
}
