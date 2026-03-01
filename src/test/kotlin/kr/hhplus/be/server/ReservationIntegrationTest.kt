package kr.hhplus.be.server

import kr.hhplus.be.server.common.ConflictException
import kr.hhplus.be.server.domain.entity.QueueTokenEntity
import kr.hhplus.be.server.domain.entity.UserEntity
import kr.hhplus.be.server.domain.entity.UserPointEntity
import kr.hhplus.be.server.domain.enums.HoldStatus
import kr.hhplus.be.server.domain.enums.PaymentStatus
import kr.hhplus.be.server.domain.enums.QueueStatus
import kr.hhplus.be.server.domain.enums.ReservationStatus
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
import kr.hhplus.be.server.domain.repository.UserPointRepository
import kr.hhplus.be.server.domain.repository.UserRepository
import kr.hhplus.be.server.domain.repository.UserSessionRepository
import kr.hhplus.be.server.queue.QueueService
import kr.hhplus.be.server.reservation.application.CreateReservationUseCase
import kr.hhplus.be.server.reservation.application.HoldSeatsUseCase
import kr.hhplus.be.server.reservation.application.PayReservationUseCase
import kr.hhplus.be.server.service.ConcertFacadeService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

@SpringBootTest(classes = [ServerApplication::class])
@Testcontainers(disabledWithoutDocker = true)
class ReservationIntegrationTest {
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
    private lateinit var pointTransactionRepository: PointTransactionRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var userPointRepository: UserPointRepository

    @Autowired
    private lateinit var userSessionRepository: UserSessionRepository

    @Autowired
    private lateinit var clock: Clock

    companion object {
        @Container
        @JvmStatic
        val mySqlContainer: MySQLContainer<*> = MySQLContainer(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("hhplus")
            .withUsername("test")
            .withPassword("test")

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { mySqlContainer.getJdbcUrl() + "?characterEncoding=UTF-8&serverTimezone=UTC" }
            registry.add("spring.datasource.username") { mySqlContainer.username }
            registry.add("spring.datasource.password") { mySqlContainer.password }
        }
    }

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
        seatRepository.findAll().forEach { it.seatStatus = SeatStatus.AVAILABLE }
    }

    @Test
    fun `통합 테스트 - 토큰 발급부터 결제 완료까지 전체 흐름이 동작한다`() {
        val user = createUser()
        val concert = concertRepository.findAll().first()
        val schedule = scheduleRepository.findByConcertIdAndStatusOrderByStartAt(concert.id!!, ScheduleStatus.OPEN).first()
        val seat = seatRepository.findByScheduleIdOrderById(schedule.id!!).first()

        val queueToken = queueService.issueToken(user, concert.id!!)
        concertFacadeService.charge(user, seat.price)
        val hold = holdSeatsUseCase.execute(user.id!!, queueToken.queueToken, schedule.id!!, listOf(seat.id!!))
        val reservation = createReservationUseCase.execute(user.id!!, queueToken.queueToken, hold.holdId)
        val payment = payReservationUseCase.execute(user.id!!, queueToken.queueToken, reservation.reservationId, reservation.totalAmount, "CARD")

        assertEquals("SUCCESS", payment.status)
        assertEquals(SeatStatus.SOLD, seatRepository.findById(seat.id!!).orElseThrow().seatStatus)
        assertEquals(ReservationStatus.CONFIRMED, reservationRepository.findById(reservation.reservationId).orElseThrow().reservationStatus)
        assertEquals(PaymentStatus.SUCCESS, paymentRepository.findById(payment.paymentId).orElseThrow().paymentStatus)
        assertEquals(0L, userPointRepository.findById(user.id!!).orElseThrow().balance)
    }

    @Test
    fun `통합 테스트 - 만료 시간 도래 후 좌석은 다시 예약 가능해야 한다`() {
        val user = createUser()
        val concert = concertRepository.findAll().first()
        val schedule = scheduleRepository.findByConcertIdAndStatusOrderByStartAt(concert.id!!, ScheduleStatus.OPEN).first()
        val seat = seatRepository.findByScheduleIdOrderById(schedule.id!!).first()

        val queueToken = queueService.issueToken(user, concert.id!!)
        val firstHold = holdSeatsUseCase.execute(user.id!!, queueToken.queueToken, schedule.id!!, listOf(seat.id!!))
        val holdEntity = seatHoldRepository.findById(firstHold.holdId).orElseThrow()
        holdEntity.holdExpiresAt = LocalDateTime.now(clock).minusMinutes(1)

        val secondHold = holdSeatsUseCase.execute(user.id!!, queueToken.queueToken, schedule.id!!, listOf(seat.id!!))

        assertTrue(secondHold.holdId != firstHold.holdId)
        assertEquals(HoldStatus.EXPIRED, seatHoldRepository.findById(firstHold.holdId).orElseThrow().holdStatus)
        assertEquals(HoldStatus.ACTIVE, seatHoldRepository.findById(secondHold.holdId).orElseThrow().holdStatus)
        assertEquals(SeatStatus.HELD, seatRepository.findById(seat.id!!).orElseThrow().seatStatus)
    }

    @Test
    fun `통합 테스트 - 다중 유저가 동시에 같은 좌석을 요청하면 한 명만 성공한다`() {
        val concert = concertRepository.findAll().first()
        val schedule = scheduleRepository.findByConcertIdAndStatusOrderByStartAt(concert.id!!, ScheduleStatus.OPEN).first()
        val seat = seatRepository.findByScheduleIdOrderById(schedule.id!!).first()
        val user1 = createUser()
        val user2 = createUser()
        val token1 = createAdmittedToken(user1, concert.id!!, 1L)
        val token2 = createAdmittedToken(user2, concert.id!!, 2L)

        val successCount = AtomicInteger()
        val conflictCount = AtomicInteger()
        runConcurrently(2) { index ->
            val userId = if (index == 0) user1.id!! else user2.id!!
            val token = if (index == 0) token1 else token2
            try {
                holdSeatsUseCase.execute(userId, token, schedule.id!!, listOf(seat.id!!))
                successCount.incrementAndGet()
            } catch (_: ConflictException) {
                conflictCount.incrementAndGet()
            }
        }

        assertEquals(1, successCount.get())
        assertEquals(1, conflictCount.get())
        assertEquals(SeatStatus.HELD, seatRepository.findById(seat.id!!).orElseThrow().seatStatus)
    }

    @Test
    fun `통합 테스트 - 동일 사용자의 동일 콘서트 대기열 토큰 동시 발급은 하나만 성공한다`() {
        val user = createUser()
        val concert = concertRepository.findAll().first()
        val successCount = AtomicInteger()
        val conflictCount = AtomicInteger()

        runConcurrently(2) {
            try {
                queueService.issueToken(userRepository.findById(user.id!!).orElseThrow(), concert.id!!)
                successCount.incrementAndGet()
            } catch (_: ConflictException) {
                conflictCount.incrementAndGet()
            }
        }

        assertEquals(1, successCount.get())
        assertEquals(1, conflictCount.get())
        assertEquals(1, queueTokenRepository.findAll().count { it.user.id == user.id && it.concert.id == concert.id })
    }

    private fun createUser(): UserEntity {
        val user = userRepository.save(
            UserEntity(
                email = "test-${UUID.randomUUID()}@hhplus.kr",
                name = "tester",
                passwordHash = "password123",
            ),
        )
        userPointRepository.save(UserPointEntity(user = user, balance = 0L, updatedAt = LocalDateTime.now(clock)))
        return user
    }

    private fun createAdmittedToken(user: UserEntity, concertId: Long, queueNumber: Long): String {
        val concert = concertRepository.findById(concertId).orElseThrow()
        val now = LocalDateTime.now(clock)
        val token = queueTokenRepository.save(
            QueueTokenEntity(
                queueToken = UUID.randomUUID().toString(),
                user = user,
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

    private fun runConcurrently(threadCount: Int, task: (Int) -> Unit) {
        val executor = Executors.newFixedThreadPool(threadCount)
        val ready = CountDownLatch(threadCount)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threadCount)
        repeat(threadCount) { index ->
            executor.submit {
                ready.countDown()
                start.await()
                try {
                    task(index)
                } finally {
                    done.countDown()
                }
            }
        }
        ready.await()
        start.countDown()
        done.await()
        executor.shutdown()
    }
}
