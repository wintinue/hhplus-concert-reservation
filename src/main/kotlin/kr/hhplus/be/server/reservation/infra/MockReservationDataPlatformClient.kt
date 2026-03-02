package kr.hhplus.be.server.reservation.infra

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.CopyOnWriteArrayList
import kr.hhplus.be.server.api.MockReservationPaymentRequest

@Component
class MockReservationDataPlatformClient {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val sentPayloads = CopyOnWriteArrayList<MockReservationPaymentRequest>()

    fun record(payload: MockReservationPaymentRequest) {
        sentPayloads += payload
        logger.info(
            "mock data platform accepted reservation payment. reservationId={}, paymentId={}, amount={}",
            payload.reservationId,
            payload.paymentId,
            payload.amount,
        )
    }

    fun getSentPayloads(): List<MockReservationPaymentRequest> = sentPayloads.toList()

    fun clear() {
        sentPayloads.clear()
    }
}
