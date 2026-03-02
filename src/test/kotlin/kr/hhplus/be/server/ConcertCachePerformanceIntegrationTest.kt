package kr.hhplus.be.server

import jakarta.persistence.EntityManagerFactory
import kr.hhplus.be.server.auth.AuthService
import kr.hhplus.be.server.domain.entity.QueueTokenEntity
import kr.hhplus.be.server.domain.entity.UserEntity
import kr.hhplus.be.server.domain.enums.QueueStatus
import kr.hhplus.be.server.domain.enums.ScheduleStatus
import kr.hhplus.be.server.domain.enums.SeatStatus
import kr.hhplus.be.server.domain.repository.ConcertRepository
import kr.hhplus.be.server.domain.repository.ConcertScheduleRepository
import kr.hhplus.be.server.domain.repository.PaymentRepository
import kr.hhplus.be.server.domain.repository.PointTransactionRepository
import kr.hhplus.be.server.domain.repository.QueueTokenRepository
import kr.hhplus.be.server.domain.repository.ReservationItemRepository
import kr.hhplus.be.server.domain.repository.ReservationRepository
import kr.hhplus.be.server.domain.repository.SeatHoldItemRepository
import kr.hhplus.be.server.domain.repository.SeatHoldRepository
import kr.hhplus.be.server.domain.repository.SeatRepository
import kr.hhplus.be.server.domain.repository.UserSessionRepository
import kr.hhplus.be.server.service.ConcertFacadeService
import org.hibernate.SessionFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cache.CacheManager
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

@SpringBootTest(classes = [ServerApplication::class])
@Testcontainers(disabledWithoutDocker = true)
class ConcertCachePerformanceIntegrationTest {
    @Autowired
    private lateinit var authService: AuthService

    @Autowired
    private lateinit var concertFacadeService: ConcertFacadeService

    @Autowired
    private lateinit var concertRepository: ConcertRepository

    @Autowired
    private lateinit var scheduleRepository: ConcertScheduleRepository

    @Autowired
    private lateinit var seatRepository: SeatRepository

    @Autowired
    private lateinit var queueTokenRepository: QueueTokenRepository

    @Autowired
    private lateinit var seatHoldRepository: SeatHoldRepository

    @Autowired
    private lateinit var seatHoldItemRepository: SeatHoldItemRepository

    @Autowired
    private lateinit var reservationRepository: ReservationRepository

    @Autowired
    private lateinit var reservationItemRepository: ReservationItemRepository

    @Autowired
    private lateinit var paymentRepository: PaymentRepository

    @Autowired
    private lateinit var pointTransactionRepository: PointTransactionRepository

    @Autowired
    private lateinit var userSessionRepository: UserSessionRepository

    @Autowired
    private lateinit var cacheManager: CacheManager

    @Autowired
    private lateinit var entityManagerFactory: EntityManagerFactory

    @Autowired
    private lateinit var clock: Clock

    @BeforeEach
    fun cleanState() {
        paymentRepository.deleteAllInBatch()
        pointTransactionRepository.deleteAllInBatch()
        reservationItemRepository.deleteAllInBatch()
        reservationRepository.deleteAllInBatch()
        seatHoldItemRepository.deleteAllInBatch()
        seatHoldRepository.deleteAllInBatch()
        queueTokenRepository.deleteAllInBatch()
        userSessionRepository.deleteAllInBatch()
        val seats = seatRepository.findAll().onEach { it.seatStatus = SeatStatus.AVAILABLE }
        seatRepository.saveAllAndFlush(seats)
        clearCaches()
    }

    @Test
    fun `measure cache hit improvements for concert read scenarios`() {
        val concert = concertRepository.findAll().first()
        val schedule = scheduleRepository.findByConcertIdAndStatusOrderByStartAt(concert.id!!, ScheduleStatus.OPEN).first()
        val user = createUser()
        val queueToken = createAdmittedToken(user, concert.id!!, 1L)

        val concertMetrics = measureScenario(
            clearAction = { clearConcertListCache() },
            action = { concertFacadeService.getConcerts(0, 20) },
        )
        val scheduleMetrics = measureScenario(
            clearAction = { clearScheduleCaches(concert.id!!, schedule.id!!) },
            action = { concertFacadeService.getSchedules(user, concert.id!!, queueToken) },
        )
        val seatMetrics = measureScenario(
            clearAction = { clearScheduleCaches(concert.id!!, schedule.id!!) },
            action = { concertFacadeService.getSeats(user, schedule.id!!, queueToken) },
        )

        println(
            """
            CACHE_PERF concert-list coldMs=${concertMetrics.coldAvgMillis} warmMs=${concertMetrics.warmAvgMillis} coldSql=${concertMetrics.coldAvgStatements} warmSql=${concertMetrics.warmAvgStatements}
            CACHE_PERF concert-schedule coldMs=${scheduleMetrics.coldAvgMillis} warmMs=${scheduleMetrics.warmAvgMillis} coldSql=${scheduleMetrics.coldAvgStatements} warmSql=${scheduleMetrics.warmAvgStatements}
            CACHE_PERF schedule-seat coldMs=${seatMetrics.coldAvgMillis} warmMs=${seatMetrics.warmAvgMillis} coldSql=${seatMetrics.coldAvgStatements} warmSql=${seatMetrics.warmAvgStatements}
            """.trimIndent(),
        )
    }

    private fun measureScenario(
        clearAction: () -> Unit,
        action: () -> Unit,
    ): CachePerfMetrics {
        val statistics = entityManagerFactory.unwrap(SessionFactory::class.java).statistics.apply { isStatisticsEnabled = true }

        var coldNanos = 0L
        var coldStatements = 0L
        repeat(20) {
            clearAction()
            statistics.clear()
            val startedAt = System.nanoTime()
            action()
            coldNanos += System.nanoTime() - startedAt
            coldStatements += statistics.prepareStatementCount
        }

        clearAction()
        action()

        var warmNanos = 0L
        var warmStatements = 0L
        repeat(20) {
            statistics.clear()
            val startedAt = System.nanoTime()
            action()
            warmNanos += System.nanoTime() - startedAt
            warmStatements += statistics.prepareStatementCount
        }

        return CachePerfMetrics(
            coldAvgMillis = nanosToMillis(coldNanos / 20),
            warmAvgMillis = nanosToMillis(warmNanos / 20),
            coldAvgStatements = coldStatements / 20,
            warmAvgStatements = warmStatements / 20,
        )
    }

    private fun createUser(): UserEntity {
        val authToken = authService.signUp(
            kr.hhplus.be.server.api.SignUpRequest(
                name = "tester",
                email = "perf-${UUID.randomUUID()}@hhplus.kr",
                password = "password123",
            ),
        )
        return authService.requireUser("Bearer ${authToken.accessToken}")
    }

    private fun createAdmittedToken(user: UserEntity, concertId: Long, queueNumber: Long): String {
        val managedUser = authService.requireUser("Bearer ${userSessionRepository.findAll().first { it.user.id == user.id }.accessToken}")
        val concert = concertRepository.findById(concertId).orElseThrow()
        val now = LocalDateTime.now(clock)
        return queueTokenRepository.save(
            QueueTokenEntity(
                queueToken = UUID.randomUUID().toString(),
                user = managedUser,
                concert = concert,
                queueNumber = queueNumber,
                queueStatus = QueueStatus.ADMITTED,
                issuedAt = now,
                expiresAt = now.plusMinutes(30),
                reservationWindowExpiresAt = now.plusMinutes(10),
                refreshedAt = now,
            ),
        ).queueToken
    }

    private fun clearCaches() {
        cacheManager.getCache("concert-list")?.clear()
        cacheManager.getCache("concert-schedule")?.clear()
        cacheManager.getCache("schedule-seat")?.clear()
    }

    private fun clearConcertListCache() {
        cacheManager.getCache("concert-list")?.clear()
    }

    private fun clearScheduleCaches(concertId: Long, scheduleId: Long) {
        cacheManager.getCache("concert-schedule")?.evict(concertId.toString())
        cacheManager.getCache("schedule-seat")?.evict(scheduleId.toString())
    }

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
            registry.add("spring.jpa.properties.hibernate.generate_statistics") { true }
        }
    }
}

private data class CachePerfMetrics(
    val coldAvgMillis: Double,
    val warmAvgMillis: Double,
    val coldAvgStatements: Long,
    val warmAvgStatements: Long,
)

private fun nanosToMillis(nanos: Long): Double = nanos / 1_000_000.0
