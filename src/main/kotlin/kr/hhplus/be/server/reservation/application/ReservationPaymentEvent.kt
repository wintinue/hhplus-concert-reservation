package kr.hhplus.be.server.reservation.application

import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.LocalDateTime

data class ReservationPaymentCompletedEvent(
    val reservationId: Long = 0L,
    val userId: Long = 0L,
    val concertId: Long = 0L,
    val scheduleId: Long = 0L,
    val seatIds: List<Long> = emptyList(),
    val paymentId: Long = 0L,
    val amount: Long = 0L,
    val method: String = "",
    val paidAt: LocalDateTime = LocalDateTime.MIN,
)

data class ReservationPaymentPayload(
    val reservationId: Long = 0L,
    val userId: Long = 0L,
    val concertId: Long = 0L,
    val scheduleId: Long = 0L,
    val seatIds: List<Long> = emptyList(),
    val paymentId: Long = 0L,
    val amount: Long = 0L,
    val method: String = "",
    val paidAt: LocalDateTime = LocalDateTime.MIN,
)

interface ReservationDataPlatformPort {
    fun sendReservationPayment(payload: ReservationPaymentPayload)
}

@Component
class ReservationPaymentEventPublisher(
    private val reservationPaymentSagaService: ReservationPaymentSagaService,
    private val reservationOutboxRelay: ReservationOutboxRelay,
) {
    fun completed(event: ReservationPaymentCompletedEvent) {
        val eventKey = reservationPaymentSagaService.start(event)
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun afterCommit() {
                        reservationOutboxRelay.publishByEventKey(eventKey)
                    }
                },
            )
            return
        }
        reservationOutboxRelay.publishByEventKey(eventKey)
    }
}

class ReservationPaymentEventListener
