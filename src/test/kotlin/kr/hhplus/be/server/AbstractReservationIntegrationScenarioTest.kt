package kr.hhplus.be.server

import kr.hhplus.be.server.api.LoginRequest
import kr.hhplus.be.server.api.SignUpRequest
import kr.hhplus.be.server.auth.AuthService
import kr.hhplus.be.server.common.ConflictException
import kr.hhplus.be.server.domain.entity.QueueTokenEntity
import kr.hhplus.be.server.domain.entity.UserEntity
import kr.hhplus.be.server.domain.enums.HoldStatus
import kr.hhplus.be.server.domain.enums.BookingSagaStatus
import kr.hhplus.be.server.domain.enums.OutboxEventStatus
import kr.hhplus.be.server.domain.enums.PaymentStatus
import kr.hhplus.be.server.domain.enums.QueueStatus
import kr.hhplus.be.server.domain.enums.ReservationStatus
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
import kr.hhplus.be.server.reservation.infra.MockReservationDataPlatformClient
import kr.hhplus.be.server.reservation.application.CancelReservationUseCase
import kr.hhplus.be.server.reservation.application.CreateReservationUseCase
import kr.hhplus.be.server.reservation.application.GetReservationUseCase
import kr.hhplus.be.server.reservation.application.GetReservationsUseCase
import kr.hhplus.be.server.reservation.application.HoldSeatsUseCase
import kr.hhplus.be.server.reservation.application.PayReservationUseCase
import kr.hhplus.be.server.reservation.application.ReservationCleanupScheduler
import kr.hhplus.be.server.service.ConcertFacadeService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

abstract class AbstractReservationIntegrationScenarioTest {
    @Autowired
    protected lateinit var authService: AuthService

    @Autowired
    protected lateinit var queueService: QueueService

    @Autowired
    protected lateinit var holdSeatsUseCase: HoldSeatsUseCase

    @Autowired
    protected lateinit var createReservationUseCase: CreateReservationUseCase

    @Autowired
    protected lateinit var payReservationUseCase: PayReservationUseCase

    @Autowired
    protected lateinit var cancelReservationUseCase: CancelReservationUseCase

    @Autowired
    protected lateinit var getReservationsUseCase: GetReservationsUseCase

    @Autowired
    protected lateinit var getReservationUseCase: GetReservationUseCase

    @Autowired
    protected lateinit var reservationCleanupScheduler: ReservationCleanupScheduler

    @Autowired
    protected lateinit var concertFacadeService: ConcertFacadeService

    @Autowired
    protected lateinit var concertRepository: ConcertRepository

    @Autowired
    protected lateinit var scheduleRepository: ConcertScheduleRepository

    @Autowired
    protected lateinit var seatRepository: SeatRepository

    @Autowired
    protected lateinit var queueTokenRepository: QueueTokenRepository

    @Autowired
    protected lateinit var seatHoldRepository: SeatHoldRepository

    @Autowired
    protected lateinit var seatHoldItemRepository: SeatHoldItemRepository

    @Autowired
    protected lateinit var reservationRepository: ReservationRepository

    @Autowired
    protected lateinit var reservationItemRepository: ReservationItemRepository

    @Autowired
    protected lateinit var paymentRepository: PaymentRepository

    @Autowired
    protected lateinit var outboxEventRepository: OutboxEventRepository

    @Autowired
    protected lateinit var bookingSagaRepository: BookingSagaRepository

    @Autowired
    protected lateinit var pointTransactionRepository: PointTransactionRepository

    @Autowired
    protected lateinit var userRepository: UserRepository

    @Autowired
    protected lateinit var userPointRepository: UserPointRepository

    @Autowired
    protected lateinit var userSessionRepository: UserSessionRepository

    @Autowired
    protected lateinit var clock: Clock

    @Autowired
    protected lateinit var mockReservationDataPlatformClient: MockReservationDataPlatformClient

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
        assertEquals(1, mockReservationDataPlatformClient.getSentPayloads().size)
        assertEquals(reservation.reservationId, mockReservationDataPlatformClient.getSentPayloads().single().reservationId)
        assertEquals(OutboxEventStatus.PUBLISHED, outboxEventRepository.findAll().single().eventStatus)
        assertEquals(BookingSagaStatus.COMPLETED, bookingSagaRepository.findByReservationId(reservation.reservationId)?.sagaStatus)
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
        seatHoldRepository.saveAndFlush(holdEntity)

        val secondHold = holdSeatsUseCase.execute(user.id!!, queueToken.queueToken, schedule.id!!, listOf(seat.id!!))

        assertTrue(secondHold.holdId != firstHold.holdId)
        assertEquals(HoldStatus.EXPIRED, seatHoldRepository.findById(firstHold.holdId).orElseThrow().holdStatus)
        assertEquals(HoldStatus.ACTIVE, seatHoldRepository.findById(secondHold.holdId).orElseThrow().holdStatus)
        assertEquals(SeatStatus.HELD, seatRepository.findById(seat.id!!).orElseThrow().seatStatus)
    }

    @Test
    fun `통합 테스트 - 배정 타임아웃 해제 스케줄러가 만료된 hold를 정리한다`() {
        val user = createUser()
        val concert = concertRepository.findAll().first()
        val schedule = scheduleRepository.findByConcertIdAndStatusOrderByStartAt(concert.id!!, ScheduleStatus.OPEN).first()
        val seat = seatRepository.findByScheduleIdOrderById(schedule.id!!).first()
        val queueToken = createAdmittedToken(user, concert.id!!, 1L)

        val hold = holdSeatsUseCase.execute(user.id!!, queueToken, schedule.id!!, listOf(seat.id!!))
        val holdEntity = seatHoldRepository.findById(hold.holdId).orElseThrow()
        holdEntity.holdExpiresAt = LocalDateTime.now(clock).minusMinutes(1)
        seatHoldRepository.saveAndFlush(holdEntity)

        reservationCleanupScheduler.cleanupExpiredResources()

        assertEquals(HoldStatus.EXPIRED, seatHoldRepository.findById(hold.holdId).orElseThrow().holdStatus)
        assertEquals(SeatStatus.AVAILABLE, seatRepository.findById(seat.id!!).orElseThrow().seatStatus)
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

    @Test
    fun `통합 테스트 - 회원가입과 로그인 후 인증 사용자를 조회할 수 있다`() {
        val email = "integration-${UUID.randomUUID()}@hhplus.kr"
        val signUp = authService.signUp(SignUpRequest("tester", email, "password123"))
        val login = authService.login(LoginRequest(email, "password123"))

        val signedUpUser = authService.requireUser("Bearer ${signUp.accessToken}")

        assertEquals("tester", signedUpUser.name)
        assertEquals(0L, userPointRepository.findById(signedUpUser.id!!).orElseThrow().balance)
        assertTrue(login.accessToken.isNotBlank())
    }

    @Test
    fun `통합 테스트 - 대기열 위치 조회는 현재 순번을 반환한다`() {
        val concert = concertRepository.findAll().first()
        val user1 = createUser()
        val user2 = createUser()

        val firstToken = queueService.issueToken(user1, concert.id!!)
        val secondToken = queueService.issueToken(user2, concert.id!!)
        val position = queueService.getPosition(user2.id!!, secondToken.queueToken)

        assertEquals("ADMITTED", queueTokenRepository.findById(firstToken.queueToken).orElseThrow().queueStatus.name)
        assertEquals(1L, position.currentPosition)
        assertEquals("WAITING", position.queueStatus)
    }

    @Test
    fun `통합 테스트 - 포인트 충전 후 잔액 조회가 반영된다`() {
        val user = createUser()

        concertFacadeService.charge(user, 70000L)
        val balance = concertFacadeService.getMyPoints(user)

        assertEquals(70000L, balance.balance)
        assertEquals(1, pointTransactionRepository.findAll().count { it.user.id == user.id && it.transactionType.name == "CHARGE" })
    }

    @Test
    fun `통합 테스트 - 콘서트 목록과 회차 및 좌석 조회가 반환된다`() {
        val user = createUser()
        val concertList = concertFacadeService.getConcerts(0, 20)
        val concert = concertRepository.findAll().first()
        val queueToken = createAdmittedToken(user, concert.id!!, 1L)

        val schedules = concertFacadeService.getSchedules(user, concert.id!!, queueToken)
        val seats = concertFacadeService.getSeats(user, schedules.items.first().scheduleId, queueToken)

        assertTrue(concertList.items.isNotEmpty())
        assertTrue(schedules.items.isNotEmpty())
        assertTrue(seats.items.isNotEmpty())
    }

    @Test
    fun `통합 테스트 - 예약 목록과 상세 조회가 반환된다`() {
        val user = createUser()
        val concert = concertRepository.findAll().first()
        val schedule = scheduleRepository.findByConcertIdAndStatusOrderByStartAt(concert.id!!, ScheduleStatus.OPEN).first()
        val seat = seatRepository.findByScheduleIdOrderById(schedule.id!!).first()
        val queueToken = createAdmittedToken(user, concert.id!!, 1L)

        val hold = holdSeatsUseCase.execute(user.id!!, queueToken, schedule.id!!, listOf(seat.id!!))
        val reservation = createReservationUseCase.execute(user.id!!, queueToken, hold.holdId)

        val listResponse = getReservationsUseCase.execute(user.id!!, queueToken, 0, 20)
        val detailResponse = getReservationUseCase.execute(user.id!!, queueToken, reservation.reservationId)

        assertEquals(1, listResponse.items.size)
        assertEquals(reservation.reservationId, listResponse.items.first().reservationId)
        assertEquals(reservation.reservationId, detailResponse.reservationId)
        assertEquals("PENDING_PAYMENT", detailResponse.status)
    }

    @Test
    fun `통합 테스트 - 동일 예약에 대한 동시 결제 요청은 하나만 성공한다`() {
        val user = createUser()
        val concert = concertRepository.findAll().first()
        val schedule = scheduleRepository.findByConcertIdAndStatusOrderByStartAt(concert.id!!, ScheduleStatus.OPEN).first()
        val seat = seatRepository.findByScheduleIdOrderById(schedule.id!!).first()
        val queueToken = createAdmittedToken(user, concert.id!!, 1L)

        concertFacadeService.charge(user, seat.price)
        val hold = holdSeatsUseCase.execute(user.id!!, queueToken, schedule.id!!, listOf(seat.id!!))
        val reservation = createReservationUseCase.execute(user.id!!, queueToken, hold.holdId)

        val successCount = AtomicInteger()
        val conflictCount = AtomicInteger()
        runConcurrently(2) {
            try {
                payReservationUseCase.execute(user.id!!, queueToken, reservation.reservationId, reservation.totalAmount, "CARD")
                successCount.incrementAndGet()
            } catch (_: ConflictException) {
                conflictCount.incrementAndGet()
            }
        }

        assertEquals(1, successCount.get())
        assertEquals(1, conflictCount.get())
        assertEquals(ReservationStatus.CONFIRMED, reservationRepository.findById(reservation.reservationId).orElseThrow().reservationStatus)
        assertEquals(1, paymentRepository.findAll().count { it.reservation.id == reservation.reservationId && it.paymentStatus == PaymentStatus.SUCCESS })
        assertEquals(SeatStatus.SOLD, seatRepository.findById(seat.id!!).orElseThrow().seatStatus)
    }

    @Test
    fun `통합 테스트 - 동일 예약에 대한 동시 취소 요청은 한 번만 환불된다`() {
        val user = createUser()
        val concert = concertRepository.findAll().first()
        val schedule = scheduleRepository.findByConcertIdAndStatusOrderByStartAt(concert.id!!, ScheduleStatus.OPEN).first()
        val seat = seatRepository.findByScheduleIdOrderById(schedule.id!!).first()
        val queueToken = createAdmittedToken(user, concert.id!!, 1L)

        concertFacadeService.charge(user, seat.price)
        val hold = holdSeatsUseCase.execute(user.id!!, queueToken, schedule.id!!, listOf(seat.id!!))
        val reservation = createReservationUseCase.execute(user.id!!, queueToken, hold.holdId)
        payReservationUseCase.execute(user.id!!, queueToken, reservation.reservationId, reservation.totalAmount, "CARD")
        val cancelQueueToken = createAdmittedToken(user, concert.id!!, 2L)

        val successCount = AtomicInteger()
        val conflictCount = AtomicInteger()
        runConcurrently(2) {
            try {
                cancelReservationUseCase.execute(user.id!!, cancelQueueToken, reservation.reservationId, "CANCELED")
                successCount.incrementAndGet()
            } catch (_: ConflictException) {
                conflictCount.incrementAndGet()
            }
        }

        assertEquals(1, successCount.get())
        assertEquals(1, conflictCount.get())
        assertEquals(ReservationStatus.CANCELED, reservationRepository.findById(reservation.reservationId).orElseThrow().reservationStatus)
        assertEquals(PaymentStatus.CANCELED, paymentRepository.findAll().single { it.reservation.id == reservation.reservationId }.paymentStatus)
        assertEquals(seat.price, userPointRepository.findById(user.id!!).orElseThrow().balance)
        assertEquals(1, pointTransactionRepository.findAll().count { it.user.id == user.id && it.transactionType.name == "REFUND" })
        assertEquals(SeatStatus.AVAILABLE, seatRepository.findById(seat.id!!).orElseThrow().seatStatus)
    }

    protected fun createUser(): UserEntity {
        val email = "test-${UUID.randomUUID()}@hhplus.kr"
        val authToken = authService.signUp(SignUpRequest("tester", email, "password123"))
        return authService.requireUser("Bearer ${authToken.accessToken}")
    }

    protected fun createAdmittedToken(user: UserEntity, concertId: Long, queueNumber: Long): String {
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

    protected fun runConcurrently(threadCount: Int, task: (Int) -> Unit) {
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
