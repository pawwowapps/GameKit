package com.board.gamekit.repository

import com.board.gamekit.bgg.BggDataSource
import com.board.gamekit.db.GameCache
import com.board.gamekit.model.GameDto
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import org.slf4j.LoggerFactory

class GameRepository(
    private val bggDataSource: BggDataSource,
    private val cache: GameCache,
    private val maxResults: Int,
) {
    private val logger = LoggerFactory.getLogger(GameRepository::class.java)
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<List<GameDto>>>()

    suspend fun searchGames(query: String, limit: Int, offset: Int): List<GameDto> {
        val normalized = query.trim().lowercase()
        if (normalized.isEmpty()) return emptyList()

        cache.find(normalized, limit, offset)?.let { cached ->
            logger.debug("Cache hit: {} games for query '{}'", cached.size, normalized)
            return cached
        }

        return fetchOnce(normalized).drop(offset).take(limit)
    }

    private suspend fun fetchOnce(query: String): List<GameDto> {
        val pending = CompletableDeferred<List<GameDto>>()
        val running = inFlight.putIfAbsent(query, pending)
        if (running != null) {
            logger.debug("Joining in-flight fetch for query '{}'", query)
            return running.await()
        }

        try {
            val fetched = bggDataSource.search(query, maxResults)
            cache.save(query, fetched)
            logger.info("Cached {} games fetched from BGG for query '{}'", fetched.size, query)
            pending.complete(fetched)
            return fetched
        } catch (failure: Throwable) {
            pending.completeExceptionally(failure)
            throw failure
        } finally {
            inFlight.remove(query)
        }
    }
}
