package kr.hhplus.be.server.concert

import kr.hhplus.be.server.concert.common.ValidationException
import kr.hhplus.be.server.concert.domain.entity.ConcertEntity
import kr.hhplus.be.server.concert.domain.entity.ConcertScheduleEntity
import kr.hhplus.be.server.concert.domain.entity.SeatEntity
import kr.hhplus.be.server.concert.domain.entity.UserEntity
import kr.hhplus.be.server.concert.domain.entity.UserPointEntity
import kr.hhplus.be.server.concert.domain.enums.ScheduleStatus
import kr.hhplus.be.server.concert.domain.enums.SeatStatus
import kr.hhplus.be.server.concert.domain.repository.ConcertRepository
import kr.hhplus.be.server.concert.domain.repository.ConcertScheduleRepository
import kr.hhplus.be.server.concert.domain.repository.PointTransactionRepository
import kr.hhplus.be.server.concert.domain.repository.SeatRepository
import kr.hhplus.be.server.concert.domain.repository.UserPointRepository
import kr.hhplus.be.server.concert.queue.QueueService
import kr.hhplus.be.server.concert.reservation.application.HoldPort
import kr.hhplus.be.server.concert.reservation.application.ReservationPort
import kr.hhplus.be.server.concert.service.ConcertFacadeService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Optional

class ConcertFacadeServiceTest {
    private val clock = Clock.fixed(Instant.parse("2026-03-01T00:00:00Z"), ZoneOffset.UTC)
    private val concertRepository: ConcertRepository = mock(ConcertRepository::class.java)
    private val scheduleRepository: ConcertScheduleRepository = mock(ConcertScheduleRepository::class.java)
    private val seatRepository: SeatRepository = mock(SeatRepository::class.java)
    private val userPointRepository: UserPointRepository = mock(UserPointRepository::class.java)
    private val pointTransactionRepository: PointTransactionRepository = mock(PointTransactionRepository::class.java)
    private val queueService: QueueService = mock(QueueService::class.java)
    private val holdPort: HoldPort = mock(HoldPort::class.java)
    private val reservationPort: ReservationPort = mock(ReservationPort::class.java)

    private val service = ConcertFacadeService(
        concertRepository,
        scheduleRepository,
        seatRepository,
        userPointRepository,
        pointTransactionRepository,
        queueService,
        holdPort,
        reservationPort,
        clock,
    )

    @Test
    fun `charge는 amount가 0 이하면 유효성 오류를 반환한다`() {
        val user = userEntity()

        assertThrows(ValidationException::class.java) {
            service.charge(user, 0)
        }

        verify(userPointRepository, never()).findForUpdate(anyLong())
    }

    @Test
    fun `getConcerts는 페이지 응답을 반환한다`() {
        val concert = concertEntity()
        `when`(concertRepository.findAll(org.springframework.data.domain.PageRequest.of(0, 20))).thenReturn(
            org.springframework.data.domain.PageImpl(listOf(concert), org.springframework.data.domain.PageRequest.of(0, 20), 1),
        )

        val response = service.getConcerts(0, 20)

        assertEquals(1, response.items.size)
        assertEquals("HH Plus Concert", response.items.first().title)
    }

    @Test
    fun `getSchedules는 예약 가능 날짜 목록을 반환한다`() {
        val user = userEntity()
        val concert = concertEntity()
        val schedule = scheduleEntity(concert)
        `when`(concertRepository.findById(1L)).thenReturn(Optional.of(concert))
        `when`(scheduleRepository.findByConcertIdAndStatusOrderByStartAt(1L, ScheduleStatus.OPEN)).thenReturn(listOf(schedule))
        `when`(seatRepository.findByScheduleIdOrderById(1L)).thenReturn(
            listOf(
                seatEntity(schedule, 1L, SeatStatus.AVAILABLE),
                seatEntity(schedule, 2L, SeatStatus.HELD),
            ),
        )

        val response = service.getSchedules(user, 1L, "queue-token")

        assertEquals(1, response.items.size)
        assertEquals(1, response.items.first().availableSeat)
        verify(queueService).validateQueueTokenForRead("queue-token", 1L, 1L)
    }

    @Test
    fun `getSeats는 좌석 현황을 반환한다`() {
        val user = userEntity()
        val concert = concertEntity()
        val schedule = scheduleEntity(concert)
        `when`(scheduleRepository.findById(1L)).thenReturn(Optional.of(schedule))
        `when`(seatRepository.findByScheduleIdOrderById(1L)).thenReturn(listOf(seatEntity(schedule, 1L, SeatStatus.AVAILABLE)))

        val response = service.getSeats(user, 1L, "queue-token")

        assertEquals(1, response.items.size)
        assertEquals("AVAILABLE", response.items.first().status)
        verify(queueService).validateQueueTokenForRead("queue-token", 1L, 1L)
    }

    @Test
    fun `getMyPoints는 현재 포인트를 반환한다`() {
        val user = userEntity()
        `when`(userPointRepository.findById(1L)).thenReturn(
            Optional.of(UserPointEntity(userId = 1L, user = user, balance = 200000L, updatedAt = LocalDateTime.now(clock))),
        )

        val response = service.getMyPoints(user)

        assertEquals(200000L, response.balance)
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
}
