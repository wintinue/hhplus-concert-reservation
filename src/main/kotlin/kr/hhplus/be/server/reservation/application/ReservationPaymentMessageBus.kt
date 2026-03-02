package kr.hhplus.be.server.reservation.application

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

data class ReservationPaymentKafkaMessage(
    val eventKey: String = "",
    val sagaId: String = "",
    val reservationId: Long = 0L,
    val payload: ReservationPaymentPayload = ReservationPaymentPayload(),
)

enum class ReservationPaymentPublishResult {
    DISPATCHED,
    COMPLETED,
}

interface ReservationPaymentMessagePublisher {
    fun publish(message: ReservationPaymentKafkaMessage): ReservationPaymentPublishResult
}

@Component
@ConditionalOnProperty(prefix = "app.kafka", name = ["enabled"], havingValue = "false", matchIfMissing = true)
class DirectReservationPaymentMessagePublisher(
    private val reservationDataPlatformPort: ReservationDataPlatformPort,
) : ReservationPaymentMessagePublisher {
    override fun publish(message: ReservationPaymentKafkaMessage): ReservationPaymentPublishResult {
        reservationDataPlatformPort.sendReservationPayment(message.payload)
        return ReservationPaymentPublishResult.COMPLETED
    }
}

@Component
@ConditionalOnProperty(prefix = "app.kafka", name = ["enabled"], havingValue = "true")
class KafkaReservationPaymentMessagePublisher(
    private val kafkaTemplate: KafkaTemplate<String, ReservationPaymentKafkaMessage>,
    @Value("\${app.kafka.topic.reservation-payment}")
    private val topicName: String,
) : ReservationPaymentMessagePublisher {
    override fun publish(message: ReservationPaymentKafkaMessage): ReservationPaymentPublishResult {
        kafkaTemplate.send(topicName, message.reservationId.toString(), message).get()
        return ReservationPaymentPublishResult.DISPATCHED
    }
}

@Component
@ConditionalOnProperty(prefix = "app.kafka", name = ["enabled"], havingValue = "true")
class ReservationPaymentKafkaConsumer(
    private val reservationDataPlatformPort: ReservationDataPlatformPort,
    private val reservationPaymentSagaService: ReservationPaymentSagaService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = ["\${app.kafka.topic.reservation-payment}"],
        containerFactory = "reservationPaymentKafkaListenerContainerFactory",
    )
    fun consume(
        message: ReservationPaymentKafkaMessage,
        acknowledgment: Acknowledgment,
    ) {
        try {
            reservationDataPlatformPort.sendReservationPayment(message.payload)
            reservationPaymentSagaService.markCompleted(message.sagaId)
            acknowledgment.acknowledge()
        } catch (ex: RuntimeException) {
            reservationPaymentSagaService.markFailed(message.sagaId, ex.message ?: ex.javaClass.simpleName)
            logger.warn("failed to consume reservation payment kafka message. eventKey={}", message.eventKey)
            throw ex
        }
    }
}
