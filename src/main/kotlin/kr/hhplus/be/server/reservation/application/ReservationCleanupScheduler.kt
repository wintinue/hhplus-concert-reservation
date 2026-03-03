package kr.hhplus.be.server.reservation.application

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ReservationCleanupScheduler(
    private val reservationExpirationCoordinator: ReservationExpirationCoordinator,
) {
    @Transactional
    @Scheduled(fixedDelayString = "\${reservation.cleanup.fixed-delay-ms:30000}")
    fun cleanupExpiredResources() {
        reservationExpirationCoordinator.cleanupExpiredResources()
    }
}
