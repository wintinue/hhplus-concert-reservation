package kr.hhplus.be.server

import kr.hhplus.be.server.reservation.application.HoldPort
import kr.hhplus.be.server.reservation.application.ReservationCleanupScheduler
import kr.hhplus.be.server.reservation.application.ReservationPort
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class ReservationCleanupSchedulerTest {
    private val clock = Clock.fixed(Instant.parse("2026-03-01T00:00:00Z"), ZoneOffset.UTC)
    private val holdPort = mockk<HoldPort>(relaxed = true)
    private val reservationPort = mockk<ReservationPort>(relaxed = true)
    private val scheduler = ReservationCleanupScheduler(holdPort, reservationPort, clock)

    @Test
    fun `cleanupExpiredResources는 만료된 hold와 reservation 정리를 호출한다`() {
        scheduler.cleanupExpiredResources()

        verify { holdPort.expireActiveHolds(LocalDateTime.now(clock)) }
        verify { reservationPort.expirePendingReservations(LocalDateTime.now(clock)) }
    }
}
