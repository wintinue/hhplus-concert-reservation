package kr.hhplus.be.server.reservation.application

import com.fasterxml.jackson.databind.ObjectMapper
import kr.hhplus.be.server.domain.entity.BookingSagaEntity
import kr.hhplus.be.server.domain.entity.OutboxEventEntity
import kr.hhplus.be.server.domain.enums.BookingSagaStatus
import kr.hhplus.be.server.domain.enums.OutboxEventStatus
import kr.hhplus.be.server.domain.repository.BookingSagaRepository
import kr.hhplus.be.server.domain.repository.OutboxEventRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

@Service
class ReservationPaymentSagaService(
    private val bookingSagaRepository: BookingSagaRepository,
    private val outboxEventRepository: OutboxEventRepository,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) {
    @Transactional
    fun start(event: ReservationPaymentCompletedEvent): String {
        val existing = bookingSagaRepository.findByReservationId(event.reservationId)
        if (existing != null) {
            return outboxEventRepository.findTop100ByEventStatusInOrderByCreatedAtAsc(listOf(OutboxEventStatus.PENDING, OutboxEventStatus.FAILED))
                .firstOrNull { it.sagaId == existing.sagaId && it.aggregateId == event.reservationId }
                ?.eventKey
                ?: existing.sagaId
        }

        val now = LocalDateTime.now(clock)
        val sagaId = "booking-saga:${event.reservationId}:${UUID.randomUUID()}"
        bookingSagaRepository.save(
            BookingSagaEntity(
                sagaId = sagaId,
                reservationId = event.reservationId,
                sagaType = "RESERVATION_PAYMENT_COMPLETION",
                sagaStatus = BookingSagaStatus.STARTED,
                currentStep = "OUTBOX_STORED",
                startedAt = now,
            ),
        )
        val outbox = outboxEventRepository.save(
            OutboxEventEntity(
                eventKey = "reservation-payment:${event.reservationId}:${UUID.randomUUID()}",
                sagaId = sagaId,
                aggregateType = "RESERVATION",
                aggregateId = event.reservationId,
                eventType = "ReservationPaymentCompleted",
                payload = objectMapper.writeValueAsString(
                    ReservationPaymentPayload(
                        reservationId = event.reservationId,
                        userId = event.userId,
                        concertId = event.concertId,
                        scheduleId = event.scheduleId,
                        seatIds = event.seatIds,
                        paymentId = event.paymentId,
                        amount = event.amount,
                        method = event.method,
                        paidAt = event.paidAt,
                    ),
                ),
                eventStatus = OutboxEventStatus.PENDING,
            ),
        )
        return outbox.eventKey
    }

    @Transactional
    fun markPublished(sagaId: String) {
        val saga = bookingSagaRepository.findBySagaId(sagaId) ?: return
        saga.currentStep = "DATA_PLATFORM_SENT"
        saga.sagaStatus = BookingSagaStatus.COMPLETED
        saga.completedAt = LocalDateTime.now(clock)
        saga.failedAt = null
        saga.failureReason = null
    }

    @Transactional
    fun markFailed(sagaId: String, reason: String) {
        val saga = bookingSagaRepository.findBySagaId(sagaId) ?: return
        saga.currentStep = "DATA_PLATFORM_SEND_FAILED"
        saga.sagaStatus = BookingSagaStatus.FAILED
        saga.failedAt = LocalDateTime.now(clock)
        saga.failureReason = reason
    }
}

@Component
class ReservationPaymentSagaListener(
    private val reservationPaymentSagaService: ReservationPaymentSagaService,
    private val reservationOutboxRelay: ReservationOutboxRelay,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onCompleted(event: ReservationPaymentCompletedEvent) {
        val eventKey = reservationPaymentSagaService.start(event)
        reservationOutboxRelay.publishByEventKey(eventKey)
    }
}

@Component
class ReservationOutboxRelay(
    private val outboxEventRepository: OutboxEventRepository,
    private val reservationDataPlatformPort: ReservationDataPlatformPort,
    private val reservationPaymentSagaService: ReservationPaymentSagaService,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
    @Value("\${app.outbox.max-retry-count:3}")
    private val maxRetryCount: Int,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 5000)
    fun publishPending() {
        outboxEventRepository.findTop100ByEventStatusInOrderByCreatedAtAsc(listOf(OutboxEventStatus.PENDING, OutboxEventStatus.FAILED))
            .filter { it.retryCount < maxRetryCount }
            .forEach { publish(it) }
    }

    fun publishByEventKey(eventKey: String) {
        outboxEventRepository.findByEventKey(eventKey)
            ?.takeIf { it.retryCount < maxRetryCount }
            ?.let(::publish)
    }

    @Transactional
    fun publish(outboxEvent: OutboxEventEntity) {
        try {
            val payload = objectMapper.readValue(outboxEvent.payload, ReservationPaymentPayload::class.java)
            reservationDataPlatformPort.sendReservationPayment(payload)
            outboxEvent.eventStatus = OutboxEventStatus.PUBLISHED
            outboxEvent.publishedAt = LocalDateTime.now(clock)
            outboxEvent.lastError = null
            outboxEventRepository.save(outboxEvent)
            reservationPaymentSagaService.markPublished(outboxEvent.sagaId)
        } catch (ex: RuntimeException) {
            outboxEvent.eventStatus = OutboxEventStatus.FAILED
            outboxEvent.retryCount += 1
            outboxEvent.lastError = ex.message ?: ex.javaClass.simpleName
            outboxEventRepository.save(outboxEvent)
            reservationPaymentSagaService.markFailed(outboxEvent.sagaId, outboxEvent.lastError!!)
            logger.warn("failed to publish outbox event. eventKey={}, retryCount={}", outboxEvent.eventKey, outboxEvent.retryCount)
            throw ex
        }
    }
}
