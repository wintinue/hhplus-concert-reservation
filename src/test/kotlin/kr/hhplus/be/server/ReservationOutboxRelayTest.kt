package kr.hhplus.be.server

import com.fasterxml.jackson.databind.ObjectMapper
import kr.hhplus.be.server.domain.entity.OutboxEventEntity
import kr.hhplus.be.server.domain.enums.OutboxEventStatus
import kr.hhplus.be.server.domain.repository.OutboxEventRepository
import kr.hhplus.be.server.reservation.application.ReservationDataPlatformPort
import kr.hhplus.be.server.reservation.application.ReservationOutboxRelay
import kr.hhplus.be.server.reservation.application.ReservationPaymentPayload
import kr.hhplus.be.server.reservation.application.ReservationPaymentSagaService
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class ReservationOutboxRelayTest {
    private val objectMapper = ObjectMapper().findAndRegisterModules()
    private val clock = Clock.fixed(Instant.parse("2026-03-01T00:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `최대 재시도 횟수에 도달한 outbox 이벤트는 즉시 발행 대상에서 제외한다`() {
        val outboxEventRepository = mockk<OutboxEventRepository>()
        val dataPlatformPort = mockk<ReservationDataPlatformPort>(relaxed = true)
        val sagaService = mockk<ReservationPaymentSagaService>(relaxed = true)
        val relay = ReservationOutboxRelay(
            outboxEventRepository = outboxEventRepository,
            reservationDataPlatformPort = dataPlatformPort,
            reservationPaymentSagaService = sagaService,
            objectMapper = objectMapper,
            clock = clock,
            maxRetryCount = 3,
        )

        every { outboxEventRepository.findByEventKey("event-key") } returns outboxEvent(retryCount = 3)

        relay.publishByEventKey("event-key")

        verify(exactly = 0) { dataPlatformPort.sendReservationPayment(any()) }
    }

    @Test
    fun `발행 실패 시 retry count를 증가시키고 saga를 failed로 표시한다`() {
        val outboxEventRepository = mockk<OutboxEventRepository>()
        val dataPlatformPort = mockk<ReservationDataPlatformPort>()
        val sagaService = mockk<ReservationPaymentSagaService>()
        val relay = ReservationOutboxRelay(
            outboxEventRepository = outboxEventRepository,
            reservationDataPlatformPort = dataPlatformPort,
            reservationPaymentSagaService = sagaService,
            objectMapper = objectMapper,
            clock = clock,
            maxRetryCount = 3,
        )
        val outboxEvent = outboxEvent(retryCount = 1)

        every { dataPlatformPort.sendReservationPayment(any()) } throws IllegalStateException("mock api down")
        every { outboxEventRepository.save(any()) } answers { firstArg() }
        every { sagaService.markFailed("saga-1", "mock api down") } just runs

        assertThrows<IllegalStateException> {
            relay.publish(outboxEvent)
        }

        verify { outboxEventRepository.save(match { it.retryCount == 2 && it.eventStatus == OutboxEventStatus.FAILED }) }
        verify { sagaService.markFailed("saga-1", "mock api down") }
    }

    private fun outboxEvent(retryCount: Int): OutboxEventEntity =
        OutboxEventEntity(
            eventKey = "event-key",
            sagaId = "saga-1",
            aggregateType = "RESERVATION",
            aggregateId = 10L,
            eventType = "ReservationPaymentCompleted",
            payload = objectMapper.writeValueAsString(
                ReservationPaymentPayload(
                    reservationId = 10L,
                    userId = 1L,
                    concertId = 1L,
                    scheduleId = 100L,
                    seatIds = listOf(1L, 2L),
                    paymentId = 99L,
                    amount = 100000L,
                    method = "CARD",
                    paidAt = LocalDateTime.now(clock),
                ),
            ),
            eventStatus = OutboxEventStatus.FAILED,
            retryCount = retryCount,
        )
}
