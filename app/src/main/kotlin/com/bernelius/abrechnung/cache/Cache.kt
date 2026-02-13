package com.bernelius.abrechnung.cache

import com.bernelius.abrechnung.models.InvoiceDTO
import com.bernelius.abrechnung.models.RecipientDTO
import com.bernelius.abrechnung.models.UserConfigDTO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

open class TtlCache<K : Any, V : Any>(
    private val ttlMillis: Long = 300_000,
) {
    private data class Entry<V>(val value: V, val timestamp: Long)

    private val cache = ConcurrentHashMap<K, Entry<V>>()
    private val fetchMutexes = ConcurrentHashMap<K, Mutex>()

    suspend fun getOrFetch(key: K, fetcher: suspend () -> V): V {
        val cached = cache[key]
        if (cached != null && System.currentTimeMillis() - cached.timestamp < ttlMillis) {
            return cached.value
        }

        val mutex = fetchMutexes.getOrPut(key) { Mutex() }
        mutex.withLock {
            val fresh = cache[key]
            if (fresh != null && System.currentTimeMillis() - fresh.timestamp < ttlMillis) {
                return fresh.value
            }

            return try {
                val value = fetcher()
                cache[key] = Entry(value, System.currentTimeMillis())
                value
            } catch (e: Exception) {
                throw e
            }
        }
    }

    fun invalidate(key: K) {
        cache.remove(key)
    }

    fun invalidateAll() {
        cache.clear()
    }

    fun isFresh(key: K): Boolean {
        val cached = cache[key]
        return cached != null && System.currentTimeMillis() - cached.timestamp < ttlMillis
    }
}

object RecipientCache : TtlCache<String, List<RecipientDTO>>(ttlMillis = 300_000)
object InvoiceCache : TtlCache<String, List<InvoiceDTO>>(ttlMillis = 300_000)
object UserConfigCache : TtlCache<String, UserConfigDTO>(ttlMillis = 300_000)

