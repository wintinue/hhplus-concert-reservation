package kr.hhplus.be.server

import kr.hhplus.be.server.api.SignUpRequest
import kr.hhplus.be.server.auth.AuthService
import kr.hhplus.be.server.domain.entity.QueueTokenEntity
import kr.hhplus.be.server.domain.entity.UserEntity
import kr.hhplus.be.server.domain.enums.BookingSagaStatus
import kr.hhplus.be.server.domain.enums.OutboxEventStatus
import kr.hhplus.be.server.domain.enums.QueueStatus
import kr.hhplus.be.server.domain.enums.ScheduleStatus
import kr.hhplus.be.server.domain.enums.SeatStatus
import kr.hhplus.be.server.domain.repository.BookingSagaRepository
import kr.hhplus.be.server.domain.repository.ConcertRepository
import kr.hhplus.be.server.domain.repository.ConcertScheduleRepository
import kr.hhplus.be.server.domain.repository.OutboxEventRepository
import kr.hhplus.be.server.domain.repository.PaymentRepository
import kr.hhplus.be.server.domain.repository.PointTransactionRepository
import kr.hhplus.be.server.domain.repository.QueueTokenRepository
import kr.hhplus.be.server.domain.repository.ReservationItemRepository
import kr.hhplus.be.server.domain.repository.ReservationRepository
import kr.hhplus.be.server.domain.repository.SeatHoldItemRepository
import kr.hhplus.be.server.domain.repository.SeatHoldRepository
import kr.hhplus.be.server.domain.repository.SeatRepository
import kr.hhplus.be.server.domain.repository.UserPointRepository
import kr.hhplus.be.server.domain.repository.UserRepository
import kr.hhplus.be.server.domain.repository.UserSessionRepository
import kr.hhplus.be.server.queue.QueueService
import kr.hhplus.be.server.reservation.application.CreateReservationUseCase
import kr.hhplus.be.server.reservation.application.HoldSeatsUseCase
import kr.hhplus.be.server.reservation.application.PayReservationUseCase
import kr.hhplus.be.server.reservation.infra.MockReservationDataPlatformClient
import kr.hhplus.be.server.service.ConcertFacadeService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.utility.DockerImageName
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

@SpringBootTest(classes = [ServerApplication::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test", "kafka")
@Testcontainers(disabledWithoutDocker = true)
class ReservationKafkaIntegrationTest {
    @Autowired
    private lateinit var authService: AuthService

    @Autowired
    private lateinit var queueService: QueueService

    @Autowired
    private lateinit var holdSeatsUseCase: HoldSeatsUseCase

    @Autowired
    private lateinit var createReservationUseCase: CreateReservationUseCase

    @Autowired
    private lateinit var payReservationUseCase: PayReservationUseCase

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
    private lateinit var outboxEventRepository: OutboxEventRepository

    @Autowired
    private lateinit var bookingSagaRepository: BookingSagaRepository

    @Autowired
    private lateinit var pointTransactionRepository: PointTransactionRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var userPointRepository: UserPointRepository

    @Autowired
    private lateinit var userSessionRepository: UserSessionRepository

    @Autowired
    private lateinit var clock: Clock

    @Autowired
    private lateinit var mockReservationDataPlatformClient: MockReservationDataPlatformClient

    @BeforeEach
    fun cleanState() {
        mockReservationDataPlatformClient.clear()
        paymentRepository.deleteAllInBatch()
        outboxEventRepository.deleteAllInBatch()
        bookingSagaRepository.deleteAllInBatch()
        pointTransactionRepository.deleteAllInBatch()
        reservationItemRepository.deleteAllInBatch()
        reservationRepository.deleteAllInBatch()
        seatHoldItemRepository.deleteAllInBatch()
        seatHoldRepository.deleteAllInBatch()
        queueTokenRepository.deleteAllInBatch()
        userSessionRepository.deleteAllInBatch()
        val seats = seatRepository.findAll().onEach { it.seatStatus = SeatStatus.AVAILABLE }
        seatRepository.saveAllAndFlush(seats)
    }

    @Test
    fun `kafka 활성화 시 예약 결제 이벤트가 outbox에서 kafka를 거쳐 데이터 플랫폼까지 전달된다`() {
        val user = createUser()
        val concert = concertRepository.findAll().first()
        val schedule = scheduleRepository.findByConcertIdAndStatusOrderByStartAt(concert.id!!, ScheduleStatus.OPEN).first()
        val seat = seatRepository.findByScheduleIdOrderById(schedule.id!!).first()
        val queueToken = createAdmittedToken(user, concert.id!!, 1L)

        concertFacadeService.charge(user, seat.price)
        val hold = holdSeatsUseCase.execute(user.id!!, queueToken, schedule.id!!, listOf(seat.id!!))
        val reservation = createReservationUseCase.execute(user.id!!, queueToken, hold.holdId)
        payReservationUseCase.execute(user.id!!, queueToken, reservation.reservationId, reservation.totalAmount, "CARD")

        waitUntil(timeoutMillis = 15_000L) {
            mockReservationDataPlatformClient.getSentPayloads().size == 1 &&
                bookingSagaRepository.findByReservationId(reservation.reservationId)?.sagaStatus == BookingSagaStatus.COMPLETED
        }

        assertEquals(1, mockReservationDataPlatformClient.getSentPayloads().size)
        assertEquals(reservation.reservationId, mockReservationDataPlatformClient.getSentPayloads().single().reservationId)
        assertEquals(OutboxEventStatus.PUBLISHED, outboxEventRepository.findAll().single().eventStatus)
        assertEquals(BookingSagaStatus.COMPLETED, bookingSagaRepository.findByReservationId(reservation.reservationId)?.sagaStatus)
        assertTrue(outboxEventRepository.findAll().single().publishedAt != null)
    }

    private fun createUser(): UserEntity {
        val email = "kafka-${UUID.randomUUID()}@hhplus.kr"
        val authToken = authService.signUp(SignUpRequest("tester", email, "password123"))
        return authService.requireUser("Bearer ${authToken.accessToken}")
    }

    private fun createAdmittedToken(user: UserEntity, concertId: Long, queueNumber: Long): String {
        val managedUser = userRepository.findById(user.id!!).orElseThrow()
        val concert = concertRepository.findById(concertId).orElseThrow()
        val now = LocalDateTime.now(clock)
        val token = queueTokenRepository.save(
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
        )
        return token.queueToken
    }

    private fun waitUntil(timeoutMillis: Long, condition: () -> Boolean) {
        val startedAt = System.currentTimeMillis()
        while (System.currentTimeMillis() - startedAt < timeoutMillis) {
            if (condition()) return
            Thread.sleep(200L)
        }
        error("condition was not satisfied within ${timeoutMillis}ms")
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

        @Container
        @JvmStatic
        val kafkaContainer: KafkaContainer = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"))

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { mySqlContainer.getJdbcUrl() + "?characterEncoding=UTF-8&serverTimezone=UTC" }
            registry.add("spring.datasource.username") { mySqlContainer.username }
            registry.add("spring.datasource.password") { mySqlContainer.password }
            registry.add("spring.data.redis.host") { redisContainer.host }
            registry.add("spring.data.redis.port") { redisContainer.getMappedPort(6379) }
            registry.add("spring.kafka.bootstrap-servers") { kafkaContainer.bootstrapServers }
        }
    }
}
