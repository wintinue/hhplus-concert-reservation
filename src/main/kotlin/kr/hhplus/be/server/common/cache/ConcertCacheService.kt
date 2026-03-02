package kr.hhplus.be.server.common.cache

import com.fasterxml.jackson.databind.ObjectMapper
import kr.hhplus.be.server.api.ConcertListResponse
import kr.hhplus.be.server.api.ScheduleListResponse
import kr.hhplus.be.server.api.SeatListResponse
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Component

@Component
class ConcertCacheService(
    private val cacheManager: CacheManager,
    private val objectMapper: ObjectMapper,
) {
    fun getConcerts(page: Int, size: Int, loader: () -> ConcertListResponse): ConcertListResponse =
        getOrLoad(CONCERT_LIST_CACHE, "page:$page:size:$size", ConcertListResponse::class.java, loader)

    fun getSchedules(concertId: Long, loader: () -> ScheduleListResponse): ScheduleListResponse =
        getOrLoad(CONCERT_SCHEDULE_CACHE, concertId.toString(), ScheduleListResponse::class.java, loader)

    fun getSeats(scheduleId: Long, loader: () -> SeatListResponse): SeatListResponse =
        getOrLoad(SCHEDULE_SEAT_CACHE, scheduleId.toString(), SeatListResponse::class.java, loader)

    fun evictScheduleView(concertId: Long, scheduleId: Long) {
        cacheManager.getCache(CONCERT_SCHEDULE_CACHE)?.evict(concertId.toString())
        cacheManager.getCache(SCHEDULE_SEAT_CACHE)?.evict(scheduleId.toString())
    }

    private fun <T : Any> getOrLoad(cacheName: String, key: String, type: Class<T>, loader: () -> T): T {
        val cache = cacheManager.getCache(cacheName) ?: return loader()
        val cached = cache.get(key, Any::class.java)
        if (cached != null) {
            if (type.isInstance(cached)) {
                @Suppress("UNCHECKED_CAST")
                return cached as T
            }
            return objectMapper.convertValue(cached, type)
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
