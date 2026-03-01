package kr.hhplus.be.server.reservation.application

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Component
class ReservationCleanupScheduler(
    private val holdPort: HoldPort,
    private val reservationPort: ReservationPort,
    private val clock: Clock,
) {
    @Transactional
    @Scheduled(fixedDelayString = "\${reservation.cleanup.fixed-delay-ms:30000}")
    fun cleanupExpiredResources() {
        val now = LocalDateTime.now(clock)
        holdPort.expireActiveHolds(now)
        reservationPort.expirePendingReservations(now)
    }
}
