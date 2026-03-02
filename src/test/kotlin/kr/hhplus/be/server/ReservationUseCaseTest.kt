package kr.hhplus.be.server

import kr.hhplus.be.server.common.ConflictException
import kr.hhplus.be.server.common.ForbiddenException
import kr.hhplus.be.server.common.lock.DistributedLockExecutor
import kr.hhplus.be.server.reservation.application.CreateReservationUseCase
import kr.hhplus.be.server.reservation.application.HoldPort
import kr.hhplus.be.server.reservation.application.HoldSeatsUseCase
import kr.hhplus.be.server.reservation.application.CancelReservationUseCase
import kr.hhplus.be.server.reservation.application.PayReservationUseCase
import kr.hhplus.be.server.reservation.application.PaymentPort
import kr.hhplus.be.server.reservation.application.PointPort
import kr.hhplus.be.server.reservation.application.ReservationPort
import kr.hhplus.be.server.reservation.application.ReservationQueuePort
import kr.hhplus.be.server.reservation.application.SeatLoadPort
import kr.hhplus.be.server.reservation.domain.HoldSnapshot
import kr.hhplus.be.server.reservation.domain.PaymentSnapshot
import kr.hhplus.be.server.reservation.domain.ReservationSnapshot
import kr.hhplus.be.server.reservation.domain.SeatSnapshot
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class ReservationUseCaseTest {
    private val clock = Clock.fixed(Instant.parse("2026-03-01T00:00:00Z"), ZoneOffset.UTC)
    private val lockExecutor = object : DistributedLockExecutor {
        override fun <T> execute(key: String, action: () -> T): T = action()
    }
    private val transactionTemplate = TransactionTemplate(NoopTransactionManager())

    @Test
    fun `hold usecase는 외부 포트만 mock으로 두고 좌석 충돌을 검증한다`() {
        val queuePort = mockk<ReservationQueuePort>()
        val seatLoadPort = mockk<SeatLoadPort>(relaxed = true)
        val holdPort = mockk<HoldPort>(relaxed = true)
        val reservationPort = mockk<ReservationPort>(relaxed = true)
        val useCase = HoldSeatsUseCase(queuePort, seatLoadPort, holdPort, reservationPort, lockExecutor, transactionTemplate, clock)

        every { seatLoadPort.getScheduleConcertId(100L) } returns 1L
        every { queuePort.validateForWrite("queue-token", 1L, 1L) } returns "queue-token"
        every { seatLoadPort.getSeatsForUpdate(listOf(1L, 2L)) } returns
            listOf(
                SeatSnapshot(1L, 100L, 50000L, "AVAILABLE"),
                SeatSnapshot(2L, 100L, 50000L, "HELD"),
            )

        assertThrows(ConflictException::class.java) {
            useCase.execute(1L, "queue-token", 100L, listOf(1L, 2L))
        }

        verify { holdPort.expireActiveHolds(LocalDateTime.now(clock)) }
        verify { reservationPort.expirePendingReservations(LocalDateTime.now(clock)) }
        verify(exactly = 0) { holdPort.createHold(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `hold usecase는 좌석을 5분간 임시 배정한다`() {
        val queuePort = mockk<ReservationQueuePort>()
        val seatLoadPort = mockk<SeatLoadPort>(relaxed = true)
        val holdPort = mockk<HoldPort>(relaxed = true)
        val reservationPort = mockk<ReservationPort>(relaxed = true)
        val useCase = HoldSeatsUseCase(queuePort, seatLoadPort, holdPort, reservationPort, lockExecutor, transactionTemplate, clock)

        every { seatLoadPort.getScheduleConcertId(100L) } returns 1L
        every { queuePort.validateForWrite("queue-token", 1L, 1L) } returns "queue-token"
        every { seatLoadPort.getSeatsForUpdate(listOf(1L)) } returns listOf(SeatSnapshot(1L, 100L, 50000L, "AVAILABLE"))
        every {
            holdPort.createHold(
                1L,
                100L,
                "queue-token",
                listOf(1L),
                50000L,
                LocalDateTime.now(clock).plusMinutes(5),
            )
        } returns
            HoldSnapshot("hold-1", 1L, 1L, 100L, "queue-token", listOf(1L), 50000L, "ACTIVE", LocalDateTime.now(clock).plusMinutes(5))

        val result = useCase.execute(1L, "queue-token", 100L, listOf(1L))

        assertEquals(LocalDateTime.now(clock).plusMinutes(5), result.holdExpiresAt)
        verify { seatLoadPort.markSeatsHeld(listOf(1L)) }
    }

    @Test
    fun `create reservation usecase는 hold 소유자만 예약 확정할 수 있다`() {
        val queuePort = mockk<ReservationQueuePort>(relaxed = true)
        val holdPort = mockk<HoldPort>(relaxed = true)
        val reservationPort = mockk<ReservationPort>(relaxed = true)
        val useCase = CreateReservationUseCase(queuePort, holdPort, reservationPort, lockExecutor, transactionTemplate, clock)

        every { holdPort.getHoldForUpdate("hold-1") } returns
            HoldSnapshot("hold-1", 2L, 1L, 100L, "queue-token", listOf(1L), 50000L, "ACTIVE", LocalDateTime.now(clock).plusMinutes(5))

        assertThrows(ForbiddenException::class.java) {
            useCase.execute(1L, "queue-token", "hold-1")
        }

        verify(exactly = 0) { reservationPort.createReservation(1L, 100L, "hold-1", 50000L, listOf(1L), LocalDateTime.now(clock)) }
    }

    @Test
    fun `create reservation usecase는 만료된 hold를 예약할 수 없다`() {
        val queuePort = mockk<ReservationQueuePort>(relaxed = true)
        val holdPort = mockk<HoldPort>(relaxed = true)
        val reservationPort = mockk<ReservationPort>(relaxed = true)
        val useCase = CreateReservationUseCase(queuePort, holdPort, reservationPort, lockExecutor, transactionTemplate, clock)

        every { holdPort.getHoldForUpdate("hold-1") } returns
            HoldSnapshot("hold-1", 1L, 1L, 100L, "queue-token", listOf(1L), 50000L, "ACTIVE", LocalDateTime.now(clock).minusSeconds(1))

        assertThrows(ConflictException::class.java) {
            useCase.execute(1L, "queue-token", "hold-1")
        }

        verify(exactly = 0) { reservationPort.createReservation(1L, 100L, "hold-1", 50000L, listOf(1L), LocalDateTime.now(clock)) }
    }

    @Test
    fun `pay usecase는 포트 mock만으로 결제 성공 흐름을 검증한다`() {
        val queuePort = mockk<ReservationQueuePort>(relaxed = true)
        val reservationPort = mockk<ReservationPort>(relaxed = true)
        val paymentPort = mockk<PaymentPort>()
        val pointPort = mockk<PointPort>(relaxed = true)
        val seatLoadPort = mockk<SeatLoadPort>(relaxed = true)
        val holdPort = mockk<HoldPort>(relaxed = true)
        val useCase = PayReservationUseCase(queuePort, reservationPort, paymentPort, pointPort, seatLoadPort, holdPort, lockExecutor, transactionTemplate, clock)

        every { reservationPort.getReservationForUpdate(10L) } returns
            ReservationSnapshot(10L, 1L, 1L, 100L, "hold-1", listOf(1L, 2L), 100000L, "PENDING_PAYMENT", LocalDateTime.now(clock))
        every { queuePort.validateForWrite("queue-token", 1L, 1L) } returns "queue-token"
        every { paymentPort.hasSuccessfulPayment(10L) } returns false
        every { paymentPort.createSuccessPayment(10L, 100000L, "CARD", LocalDateTime.now(clock)) } returns
            PaymentSnapshot(1L, 10L, 100000L, "SUCCESS", LocalDateTime.now(clock))

        val result = useCase.execute(1L, "queue-token", 10L, 100000L, "CARD")

        assertEquals("SUCCESS", result.status)
        verify { pointPort.deduct(1L, 100000L, LocalDateTime.now(clock)) }
        verify { reservationPort.markReservationConfirmed(10L) }
        verify { seatLoadPort.markSeatsSold(listOf(1L, 2L)) }
        verify { queuePort.expireAfterPayment("queue-token") }
    }

    @Test
    fun `pay usecase는 포인트 부족 시 failed payment를 기록하고 좌석을 해제한다`() {
        val queuePort = mockk<ReservationQueuePort>(relaxed = true)
        val reservationPort = mockk<ReservationPort>(relaxed = true)
        val paymentPort = mockk<PaymentPort>(relaxed = true)
        val pointPort = mockk<PointPort>()
        val seatLoadPort = mockk<SeatLoadPort>(relaxed = true)
        val holdPort = mockk<HoldPort>(relaxed = true)
        val useCase = PayReservationUseCase(queuePort, reservationPort, paymentPort, pointPort, seatLoadPort, holdPort, lockExecutor, transactionTemplate, clock)

        every { reservationPort.getReservationForUpdate(10L) } returns
            ReservationSnapshot(10L, 1L, 1L, 100L, "hold-1", listOf(1L, 2L), 100000L, "PENDING_PAYMENT", LocalDateTime.now(clock))
        every { queuePort.validateForWrite("queue-token", 1L, 1L) } returns "queue-token"
        every { paymentPort.hasSuccessfulPayment(10L) } returns false
        every { pointPort.deduct(1L, 100000L, LocalDateTime.now(clock)) } throws ConflictException("포인트가 부족합니다.")

        assertThrows(ConflictException::class.java) {
            useCase.execute(1L, "queue-token", 10L, 100000L, "CARD")
        }

        verify { paymentPort.createFailedPayment(10L, 100000L, "CARD", LocalDateTime.now(clock), "포인트가 부족합니다.") }
        verify { seatLoadPort.markSeatsAvailable(listOf(1L, 2L)) }
        verify { holdPort.markHoldExpired("hold-1") }
        verify { reservationPort.markReservationExpiredByHold("hold-1", LocalDateTime.now(clock)) }
    }

    @Test
    fun `cancel usecase는 결제 완료된 예약을 취소하면 포인트를 환불한다`() {
        val queuePort = mockk<ReservationQueuePort>(relaxed = true)
        val reservationPort = mockk<ReservationPort>(relaxed = true)
        val paymentPort = mockk<PaymentPort>()
        val pointPort = mockk<PointPort>(relaxed = true)
        val seatLoadPort = mockk<SeatLoadPort>(relaxed = true)
        val useCase = CancelReservationUseCase(queuePort, reservationPort, paymentPort, pointPort, seatLoadPort, lockExecutor, transactionTemplate, clock)

        every { reservationPort.getReservationForUpdate(10L) } returns
            ReservationSnapshot(10L, 1L, 1L, 100L, "hold-1", listOf(1L, 2L), 100000L, "CONFIRMED", LocalDateTime.now(clock))
        every { queuePort.validateForWrite("queue-token", 1L, 1L) } returns "queue-token"
        every { paymentPort.cancelSuccessPayment(10L, LocalDateTime.now(clock)) } returns true
        every { reservationPort.getReservation(10L) } returns
            ReservationSnapshot(10L, 1L, 1L, 100L, "hold-1", listOf(1L, 2L), 100000L, "CANCELED", LocalDateTime.now(clock))

        val result = useCase.execute(1L, "queue-token", 10L, "CANCELED")

        assertEquals("CANCELED", result.status)
        verify { queuePort.validateForWrite("queue-token", 1L, 1L) }
        verify { seatLoadPort.markSeatsAvailable(listOf(1L, 2L)) }
        verify { pointPort.refund(1L, 100000L, LocalDateTime.now(clock)) }
        verify { reservationPort.markReservationCanceled(10L, LocalDateTime.now(clock)) }
    }

    private class NoopTransactionManager : PlatformTransactionManager {
        override fun getTransaction(definition: TransactionDefinition?): TransactionStatus = SimpleTransactionStatus()
        override fun commit(status: TransactionStatus) = Unit
        override fun rollback(status: TransactionStatus) = Unit
    }
}
