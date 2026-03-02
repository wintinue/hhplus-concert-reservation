package kr.hhplus.be.server

import kr.hhplus.be.server.common.ConflictException
import kr.hhplus.be.server.common.NotFoundException
import kr.hhplus.be.server.common.lock.DistributedLockExecutor
import kr.hhplus.be.server.api.ReservationListResponse
import kr.hhplus.be.server.reservation.application.CancelReservationUseCase
import kr.hhplus.be.server.reservation.application.CreateReservationUseCase
import kr.hhplus.be.server.reservation.application.HoldPort
import kr.hhplus.be.server.reservation.application.HoldSeatsUseCase
import kr.hhplus.be.server.reservation.application.PayReservationUseCase
import kr.hhplus.be.server.reservation.application.PaymentPort
import kr.hhplus.be.server.reservation.application.PointPort
import kr.hhplus.be.server.reservation.application.ReservationPaymentEventPublisher
import kr.hhplus.be.server.reservation.application.ReservationPort
import kr.hhplus.be.server.reservation.application.ReservationQueuePort
import kr.hhplus.be.server.reservation.application.SeatLoadPort
import kr.hhplus.be.server.reservation.domain.HoldSnapshot
import kr.hhplus.be.server.reservation.domain.PaymentSnapshot
import kr.hhplus.be.server.reservation.domain.ReservationSnapshot
import kr.hhplus.be.server.reservation.domain.SeatSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.springframework.context.ApplicationEventPublisher

class ConcurrencyRuleTest {
    private val clock = Clock.fixed(Instant.parse("2026-03-01T00:00:00Z"), ZoneOffset.UTC)
    private val lockExecutor = object : DistributedLockExecutor {
        override fun <T> execute(key: String, action: () -> T): T = action()
    }
    private val transactionTemplate = TransactionTemplate(NoopTransactionManager())
    private val eventPublisher = ReservationPaymentEventPublisher(ApplicationEventPublisher { })

    @Test
    fun `동일 좌석에 대한 동시 선점 요청은 하나만 성공해야 한다`() {
        val now = LocalDateTime.now(clock)
        val queuePort = PassThroughQueuePort()
        val reservationPort = NoopReservationPort()
        val holdPort = InMemoryHoldPort(now)
        val seatLoadPort = InMemorySeatLoadPort(
            scheduleConcertId = 1L,
            seats = mutableMapOf(1L to SeatSnapshot(1L, 100L, 50000L, "AVAILABLE")),
        )
        val useCase = HoldSeatsUseCase(queuePort, seatLoadPort, holdPort, reservationPort, lockExecutor, transactionTemplate, clock)

        val successCount = AtomicInteger()
        val conflictCount = AtomicInteger()
        runConcurrently(2) { _ ->
            try {
                useCase.execute(1L, "queue-token", 100L, listOf(1L))
                successCount.incrementAndGet()
            } catch (_: ConflictException) {
                conflictCount.incrementAndGet()
            }
        }

        assertEquals(1, successCount.get())
        assertEquals(1, conflictCount.get())
    }

    @Test
    fun `동일 예약에 대한 동시 결제 요청은 하나만 성공해야 한다`() {
        val now = LocalDateTime.now(clock)
        val queuePort = PassThroughQueuePort()
        val holdPort = NoopHoldPort()
        val seatLoadPort = InMemorySeatLoadPort(
            scheduleConcertId = 1L,
            seats = mutableMapOf(
                1L to SeatSnapshot(1L, 100L, 50000L, "HELD"),
                2L to SeatSnapshot(2L, 100L, 50000L, "HELD"),
            ),
        )
        val reservationPort = SingleReservationPort(
            ReservationSnapshot(10L, 1L, 1L, 100L, "hold-1", listOf(1L, 2L), 100000L, "PENDING_PAYMENT", now),
        )
        val paymentPort = SingleSuccessPaymentPort(now)
        val pointPort = InMemoryPointPort(balance = 200000L)
        val useCase = PayReservationUseCase(queuePort, reservationPort, paymentPort, pointPort, seatLoadPort, holdPort, eventPublisher, lockExecutor, transactionTemplate, clock)

        val successCount = AtomicInteger()
        val conflictCount = AtomicInteger()
        runConcurrently(2) { _ ->
            try {
                useCase.execute(1L, "queue-token", 10L, 100000L, "CARD")
                successCount.incrementAndGet()
            } catch (_: ConflictException) {
                conflictCount.incrementAndGet()
            }
        }

        assertEquals(1, successCount.get())
        assertEquals(1, conflictCount.get())
    }

    @Test
    fun `동일 예약에 대한 동시 취소 요청은 최종 상태가 일관되어야 한다`() {
        val now = LocalDateTime.now(clock)
        val queuePort = PassThroughQueuePort()
        val reservationPort = CancelAwareReservationPort(
            ReservationSnapshot(10L, 1L, 1L, 100L, "hold-1", listOf(1L, 2L), 100000L, "CONFIRMED", now),
        )
        val paymentPort = CancelAwarePaymentPort()
        val pointPort = RefundTrackingPointPort()
        val seatLoadPort = InMemorySeatLoadPort(
            scheduleConcertId = 1L,
            seats = mutableMapOf(
                1L to SeatSnapshot(1L, 100L, 50000L, "SOLD"),
                2L to SeatSnapshot(2L, 100L, 50000L, "SOLD"),
            ),
        )
        val useCase = CancelReservationUseCase(queuePort, reservationPort, paymentPort, pointPort, seatLoadPort, lockExecutor, transactionTemplate, clock)

        val successCount = AtomicInteger()
        val conflictCount = AtomicInteger()
        runConcurrently(2) { _ ->
            try {
                useCase.execute(1L, "queue-token", 10L, "CANCELED")
                successCount.incrementAndGet()
            } catch (_: ConflictException) {
                conflictCount.incrementAndGet()
            }
        }

        assertEquals(1, successCount.get())
        assertEquals(1, conflictCount.get())
        assertEquals(1, pointPort.refundCount.get())
    }

    @Test
    fun `동일 hold에 대한 동시 예약 생성 요청은 하나만 성공해야 한다`() {
        val queuePort = PassThroughQueuePort()
        val holdPort = StaticHoldPort(
            HoldSnapshot("hold-1", 1L, 1L, 100L, "queue-token", listOf(1L), 50000L, "ACTIVE", LocalDateTime.now(clock).plusMinutes(5)),
        )
        val reservationPort = SingleHoldReservationPort()
        val useCase = CreateReservationUseCase(queuePort, holdPort, reservationPort, lockExecutor, transactionTemplate, clock)

        val successCount = AtomicInteger()
        val conflictCount = AtomicInteger()
        runConcurrently(2) { _ ->
            try {
                useCase.execute(1L, "queue-token", "hold-1")
                successCount.incrementAndGet()
            } catch (_: ConflictException) {
                conflictCount.incrementAndGet()
            }
        }

        assertEquals(1, successCount.get())
        assertEquals(1, conflictCount.get())
    }

    @Test
    fun `동일 사용자 포인트에 대한 동시 차감은 잔액 음수를 만들지 않아야 한다`() {
        val now = LocalDateTime.now(clock)
        val queuePort = PassThroughQueuePort()
        val holdPort = NoopHoldPort()
        val seatLoadPort = InMemorySeatLoadPort(
            scheduleConcertId = 1L,
            seats = mutableMapOf(
                1L to SeatSnapshot(1L, 100L, 50000L, "HELD"),
                2L to SeatSnapshot(2L, 100L, 50000L, "HELD"),
                3L to SeatSnapshot(3L, 100L, 50000L, "HELD"),
                4L to SeatSnapshot(4L, 100L, 50000L, "HELD"),
            ),
        )
        val reservationPort = MultiReservationPort(
            listOf(
                ReservationSnapshot(10L, 1L, 1L, 100L, "hold-1", listOf(1L, 2L), 100000L, "PENDING_PAYMENT", now),
                ReservationSnapshot(11L, 1L, 1L, 100L, "hold-2", listOf(3L, 4L), 100000L, "PENDING_PAYMENT", now),
            ),
        )
        val paymentPort = MultiReservationPaymentPort(now)
        val pointPort = InMemoryPointPort(balance = 100000L)
        val useCase = PayReservationUseCase(queuePort, reservationPort, paymentPort, pointPort, seatLoadPort, holdPort, eventPublisher, lockExecutor, transactionTemplate, clock)

        val successCount = AtomicInteger()
        val conflictCount = AtomicInteger()
        runConcurrently(2) { index ->
            val reservationId = if (index == 0) 10L else 11L
            try {
                useCase.execute(1L, "queue-token", reservationId, 100000L, "CARD")
                successCount.incrementAndGet()
            } catch (_: ConflictException) {
                conflictCount.incrementAndGet()
            }
        }

        assertEquals(1, successCount.get())
        assertEquals(1, conflictCount.get())
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

    private class PassThroughQueuePort : ReservationQueuePort {
        override fun validateForRead(queueToken: String, userId: Long, concertId: Long?) = Unit
        override fun validateForWrite(queueToken: String, userId: Long, concertId: Long?) = queueToken
        override fun expireAfterPayment(queueToken: String) = Unit
    }

    private class NoopReservationPort : ReservationPort {
        override fun existsByHoldId(holdId: String) = false
        override fun createReservation(userId: Long, scheduleId: Long, holdId: String, totalAmount: Long, seatIds: List<Long>, createdAt: LocalDateTime): ReservationSnapshot {
            throw UnsupportedOperationException()
        }
        override fun getReservation(reservationId: Long): ReservationSnapshot? = null
        override fun getReservationForUpdate(reservationId: Long): ReservationSnapshot? = null
        override fun getReservations(userId: Long, page: Int, size: Int): ReservationListResponse = throw UnsupportedOperationException()
        override fun markReservationConfirmed(reservationId: Long) = Unit
        override fun markReservationCanceled(reservationId: Long, canceledAt: LocalDateTime) = Unit
        override fun markReservationExpiredByHold(holdId: String, expiredAt: LocalDateTime) = Unit
        override fun expirePendingReservations(now: LocalDateTime) = Unit
    }

    private class InMemorySeatLoadPort(
        private val scheduleConcertId: Long,
        private val seats: MutableMap<Long, SeatSnapshot>,
    ) : SeatLoadPort {
        override fun getScheduleConcertId(scheduleId: Long): Long = scheduleConcertId

        @Synchronized
        override fun getSeatsForUpdate(seatIds: List<Long>): List<SeatSnapshot> =
            seatIds.mapNotNull { seats[it] }

        @Synchronized
        override fun markSeatsHeld(seatIds: List<Long>) {
            seatIds.forEach { seatId ->
                val current = seats[seatId] ?: throw NotFoundException("SEAT", seatId)
                if (current.status != "AVAILABLE") throw ConflictException("이미 선점되었거나 판매된 좌석이 포함되어 있습니다.")
                seats[seatId] = current.copy(status = "HELD")
            }
        }

        @Synchronized
        override fun markSeatsSold(seatIds: List<Long>) {
            seatIds.forEach { seatId ->
                val current = seats[seatId] ?: throw NotFoundException("SEAT", seatId)
                if (current.status != "HELD") throw ConflictException("결제 가능한 좌석 상태가 아닙니다.")
                seats[seatId] = current.copy(status = "SOLD")
            }
        }

        @Synchronized
        override fun markSeatsAvailable(seatIds: List<Long>) {
            seatIds.forEach { seatId ->
                val current = seats[seatId] ?: throw NotFoundException("SEAT", seatId)
                seats[seatId] = current.copy(status = "AVAILABLE")
            }
        }
    }

    private class InMemoryHoldPort(private val now: LocalDateTime) : HoldPort {
        override fun expireActiveHolds(now: LocalDateTime) = Unit

        override fun createHold(
            userId: Long,
            scheduleId: Long,
            queueToken: String,
            seatIds: List<Long>,
            totalAmount: Long,
            expiresAt: LocalDateTime,
        ): HoldSnapshot = HoldSnapshot("hold-${seatIds.joinToString("-")}", userId, 1L, scheduleId, queueToken, seatIds, totalAmount, "ACTIVE", now.plusMinutes(5))

        override fun getHold(holdId: String): HoldSnapshot? = null
        override fun getHoldForUpdate(holdId: String): HoldSnapshot? = null
        override fun markHoldConfirmed(holdId: String) = Unit
        override fun markHoldExpired(holdId: String) = Unit
    }

    private class NoopTransactionManager : PlatformTransactionManager {
        override fun getTransaction(definition: TransactionDefinition?): TransactionStatus = SimpleTransactionStatus()
        override fun commit(status: TransactionStatus) = Unit
        override fun rollback(status: TransactionStatus) = Unit
    }

    private class NoopHoldPort : HoldPort {
        override fun expireActiveHolds(now: LocalDateTime) = Unit
        override fun createHold(userId: Long, scheduleId: Long, queueToken: String, seatIds: List<Long>, totalAmount: Long, expiresAt: LocalDateTime) =
            throw UnsupportedOperationException()
        override fun getHold(holdId: String): HoldSnapshot? = null
        override fun getHoldForUpdate(holdId: String): HoldSnapshot? = null
        override fun markHoldConfirmed(holdId: String) = Unit
        override fun markHoldExpired(holdId: String) = Unit
    }

    private class StaticHoldPort(private val hold: HoldSnapshot) : HoldPort {
        override fun expireActiveHolds(now: LocalDateTime) = Unit
        override fun createHold(userId: Long, scheduleId: Long, queueToken: String, seatIds: List<Long>, totalAmount: Long, expiresAt: LocalDateTime) =
            throw UnsupportedOperationException()
        override fun getHold(holdId: String): HoldSnapshot? = hold.takeIf { it.holdId == holdId }
        override fun getHoldForUpdate(holdId: String): HoldSnapshot? = getHold(holdId)
        override fun markHoldConfirmed(holdId: String) = Unit
        override fun markHoldExpired(holdId: String) = Unit
    }

    private class SingleReservationPort(initial: ReservationSnapshot) : ReservationPort {
        private var reservation = initial

        override fun existsByHoldId(holdId: String) = false

        override fun createReservation(userId: Long, scheduleId: Long, holdId: String, totalAmount: Long, seatIds: List<Long>, createdAt: LocalDateTime): ReservationSnapshot {
            throw UnsupportedOperationException()
        }

        @Synchronized
        override fun getReservation(reservationId: Long): ReservationSnapshot? = reservation.takeIf { it.reservationId == reservationId }

        @Synchronized
        override fun getReservationForUpdate(reservationId: Long): ReservationSnapshot? = reservation.takeIf { it.reservationId == reservationId }

        override fun getReservations(userId: Long, page: Int, size: Int): ReservationListResponse = throw UnsupportedOperationException()

        @Synchronized
        override fun markReservationConfirmed(reservationId: Long) {
            if (reservation.status != "PENDING_PAYMENT") throw ConflictException("결제 가능한 예약 상태가 아닙니다.")
            reservation = reservation.copy(status = "CONFIRMED")
        }

        override fun markReservationCanceled(reservationId: Long, canceledAt: LocalDateTime) = Unit
        override fun markReservationExpiredByHold(holdId: String, expiredAt: LocalDateTime) = Unit
        override fun expirePendingReservations(now: LocalDateTime) = Unit
    }

    private class SingleHoldReservationPort : ReservationPort {
        private val created = AtomicBoolean(false)

        override fun existsByHoldId(holdId: String): Boolean = created.get()

        override fun createReservation(
            userId: Long,
            scheduleId: Long,
            holdId: String,
            totalAmount: Long,
            seatIds: List<Long>,
            createdAt: LocalDateTime,
        ): ReservationSnapshot {
            if (!created.compareAndSet(false, true)) throw ConflictException("이미 예약으로 확정된 hold 입니다.")
            return ReservationSnapshot(1L, userId, 1L, scheduleId, holdId, seatIds, totalAmount, "PENDING_PAYMENT", createdAt)
        }

        override fun getReservation(reservationId: Long): ReservationSnapshot? = null
        override fun getReservationForUpdate(reservationId: Long): ReservationSnapshot? = null
        override fun getReservations(userId: Long, page: Int, size: Int): ReservationListResponse = throw UnsupportedOperationException()
        override fun markReservationConfirmed(reservationId: Long) = Unit
        override fun markReservationCanceled(reservationId: Long, canceledAt: LocalDateTime) = Unit
        override fun markReservationExpiredByHold(holdId: String, expiredAt: LocalDateTime) = Unit
        override fun expirePendingReservations(now: LocalDateTime) = Unit
    }

    private class SingleSuccessPaymentPort(private val now: LocalDateTime) : PaymentPort {
        private val success = AtomicBoolean(false)

        override fun hasSuccessfulPayment(reservationId: Long): Boolean = success.get()

        override fun createSuccessPayment(reservationId: Long, amount: Long, method: String, paidAt: LocalDateTime): PaymentSnapshot {
            if (!success.compareAndSet(false, true)) throw ConflictException("이미 결제 완료된 예약입니다.")
            return PaymentSnapshot(1L, reservationId, amount, "SUCCESS", now)
        }

        override fun createFailedPayment(reservationId: Long, amount: Long, method: String, failedAt: LocalDateTime, reason: String): PaymentSnapshot =
            PaymentSnapshot(2L, reservationId, amount, "FAILED", now)

        override fun cancelSuccessPayment(reservationId: Long, canceledAt: LocalDateTime): Boolean = false
    }

    private class InMemoryPointPort(balance: Long) : PointPort {
        private val currentBalance = AtomicInteger(balance.toInt())

        override fun deduct(userId: Long, amount: Long, now: LocalDateTime) {
            synchronized(this) {
                if (currentBalance.get() < amount) throw ConflictException("포인트가 부족합니다.")
                currentBalance.addAndGet(-amount.toInt())
            }
        }

        override fun refund(userId: Long, amount: Long, now: LocalDateTime) {
            currentBalance.addAndGet(amount.toInt())
        }
    }

    private class MultiReservationPort(initialReservations: List<ReservationSnapshot>) : ReservationPort {
        private val reservations = initialReservations.associateBy { it.reservationId }.toMutableMap()

        override fun existsByHoldId(holdId: String): Boolean = false
        override fun createReservation(userId: Long, scheduleId: Long, holdId: String, totalAmount: Long, seatIds: List<Long>, createdAt: LocalDateTime) =
            throw UnsupportedOperationException()

        @Synchronized
        override fun getReservation(reservationId: Long): ReservationSnapshot? = reservations[reservationId]

        @Synchronized
        override fun getReservationForUpdate(reservationId: Long): ReservationSnapshot? = reservations[reservationId]

        override fun getReservations(userId: Long, page: Int, size: Int): ReservationListResponse = throw UnsupportedOperationException()

        @Synchronized
        override fun markReservationConfirmed(reservationId: Long) {
            val reservation = reservations[reservationId] ?: throw NotFoundException("RESERVATION", reservationId)
            if (reservation.status != "PENDING_PAYMENT") throw ConflictException("결제 가능한 예약 상태가 아닙니다.")
            reservations[reservationId] = reservation.copy(status = "CONFIRMED")
        }

        override fun markReservationCanceled(reservationId: Long, canceledAt: LocalDateTime) = Unit
        override fun markReservationExpiredByHold(holdId: String, expiredAt: LocalDateTime) = Unit
        override fun expirePendingReservations(now: LocalDateTime) = Unit
    }

    private class MultiReservationPaymentPort(private val now: LocalDateTime) : PaymentPort {
        private val successReservations = mutableSetOf<Long>()

        @Synchronized
        override fun hasSuccessfulPayment(reservationId: Long): Boolean = successReservations.contains(reservationId)

        @Synchronized
        override fun createSuccessPayment(reservationId: Long, amount: Long, method: String, paidAt: LocalDateTime): PaymentSnapshot {
            if (!successReservations.add(reservationId)) throw ConflictException("이미 결제 완료된 예약입니다.")
            return PaymentSnapshot(reservationId, reservationId, amount, "SUCCESS", now)
        }

        override fun createFailedPayment(reservationId: Long, amount: Long, method: String, failedAt: LocalDateTime, reason: String): PaymentSnapshot =
            PaymentSnapshot(1000L + reservationId, reservationId, amount, "FAILED", now)

        override fun cancelSuccessPayment(reservationId: Long, canceledAt: LocalDateTime): Boolean = false
    }

    private class CancelAwareReservationPort(initial: ReservationSnapshot) : ReservationPort {
        private var reservation = initial

        override fun existsByHoldId(holdId: String) = false
        override fun createReservation(userId: Long, scheduleId: Long, holdId: String, totalAmount: Long, seatIds: List<Long>, createdAt: LocalDateTime) =
            throw UnsupportedOperationException()

        @Synchronized
        override fun getReservation(reservationId: Long): ReservationSnapshot? = reservation.takeIf { it.reservationId == reservationId }

        @Synchronized
        override fun getReservationForUpdate(reservationId: Long): ReservationSnapshot? = reservation.takeIf { it.reservationId == reservationId }

        override fun getReservations(userId: Long, page: Int, size: Int): ReservationListResponse = throw UnsupportedOperationException()
        override fun markReservationConfirmed(reservationId: Long) = Unit

        @Synchronized
        override fun markReservationCanceled(reservationId: Long, canceledAt: LocalDateTime) {
            if (reservation.status == "CANCELED") throw ConflictException("이미 취소된 예약입니다.")
            reservation = reservation.copy(status = "CANCELED")
        }

        override fun markReservationExpiredByHold(holdId: String, expiredAt: LocalDateTime) = Unit
        override fun expirePendingReservations(now: LocalDateTime) = Unit
    }

    private class CancelAwarePaymentPort : PaymentPort {
        private val canceled = AtomicBoolean(false)

        override fun hasSuccessfulPayment(reservationId: Long): Boolean = true
        override fun createSuccessPayment(reservationId: Long, amount: Long, method: String, paidAt: LocalDateTime) =
            throw UnsupportedOperationException()

        override fun createFailedPayment(reservationId: Long, amount: Long, method: String, failedAt: LocalDateTime, reason: String) =
            throw UnsupportedOperationException()

        override fun cancelSuccessPayment(reservationId: Long, canceledAt: LocalDateTime): Boolean =
            canceled.compareAndSet(false, true)
    }

    private class RefundTrackingPointPort : PointPort {
        val refundCount = AtomicInteger()

        override fun deduct(userId: Long, amount: Long, now: LocalDateTime) = Unit

        override fun refund(userId: Long, amount: Long, now: LocalDateTime) {
            refundCount.incrementAndGet()
        }
    }
}
