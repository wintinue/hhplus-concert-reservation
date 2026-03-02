package kr.hhplus.be.server

import kr.hhplus.be.server.api.ConcertListResponse
import kr.hhplus.be.server.api.ConcertSummary
import kr.hhplus.be.server.api.ScheduleListResponse
import kr.hhplus.be.server.api.ScheduleSummary
import kr.hhplus.be.server.api.SeatListResponse
import kr.hhplus.be.server.api.SeatSummary
import kr.hhplus.be.server.common.cache.ConcertCacheService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest(classes = [ServerApplication::class])
@Testcontainers(disabledWithoutDocker = true)
class ConcertCacheIntegrationTest {
    @Autowired
    private lateinit var concertCacheService: ConcertCacheService

    @Test
    fun `concert list cache reuses same key without reloading`() {
        val loaderCount = AtomicInteger()
        val first = concertCacheService.getConcerts(0, 20) {
            loaderCount.incrementAndGet()
            concertListResponse("2026 HH Plus Concert")
        }
        val second = concertCacheService.getConcerts(0, 20) {
            loaderCount.incrementAndGet()
            concertListResponse("Changed Concert")
        }

        assertEquals(1, loaderCount.get())
        assertEquals(first, second)
    }

    @Test
    fun `seat cache is reloaded after eviction`() {
        val loaderCount = AtomicInteger()
        val cached = concertCacheService.getSeats(1L) {
            loaderCount.incrementAndGet()
            seatListResponse("AVAILABLE")
        }

        concertCacheService.evictScheduleView(1L, 1L)

        val reloaded = concertCacheService.getSeats(1L) {
            loaderCount.incrementAndGet()
            seatListResponse("HELD")
        }

        assertEquals(2, loaderCount.get())
        assertEquals("AVAILABLE", cached.items.first().status)
        assertEquals("HELD", reloaded.items.first().status)
    }

    @Test
    fun `schedule cache is reloaded after eviction`() {
        val loaderCount = AtomicInteger()
        val cached = concertCacheService.getSchedules(1L) {
            loaderCount.incrementAndGet()
            scheduleListResponse(50)
        }

        concertCacheService.evictScheduleView(1L, 1L)

        val reloaded = concertCacheService.getSchedules(1L) {
            loaderCount.incrementAndGet()
            scheduleListResponse(49)
        }

        assertEquals(2, loaderCount.get())
        assertEquals(50, cached.items.first().availableSeat)
        assertEquals(49, reloaded.items.first().availableSeat)
    }

    private fun concertListResponse(title: String) = ConcertListResponse(
        items = listOf(
            ConcertSummary(
                concertId = 1L,
                title = title,
                venueName = "Olympic Hall",
                bookingOpenAt = LocalDateTime.of(2026, 3, 1, 10, 0),
                bookingCloseAt = LocalDateTime.of(2026, 3, 31, 23, 59),
            ),
        ),
        page = 0,
        size = 20,
        total = 1,
    )

    private fun scheduleListResponse(availableSeat: Int) = ScheduleListResponse(
        items = listOf(
            ScheduleSummary(
                scheduleId = 1L,
                startAt = LocalDateTime.of(2026, 3, 20, 19, 0),
                totalSeat = 50,
                availableSeat = availableSeat,
            ),
        ),
    )

    private fun seatListResponse(status: String) = SeatListResponse(
        items = listOf(
            SeatSummary(
                seatId = 1L,
                section = "A",
                row = "1",
                number = "1",
                price = 50000L,
                status = status,
            ),
        ),
    )

    companion object {
        @Container
        @JvmStatic
        val mySqlContainer: MySQLContainer<*> = MySQLContainer(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("hhplus")
            .withUsername("test")
            .withPassword("test")

        @Container
        @JvmStatic
        val redisContainer: GenericContainer<*> = GenericContainer(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { mySqlContainer.getJdbcUrl() + "?characterEncoding=UTF-8&serverTimezone=UTC" }
            registry.add("spring.datasource.username") { mySqlContainer.username }
            registry.add("spring.datasource.password") { mySqlContainer.password }
            registry.add("spring.data.redis.host") { redisContainer.host }
            registry.add("spring.data.redis.port") { redisContainer.getMappedPort(6379) }
        }
    }
}
