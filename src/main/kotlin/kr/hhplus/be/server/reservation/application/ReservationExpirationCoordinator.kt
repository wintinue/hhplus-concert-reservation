package kr.hhplus.be.server.reservation.application

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicLong

@Component
class ReservationExpirationCoordinator(
    private val holdPort: HoldPort,
    private val reservationPort: ReservationPort,
    private val clock: Clock,
    @Value("\${reservation.cleanup.min-interval-ms:5000}")
    private val minIntervalMs: Long,
) {
    private val lastCleanupAt = AtomicLong(0L)

    @Transactional
    fun cleanupIfDue() {
        val nowMillis = clock.millis()
        val lastRun = lastCleanupAt.get()
        if (nowMillis - lastRun < minIntervalMs) {
            return
        }
        if (!lastCleanupAt.compareAndSet(lastRun, nowMillis)) {
            return
        }
        cleanupExpiredResources()
    }

    @Transactional
    fun cleanupExpiredResources() {
        val now = LocalDateTime.now(clock)
        holdPort.expireActiveHolds(now)
        reservationPort.expirePendingReservations(now)
    }
}
