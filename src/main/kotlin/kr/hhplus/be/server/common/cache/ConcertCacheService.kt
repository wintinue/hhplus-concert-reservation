package kr.hhplus.be.server.common.cache

import kr.hhplus.be.server.api.ConcertListResponse
import kr.hhplus.be.server.api.ScheduleListResponse
import kr.hhplus.be.server.api.SeatListResponse
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Component

@Component
class ConcertCacheService(
    private val cacheManager: CacheManager,
) {
    fun getConcerts(page: Int, size: Int, loader: () -> ConcertListResponse): ConcertListResponse =
        getOrLoad(CONCERT_LIST_CACHE, "page:$page:size:$size", loader)

    fun getSchedules(concertId: Long, loader: () -> ScheduleListResponse): ScheduleListResponse =
        getOrLoad(CONCERT_SCHEDULE_CACHE, concertId.toString(), loader)

    fun getSeats(scheduleId: Long, loader: () -> SeatListResponse): SeatListResponse =
        getOrLoad(SCHEDULE_SEAT_CACHE, scheduleId.toString(), loader)

    fun evictScheduleView(concertId: Long, scheduleId: Long) {
        cacheManager.getCache(CONCERT_SCHEDULE_CACHE)?.evict(concertId.toString())
        cacheManager.getCache(SCHEDULE_SEAT_CACHE)?.evict(scheduleId.toString())
    }

    private fun <T : Any> getOrLoad(cacheName: String, key: String, loader: () -> T): T {
        val cache = cacheManager.getCache(cacheName) ?: return loader()
        val cached = cache.get(key, Any::class.java)
        if (cached != null) {
            @Suppress("UNCHECKED_CAST")
            return cached as T
        }
        val loaded = loader()
        cache.put(key, loaded)
        return loaded
    }

    companion object {
        const val CONCERT_LIST_CACHE = "concert-list"
        const val CONCERT_SCHEDULE_CACHE = "concert-schedule"
        const val SCHEDULE_SEAT_CACHE = "schedule-seat"
    }
}
