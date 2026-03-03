package kr.hhplus.be.server

import io.mockk.mockk
import io.mockk.verify
import kr.hhplus.be.server.reservation.application.HoldPort
import kr.hhplus.be.server.reservation.application.ReservationExpirationCoordinator
import kr.hhplus.be.server.reservation.application.ReservationPort
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class ReservationExpirationCoordinatorTest {
    private val holdPort = mockk<HoldPort>(relaxed = true)
    private val reservationPort = mockk<ReservationPort>(relaxed = true)
    private val clock = MutableClock(Instant.parse("2026-03-01T00:00:00Z"), ZoneOffset.UTC)
    private val coordinator = ReservationExpirationCoordinator(holdPort, reservationPort, clock, 5_000L)

    @Test
    fun `cleanupIfDue는 짧은 시간 내 중복 정리 호출을 생략한다`() {
        coordinator.cleanupIfDue()
        coordinator.cleanupIfDue()

        verify(exactly = 1) { holdPort.expireActiveHolds(LocalDateTime.now(clock)) }
        verify(exactly = 1) { reservationPort.expirePendingReservations(LocalDateTime.now(clock)) }
    }

    @Test
    fun `cleanupIfDue는 최소 간격이 지나면 다시 정리한다`() {
        coordinator.cleanupIfDue()
        clock.advanceMillis(5_001L)

        coordinator.cleanupIfDue()

        verify(exactly = 2) { holdPort.expireActiveHolds(any()) }
        verify(exactly = 2) { reservationPort.expirePendingReservations(any()) }
    }
}

private class MutableClock(
    private var currentInstant: Instant,
    private val zoneId: ZoneId,
) : Clock() {
    override fun getZone(): ZoneId = zoneId

    override fun withZone(zone: ZoneId): Clock = MutableClock(currentInstant, zone)

    override fun instant(): Instant = currentInstant

    fun advanceMillis(millis: Long) {
        currentInstant = currentInstant.plusMillis(millis)
    }
}
