package kr.hhplus.be.server

import kr.hhplus.be.server.reservation.application.ReservationCleanupScheduler
import kr.hhplus.be.server.reservation.application.ReservationExpirationCoordinator
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class ReservationCleanupSchedulerTest {
    private val reservationExpirationCoordinator = mockk<ReservationExpirationCoordinator>(relaxed = true)
    private val scheduler = ReservationCleanupScheduler(reservationExpirationCoordinator)

    @Test
    fun `cleanupExpiredResources는 만료된 hold와 reservation 정리를 호출한다`() {
        scheduler.cleanupExpiredResources()

        verify { reservationExpirationCoordinator.cleanupExpiredResources() }
    }
}
