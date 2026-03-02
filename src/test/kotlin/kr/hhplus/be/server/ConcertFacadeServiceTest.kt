package kr.hhplus.be.server

import kr.hhplus.be.server.common.cache.ConcertCacheService
import kr.hhplus.be.server.common.lock.DistributedLockExecutor
import kr.hhplus.be.server.common.ValidationException
import kr.hhplus.be.server.domain.entity.ConcertEntity
import kr.hhplus.be.server.domain.entity.ConcertScheduleEntity
import kr.hhplus.be.server.domain.entity.SeatEntity
import kr.hhplus.be.server.domain.entity.UserEntity
import kr.hhplus.be.server.domain.entity.UserPointEntity
import kr.hhplus.be.server.domain.entity.PointTransactionEntity
import kr.hhplus.be.server.domain.enums.ScheduleStatus
import kr.hhplus.be.server.domain.enums.SeatStatus
import kr.hhplus.be.server.domain.repository.ConcertRepository
import kr.hhplus.be.server.domain.repository.ConcertScheduleRepository
import kr.hhplus.be.server.domain.repository.PointTransactionRepository
import kr.hhplus.be.server.domain.repository.SeatRepository
import kr.hhplus.be.server.domain.repository.UserPointRepository
import kr.hhplus.be.server.queue.QueueService
import kr.hhplus.be.server.reservation.application.HoldPort
import kr.hhplus.be.server.reservation.application.ReservationPort
import kr.hhplus.be.server.service.ConcertFacadeService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionException
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Optional

class ConcertFacadeServiceTest {
    private val clock = Clock.fixed(Instant.parse("2026-03-01T00:00:00Z"), ZoneOffset.UTC)
    private val concertRepository = mockk<ConcertRepository>()
    private val scheduleRepository = mockk<ConcertScheduleRepository>()
    private val seatRepository = mockk<SeatRepository>()
    private val userPointRepository = mockk<UserPointRepository>()
    private val pointTransactionRepository = mockk<PointTransactionRepository>()
    private val queueService = mockk<QueueService>(relaxed = true)
    private val holdPort = mockk<HoldPort>(relaxed = true)
    private val reservationPort = mockk<ReservationPort>(relaxed = true)
    private val concertCacheService = mockk<ConcertCacheService>()
    private val lockExecutor = object : DistributedLockExecutor {
        override fun <T> execute(key: String, action: () -> T): T = action()
    }
    private val transactionTemplate = TransactionTemplate(NoopTransactionManager())

    private val service = ConcertFacadeService(
        concertRepository,
        scheduleRepository,
        seatRepository,
        userPointRepository,
        pointTransactionRepository,
        queueService,
        holdPort,
        reservationPort,
        concertCacheService,
        lockExecutor,
        transactionTemplate,
        clock,
    )

    @Test
    fun `charge는 amount가 0 이하면 유효성 오류를 반환한다`() {
        val user = userEntity()

        assertThrows(ValidationException::class.java) {
            service.charge(user, 0)
        }

        verify(exactly = 0) { userPointRepository.findForUpdate(any()) }
    }

    @Test
    fun `getConcerts는 페이지 응답을 반환한다`() {
        val concert = concertEntity()
        every { concertCacheService.getConcerts(0, 20, any()) } answers {
            @Suppress("UNCHECKED_CAST")
            (args[2] as () -> kr.hhplus.be.server.api.ConcertListResponse).invoke()
        }
        every { concertRepository.findAll(org.springframework.data.domain.PageRequest.of(0, 20)) } returns
            org.springframework.data.domain.PageImpl(listOf(concert), org.springframework.data.domain.PageRequest.of(0, 20), 1)

        val response = service.getConcerts(0, 20)

        assertEquals(1, response.items.size)
        assertEquals("HH Plus Concert", response.items.first().title)
    }

    @Test
    fun `getSchedules는 예약 가능 날짜 목록을 반환한다`() {
        val user = userEntity()
        val concert = concertEntity()
        val schedule = scheduleEntity(concert)
        every { concertCacheService.getSchedules(1L, any()) } answers {
            @Suppress("UNCHECKED_CAST")
            (args[1] as () -> kr.hhplus.be.server.api.ScheduleListResponse).invoke()
        }
        every { concertRepository.findById(1L) } returns Optional.of(concert)
        every { scheduleRepository.findByConcertIdAndStatusOrderByStartAt(1L, ScheduleStatus.OPEN) } returns listOf(schedule)
        every { seatRepository.findByScheduleIdOrderById(1L) } returns
            listOf(
                seatEntity(schedule, 1L, SeatStatus.AVAILABLE),
                seatEntity(schedule, 2L, SeatStatus.HELD),
            )

        val response = service.getSchedules(user, 1L, "queue-token")

        assertEquals(1, response.items.size)
        assertEquals(1, response.items.first().availableSeat)
        verify { queueService.validateQueueTokenForRead("queue-token", 1L, 1L) }
    }

    @Test
    fun `getSeats는 좌석 현황을 반환한다`() {
        val user = userEntity()
        val concert = concertEntity()
        val schedule = scheduleEntity(concert)
        every { concertCacheService.getSeats(1L, any()) } answers {
            @Suppress("UNCHECKED_CAST")
            (args[1] as () -> kr.hhplus.be.server.api.SeatListResponse).invoke()
        }
        every { scheduleRepository.findById(1L) } returns Optional.of(schedule)
        every { seatRepository.findByScheduleIdOrderById(1L) } returns listOf(seatEntity(schedule, 1L, SeatStatus.AVAILABLE))

        val response = service.getSeats(user, 1L, "queue-token")

        assertEquals(1, response.items.size)
        assertEquals("AVAILABLE", response.items.first().status)
        verify { queueService.validateQueueTokenForRead("queue-token", 1L, 1L) }
    }

    @Test
    fun `getMyPoints는 현재 포인트를 반환한다`() {
        val user = userEntity()
        every { userPointRepository.findById(1L) } returns
            Optional.of(UserPointEntity(userId = 1L, user = user, balance = 200000L, updatedAt = LocalDateTime.now(clock)))

        val response = service.getMyPoints(user)

        assertEquals(200000L, response.balance)
    }

    @Test
    fun `charge는 금액만큼 잔액을 증가시키고 거래를 기록한다`() {
        val user = userEntity()
        val point = UserPointEntity(userId = 1L, user = user, balance = 100000L, updatedAt = LocalDateTime.now(clock))
        every { userPointRepository.findForUpdate(1L) } returns point
        every { pointTransactionRepository.save(any<PointTransactionEntity>()) } answers { firstArg() }

        val response = service.charge(user, 50000L)

        assertEquals(50000L, response.chargedAmount)
        assertEquals(150000L, response.balanceAfter)
        assertEquals(150000L, point.balance)
        verify { pointTransactionRepository.save(any<PointTransactionEntity>()) }
    }

    private fun userEntity(): UserEntity = UserEntity("user@test.com", "user", "password123").apply { id = 1L }

    private fun concertEntity(): ConcertEntity = ConcertEntity(
        title = "HH Plus Concert",
        venueName = "Olympic Hall",
        bookingOpenAt = LocalDateTime.now(clock),
        bookingCloseAt = LocalDateTime.now(clock).plusDays(5),
    ).apply { id = 1L }

    private fun scheduleEntity(concert: ConcertEntity): ConcertScheduleEntity = ConcertScheduleEntity(
        concert = concert,
        startAt = LocalDateTime.now(clock).plusDays(1),
        endAt = LocalDateTime.now(clock).plusDays(1).plusHours(2),
        status = ScheduleStatus.OPEN,
    ).apply { id = 1L }

    private fun seatEntity(schedule: ConcertScheduleEntity, id: Long, status: SeatStatus): SeatEntity = SeatEntity(
        schedule = schedule,
        section = "A",
        rowLabel = "1",
        seatNumber = id.toString(),
        price = 50000L,
        seatStatus = status,
    ).apply { this.id = id }

    private class NoopTransactionManager : PlatformTransactionManager {
        override fun getTransaction(definition: TransactionDefinition?): TransactionStatus = SimpleTransactionStatus()
        override fun commit(status: TransactionStatus) = Unit
        override fun rollback(status: TransactionStatus) = Unit
    }
}
