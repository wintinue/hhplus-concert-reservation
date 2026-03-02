package kr.hhplus.be.server

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kr.hhplus.be.server.domain.entity.OutboxEventEntity
import kr.hhplus.be.server.domain.enums.OutboxEventStatus
import kr.hhplus.be.server.domain.repository.OutboxEventRepository
import kr.hhplus.be.server.reservation.application.ReservationOutboxRelay
import kr.hhplus.be.server.reservation.application.ReservationPaymentKafkaMessage
import kr.hhplus.be.server.reservation.application.ReservationPaymentMessagePublisher
import kr.hhplus.be.server.reservation.application.ReservationPaymentPayload
import kr.hhplus.be.server.reservation.application.ReservationPaymentPublishResult
import kr.hhplus.be.server.reservation.application.ReservationOutboxStateService
import kr.hhplus.be.server.reservation.application.ReservationPaymentSagaService
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
        val messagePublisher = mockk<ReservationPaymentMessagePublisher>(relaxed = true)
        val sagaService = mockk<ReservationPaymentSagaService>(relaxed = true)
        val outboxStateService = mockk<ReservationOutboxStateService>()
        val relay = ReservationOutboxRelay(
            reservationPaymentMessagePublisher = messagePublisher,
            reservationPaymentSagaService = sagaService,
            reservationOutboxStateService = outboxStateService,
            outboxEventRepository = outboxEventRepository,
            objectMapper = objectMapper,
            maxRetryCount = 3,
        )

        every { outboxStateService.claimForPublish("event-key", 3) } returns null

        relay.publishByEventKey("event-key")

        verify(exactly = 0) { messagePublisher.publish(any()) }
    }

    @Test
    fun `발행 실패 시 retry count를 증가시키고 saga를 failed로 표시한다`() {
        val outboxEventRepository = mockk<OutboxEventRepository>()
        val messagePublisher = mockk<ReservationPaymentMessagePublisher>()
        val sagaService = mockk<ReservationPaymentSagaService>()
        val outboxStateService = mockk<ReservationOutboxStateService>()
        val relay = ReservationOutboxRelay(
            reservationPaymentMessagePublisher = messagePublisher,
            reservationPaymentSagaService = sagaService,
            reservationOutboxStateService = outboxStateService,
            outboxEventRepository = outboxEventRepository,
            objectMapper = objectMapper,
            maxRetryCount = 3,
        )
        val outboxEvent = outboxEvent(retryCount = 1)

        every { messagePublisher.publish(any()) } throws IllegalStateException("mock api down")
        every { outboxStateService.claimForPublish("event-key", 3) } returns outboxEvent.apply { eventStatus = OutboxEventStatus.PROCESSING }
        every { outboxStateService.markFailed("event-key", "mock api down") } returns 2
        every { sagaService.markFailed("saga-1", "mock api down") } just runs

        assertThrows<IllegalStateException> {
            relay.publish(outboxEvent)
        }

        verify { outboxStateService.markFailed("event-key", "mock api down") }
        verify { sagaService.markFailed("saga-1", "mock api down") }
    }

    @Test
    fun `kafka 발행 성공 시 outbox를 published 처리하고 saga를 dispatched 상태로 변경한다`() {
        val outboxEventRepository = mockk<OutboxEventRepository>()
        val messagePublisher = mockk<ReservationPaymentMessagePublisher>()
        val sagaService = mockk<ReservationPaymentSagaService>()
        val outboxStateService = mockk<ReservationOutboxStateService>()
        val relay = ReservationOutboxRelay(
            reservationPaymentMessagePublisher = messagePublisher,
            reservationPaymentSagaService = sagaService,
            reservationOutboxStateService = outboxStateService,
            outboxEventRepository = outboxEventRepository,
            objectMapper = objectMapper,
            maxRetryCount = 3,
        )
        val outboxEvent = outboxEvent(retryCount = 0).apply { eventStatus = OutboxEventStatus.PENDING }

        every { messagePublisher.publish(any()) } returns ReservationPaymentPublishResult.DISPATCHED
        every { outboxStateService.claimForPublish("event-key", 3) } returns outboxEvent.apply { eventStatus = OutboxEventStatus.PROCESSING }
        every { outboxStateService.markPublished("event-key") } returns Unit
        every { sagaService.markDispatched("saga-1") } just runs

        relay.publish(outboxEvent)

        verify {
            messagePublisher.publish(
                match<ReservationPaymentKafkaMessage> {
                    it.eventKey == "event-key" && it.sagaId == "saga-1" && it.reservationId == 10L
                },
            )
        }
        verify { outboxStateService.markPublished("event-key") }
        verify { sagaService.markDispatched("saga-1") }
    }

    @Test
    fun `이미 선점된 outbox 이벤트는 중복 발행하지 않는다`() {
        val outboxEventRepository = mockk<OutboxEventRepository>()
        val messagePublisher = mockk<ReservationPaymentMessagePublisher>(relaxed = true)
        val sagaService = mockk<ReservationPaymentSagaService>(relaxed = true)
        val outboxStateService = mockk<ReservationOutboxStateService>()
        val relay = ReservationOutboxRelay(
            reservationPaymentMessagePublisher = messagePublisher,
            reservationPaymentSagaService = sagaService,
            reservationOutboxStateService = outboxStateService,
            outboxEventRepository = outboxEventRepository,
            objectMapper = objectMapper,
            maxRetryCount = 3,
        )

        every { outboxStateService.claimForPublish("event-key", 3) } returns null

        relay.publishByEventKey("event-key")

        verify(exactly = 1) { outboxStateService.claimForPublish("event-key", 3) }
        verify(exactly = 0) { messagePublisher.publish(any()) }
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
