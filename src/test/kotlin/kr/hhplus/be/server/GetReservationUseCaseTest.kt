package kr.hhplus.be.server

import kr.hhplus.be.server.api.ReservationListResponse
import kr.hhplus.be.server.api.ReservationResponse
import kr.hhplus.be.server.reservation.application.GetReservationUseCase
import kr.hhplus.be.server.reservation.application.GetReservationsUseCase
import kr.hhplus.be.server.reservation.application.ReservationPort
import kr.hhplus.be.server.reservation.application.ReservationQueuePort
import kr.hhplus.be.server.reservation.domain.ReservationSnapshot
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class GetReservationUseCaseTest {
    private val clock = Clock.fixed(Instant.parse("2026-03-01T00:00:00Z"), ZoneOffset.UTC)
    private val queuePort = mockk<ReservationQueuePort>(relaxed = true)
    private val reservationPort = mockk<ReservationPort>(relaxed = true)

    @Test
    fun `getReservation은 예약 상세를 반환한다`() {
        val useCase = GetReservationUseCase(queuePort, reservationPort, clock)
        every { reservationPort.getReservation(10L) } returns
            ReservationSnapshot(10L, 1L, 1L, 100L, "hold-1", listOf(1L, 2L), 100000L, "CONFIRMED", LocalDateTime.now(clock))

        val result: ReservationResponse = useCase.execute(1L, "queue-token", 10L)

        assertEquals(10L, result.reservationId)
        assertEquals("CONFIRMED", result.status)
        verify { queuePort.validateForRead("queue-token", 1L, 1L) }
    }

    @Test
    fun `getReservations는 예약 목록을 반환한다`() {
        val useCase = GetReservationsUseCase(queuePort, reservationPort, clock)
        every { reservationPort.getReservations(1L, 0, 20) } returns
            ReservationListResponse(
                items = listOf(
                    ReservationResponse(10L, 1L, 100L, listOf(1L), 50000L, "PENDING_PAYMENT", LocalDateTime.now(clock)),
                ),
                page = 0,
                size = 20,
                total = 1,
            )

        val result = useCase.execute(1L, "queue-token", 0, 20)

        assertEquals(1, result.items.size)
        assertEquals(10L, result.items.first().reservationId)
        verify { queuePort.validateForRead("queue-token", 1L, null) }
    }
}
