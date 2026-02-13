package com.bernelius.abrechnung.cache

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CacheTest {
    private val testScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @BeforeEach
    fun setup() {
        RecipientCache.invalidateAll()
        InvoiceCache.invalidateAll()
    }

    @Test
    fun `returns cached value within TTL`() = runBlocking {
        var fetchCount = 0
        RecipientCache.getOrFetch("testKey") {
            fetchCount++
            emptyList<com.bernelius.abrechnung.models.RecipientDTO>()
        }

        RecipientCache.getOrFetch("testKey") {
            fetchCount++
            emptyList<com.bernelius.abrechnung.models.RecipientDTO>()
        }

        assertEquals(1, fetchCount)
    }

    @Test
    fun `returns fresh value after TTL`() = runBlocking<Unit> {
        val cache = TtlCache<String, String>(ttlMillis = 50)
        var fetchCount = 0

        cache.getOrFetch("key") {
            fetchCount++
            "first"
        }

        delay(60)

        cache.getOrFetch("key") {
            fetchCount++
            "second"
        }

        assertEquals(2, fetchCount)
    }

    @Test
    fun `invalidate clears specific key`() = runBlocking<Unit> {
        var fetchCount = 0
        RecipientCache.getOrFetch("invalidateTest") {
            fetchCount++
            emptyList<com.bernelius.abrechnung.models.RecipientDTO>()
        }

        RecipientCache.invalidate("invalidateTest")

        RecipientCache.getOrFetch("invalidateTest") {
            fetchCount++
            emptyList<com.bernelius.abrechnung.models.RecipientDTO>()
        }

        assertEquals(2, fetchCount)
    }

    @Test
    fun `isFresh returns false for non-existent key`() {
        assertFalse(RecipientCache.isFresh("nonexistent"))
    }

    @Test
    fun `invalidateAll clears all entries`() = runBlocking<Unit> {
        RecipientCache.getOrFetch("all") { 
            listOf<com.bernelius.abrechnung.models.RecipientDTO>() 
        }
        assertTrue(RecipientCache.isFresh("all"))

        RecipientCache.invalidateAll()

        assertFalse(RecipientCache.isFresh("all"))
    }
}
