package kr.hhplus.be.server

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import kr.hhplus.be.server.api.ConcertListResponse
import kr.hhplus.be.server.api.ConcertSummary
import kr.hhplus.be.server.api.ScheduleListResponse
import kr.hhplus.be.server.api.ScheduleSummary
import kr.hhplus.be.server.api.SeatListResponse
import kr.hhplus.be.server.api.SeatSummary
import kr.hhplus.be.server.common.cache.ConcertCacheService
import org.junit.jupiter.api.Test
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger

class ConcertCachePerformanceTest {
    private val cacheManager = ConcurrentMapCacheManager("concert-list", "concert-schedule", "schedule-seat")
    private val cacheService = ConcertCacheService(cacheManager, ObjectMapper().registerModule(JavaTimeModule()))

    @Test
    fun `measure cache hit improvement with synthetic read cost`() {
        val concertMetrics = measureScenario(
            clearAction = { cacheManager.getCache("concert-list")?.clear() },
            action = { loaderConcerts() },
            cachedAction = { loader -> cacheService.getConcerts(0, 20, loader) },
        )
        val scheduleMetrics = measureScenario(
            clearAction = { cacheManager.getCache("concert-schedule")?.clear() },
            action = { loaderSchedules() },
            cachedAction = { loader -> cacheService.getSchedules(1L, loader) },
        )
        val seatMetrics = measureScenario(
            clearAction = { cacheManager.getCache("schedule-seat")?.clear() },
            action = { loaderSeats() },
            cachedAction = { loader -> cacheService.getSeats(1L, loader) },
        )

        println(
            """
            CACHE_BENCH concert-list coldMs=${concertMetrics.coldAvgMillis} warmMs=${concertMetrics.warmAvgMillis} coldLoads=${concertMetrics.coldAvgLoads} warmLoads=${concertMetrics.warmAvgLoads}
            CACHE_BENCH concert-schedule coldMs=${scheduleMetrics.coldAvgMillis} warmMs=${scheduleMetrics.warmAvgMillis} coldLoads=${scheduleMetrics.coldAvgLoads} warmLoads=${scheduleMetrics.warmAvgLoads}
            CACHE_BENCH schedule-seat coldMs=${seatMetrics.coldAvgMillis} warmMs=${seatMetrics.warmAvgMillis} coldLoads=${seatMetrics.coldAvgLoads} warmLoads=${seatMetrics.warmAvgLoads}
            """.trimIndent(),
        )
    }

    private fun <T : Any> measureScenario(
        clearAction: () -> Unit,
        action: () -> T,
        cachedAction: ((() -> T)) -> T,
    ): CacheBenchMetrics {
        val coldLoads = AtomicInteger()
        val warmLoads = AtomicInteger()
        var coldNanos = 0L
        var warmNanos = 0L

        repeat(30) {
            clearAction()
            val startedAt = System.nanoTime()
            cachedAction {
                coldLoads.incrementAndGet()
                action()
            }
            coldNanos += System.nanoTime() - startedAt
        }

        clearAction()
        cachedAction {
            warmLoads.incrementAndGet()
            action()
        }
        warmLoads.set(0)

        repeat(30) {
            val startedAt = System.nanoTime()
            cachedAction {
                warmLoads.incrementAndGet()
                action()
            }
            warmNanos += System.nanoTime() - startedAt
        }

        return CacheBenchMetrics(
            coldAvgMillis = nanosToMillis(coldNanos / 30),
            warmAvgMillis = nanosToMillis(warmNanos / 30),
            coldAvgLoads = coldLoads.get() / 30.0,
            warmAvgLoads = warmLoads.get() / 30.0,
        )
    }

    private fun loaderConcerts(): ConcertListResponse {
        Thread.sleep(15)
        return ConcertListResponse(
            items = listOf(
                ConcertSummary(
                    concertId = 1L,
                    title = "2026 HH Plus Concert",
                    venueName = "Olympic Hall",
                    bookingOpenAt = LocalDateTime.of(2026, 3, 1, 10, 0),
                    bookingCloseAt = LocalDateTime.of(2026, 3, 31, 23, 59),
                ),
            ),
            page = 0,
            size = 20,
            total = 1,
        )
    }

    private fun loaderSchedules(): ScheduleListResponse {
        Thread.sleep(20)
        return ScheduleListResponse(
            items = listOf(
                ScheduleSummary(
                    scheduleId = 1L,
                    startAt = LocalDateTime.of(2026, 3, 20, 19, 0),
                    totalSeat = 50,
                    availableSeat = 50,
                ),
            ),
        )
    }

    private fun loaderSeats(): SeatListResponse {
        Thread.sleep(25)
        return SeatListResponse(
            items = listOf(
                SeatSummary(
                    seatId = 1L,
                    section = "A",
                    row = "1",
                    number = "1",
                    price = 50000L,
                    status = "AVAILABLE",
                ),
            ),
        )
    }
}

private data class CacheBenchMetrics(
    val coldAvgMillis: Double,
    val warmAvgMillis: Double,
    val coldAvgLoads: Double,
    val warmAvgLoads: Double,
)

private fun nanosToMillis(nanos: Long): Double = nanos / 1_000_000.0
