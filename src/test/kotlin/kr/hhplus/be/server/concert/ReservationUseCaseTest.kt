package kr.hhplus.be.server.concert

import kr.hhplus.be.server.concert.common.ConflictException
import kr.hhplus.be.server.concert.common.ForbiddenException
import kr.hhplus.be.server.concert.reservation.application.CreateReservationUseCase
import kr.hhplus.be.server.concert.reservation.application.HoldPort
import kr.hhplus.be.server.concert.reservation.application.HoldSeatsUseCase
import kr.hhplus.be.server.concert.reservation.application.PayReservationUseCase
import kr.hhplus.be.server.concert.reservation.application.PaymentPort
import kr.hhplus.be.server.concert.reservation.application.PointPort
import kr.hhplus.be.server.concert.reservation.application.ReservationPort
import kr.hhplus.be.server.concert.reservation.application.ReservationQueuePort
import kr.hhplus.be.server.concert.reservation.application.SeatLoadPort
import kr.hhplus.be.server.concert.reservation.domain.HoldSnapshot
import kr.hhplus.be.server.concert.reservation.domain.PaymentSnapshot
import kr.hhplus.be.server.concert.reservation.domain.ReservationSnapshot
import kr.hhplus.be.server.concert.reservation.domain.SeatSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class ReservationUseCaseTest {
    private val clock = Clock.fixed(Instant.parse("2026-03-01T00:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `hold usecase는 외부 포트만 mock으로 두고 좌석 충돌을 검증한다`() {
        val queuePort = mock(ReservationQueuePort::class.java)
        val seatLoadPort = mock(SeatLoadPort::class.java)
        val holdPort = mock(HoldPort::class.java)
        val reservationPort = mock(ReservationPort::class.java)
        val useCase = HoldSeatsUseCase(queuePort, seatLoadPort, holdPort, reservationPort, clock)

        `when`(seatLoadPort.getScheduleConcertId(100L)).thenReturn(1L)
        `when`(queuePort.validateForWrite("queue-token", 1L, 1L)).thenReturn("queue-token")
        `when`(seatLoadPort.getSeatsForUpdate(listOf(1L, 2L))).thenReturn(
            listOf(
                SeatSnapshot(1L, 100L, 50000L, "AVAILABLE"),
                SeatSnapshot(2L, 100L, 50000L, "HELD"),
            ),
        )

        assertThrows(ConflictException::class.java) {
            useCase.execute(1L, "queue-token", 100L, listOf(1L, 2L))
        }

        verify(holdPort).expireActiveHolds(LocalDateTime.now(clock))
        verify(reservationPort).expirePendingReservations(LocalDateTime.now(clock))
        verifyNoMoreInteractions(holdPort)
    }

    @Test
    fun `hold usecase는 좌석을 5분간 임시 배정한다`() {
        val queuePort = mock(ReservationQueuePort::class.java)
        val seatLoadPort = mock(SeatLoadPort::class.java)
        val holdPort = mock(HoldPort::class.java)
        val reservationPort = mock(ReservationPort::class.java)
        val useCase = HoldSeatsUseCase(queuePort, seatLoadPort, holdPort, reservationPort, clock)

        `when`(seatLoadPort.getScheduleConcertId(100L)).thenReturn(1L)
        `when`(queuePort.validateForWrite("queue-token", 1L, 1L)).thenReturn("queue-token")
        `when`(seatLoadPort.getSeatsForUpdate(listOf(1L))).thenReturn(listOf(SeatSnapshot(1L, 100L, 50000L, "AVAILABLE")))
        `when`(
            holdPort.createHold(
                1L,
                100L,
                "queue-token",
                listOf(1L),
                50000L,
                LocalDateTime.now(clock).plusMinutes(5),
            ),
        ).thenReturn(
            HoldSnapshot("hold-1", 1L, 1L, 100L, "queue-token", listOf(1L), 50000L, "ACTIVE", LocalDateTime.now(clock).plusMinutes(5)),
        )

        val result = useCase.execute(1L, "queue-token", 100L, listOf(1L))

        assertEquals(LocalDateTime.now(clock).plusMinutes(5), result.holdExpiresAt)
        verify(seatLoadPort).markSeatsHeld(listOf(1L))
    }

    @Test
    fun `create reservation usecase는 hold 소유자만 예약 확정할 수 있다`() {
        val queuePort = mock(ReservationQueuePort::class.java)
        val holdPort = mock(HoldPort::class.java)
        val reservationPort = mock(ReservationPort::class.java)
        val useCase = CreateReservationUseCase(queuePort, holdPort, reservationPort, clock)

        `when`(holdPort.getHold("hold-1")).thenReturn(
            HoldSnapshot("hold-1", 2L, 1L, 100L, "queue-token", listOf(1L), 50000L, "ACTIVE", LocalDateTime.now(clock).plusMinutes(5)),
        )

        assertThrows(ForbiddenException::class.java) {
            useCase.execute(1L, "queue-token", "hold-1")
        }

        verify(reservationPort, never()).createReservation(1L, 100L, "hold-1", 50000L, listOf(1L), LocalDateTime.now(clock))
    }

    @Test
    fun `pay usecase는 포트 mock만으로 결제 성공 흐름을 검증한다`() {
        val queuePort = mock(ReservationQueuePort::class.java)
        val reservationPort = mock(ReservationPort::class.java)
        val paymentPort = mock(PaymentPort::class.java)
        val pointPort = mock(PointPort::class.java)
        val seatLoadPort = mock(SeatLoadPort::class.java)
        val holdPort = mock(HoldPort::class.java)
        val useCase = PayReservationUseCase(queuePort, reservationPort, paymentPort, pointPort, seatLoadPort, holdPort, clock)

        `when`(reservationPort.getReservationForUpdate(10L)).thenReturn(
            ReservationSnapshot(10L, 1L, 1L, 100L, "hold-1", listOf(1L, 2L), 100000L, "PENDING_PAYMENT", LocalDateTime.now(clock)),
        )
        `when`(queuePort.validateForWrite("queue-token", 1L, 1L)).thenReturn("queue-token")
        `when`(paymentPort.hasSuccessfulPayment(10L)).thenReturn(false)
        `when`(paymentPort.createSuccessPayment(10L, 100000L, "CARD", LocalDateTime.now(clock))).thenReturn(
            PaymentSnapshot(1L, 10L, 100000L, "SUCCESS", LocalDateTime.now(clock)),
        )

        val result = useCase.execute(1L, "queue-token", 10L, 100000L, "CARD")

        assertEquals("SUCCESS", result.status)
        verify(pointPort).deduct(1L, 100000L, LocalDateTime.now(clock))
        verify(reservationPort).markReservationConfirmed(10L)
        verify(seatLoadPort).markSeatsSold(listOf(1L, 2L))
        verify(queuePort).expireAfterPayment("queue-token")
    }

    @Test
    fun `pay usecase는 포인트 부족 시 failed payment를 기록하고 좌석을 해제한다`() {
        val queuePort = mock(ReservationQueuePort::class.java)
        val reservationPort = mock(ReservationPort::class.java)
        val paymentPort = mock(PaymentPort::class.java)
        val pointPort = mock(PointPort::class.java)
        val seatLoadPort = mock(SeatLoadPort::class.java)
        val holdPort = mock(HoldPort::class.java)
        val useCase = PayReservationUseCase(queuePort, reservationPort, paymentPort, pointPort, seatLoadPort, holdPort, clock)

        `when`(reservationPort.getReservationForUpdate(10L)).thenReturn(
            ReservationSnapshot(10L, 1L, 1L, 100L, "hold-1", listOf(1L, 2L), 100000L, "PENDING_PAYMENT", LocalDateTime.now(clock)),
        )
        `when`(queuePort.validateForWrite("queue-token", 1L, 1L)).thenReturn("queue-token")
        `when`(paymentPort.hasSuccessfulPayment(10L)).thenReturn(false)
        org.mockito.Mockito.doThrow(ConflictException("포인트가 부족합니다.")).`when`(pointPort).deduct(1L, 100000L, LocalDateTime.now(clock))

        assertThrows(ConflictException::class.java) {
            useCase.execute(1L, "queue-token", 10L, 100000L, "CARD")
        }

        verify(paymentPort).createFailedPayment(10L, 100000L, "CARD", LocalDateTime.now(clock), "포인트가 부족합니다.")
        verify(seatLoadPort).markSeatsAvailable(listOf(1L, 2L))
        verify(holdPort).markHoldExpired("hold-1")
        verify(reservationPort).markReservationExpiredByHold("hold-1", LocalDateTime.now(clock))
    }
}
