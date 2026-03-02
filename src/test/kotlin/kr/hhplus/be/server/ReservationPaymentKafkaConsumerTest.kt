package kr.hhplus.be.server

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kr.hhplus.be.server.reservation.application.ReservationDataPlatformPort
import kr.hhplus.be.server.reservation.application.ReservationPaymentKafkaConsumer
import kr.hhplus.be.server.reservation.application.ReservationPaymentKafkaMessage
import kr.hhplus.be.server.reservation.application.ReservationPaymentPayload
import kr.hhplus.be.server.reservation.application.ReservationPaymentSagaService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.kafka.support.Acknowledgment
import java.time.LocalDateTime

class ReservationPaymentKafkaConsumerTest {
    @Test
    fun `consumer는 데이터 플랫폼 전송 성공 시 saga를 완료 처리하고 offset을 ack 한다`() {
        val dataPlatformPort = mockk<ReservationDataPlatformPort>()
        val sagaService = mockk<ReservationPaymentSagaService>()
        val acknowledgment = mockk<Acknowledgment>()
        val consumer = ReservationPaymentKafkaConsumer(dataPlatformPort, sagaService)

        every { sagaService.isCompleted("saga-1") } returns false
        every { dataPlatformPort.sendReservationPayment(any()) } just runs
        every { sagaService.markCompleted("saga-1") } just runs
        every { acknowledgment.acknowledge() } just runs

        consumer.consume(message(), acknowledgment)

        verify { dataPlatformPort.sendReservationPayment(any()) }
        verify { sagaService.markCompleted("saga-1") }
        verify { acknowledgment.acknowledge() }
    }

    @Test
    fun `consumer는 이미 완료된 saga면 데이터 플랫폼에 중복 전송하지 않고 ack 한다`() {
        val dataPlatformPort = mockk<ReservationDataPlatformPort>()
        val sagaService = mockk<ReservationPaymentSagaService>()
        val acknowledgment = mockk<Acknowledgment>()
        val consumer = ReservationPaymentKafkaConsumer(dataPlatformPort, sagaService)

        every { sagaService.isCompleted("saga-1") } returns true
        every { acknowledgment.acknowledge() } just runs

        consumer.consume(message(), acknowledgment)

        verify(exactly = 0) { dataPlatformPort.sendReservationPayment(any()) }
        verify(exactly = 0) { sagaService.markCompleted(any()) }
        verify { acknowledgment.acknowledge() }
    }

    @Test
    fun `consumer는 데이터 플랫폼 전송 실패 시 saga를 failed 처리하고 예외를 다시 던진다`() {
        val dataPlatformPort = mockk<ReservationDataPlatformPort>()
        val sagaService = mockk<ReservationPaymentSagaService>()
        val acknowledgment = mockk<Acknowledgment>(relaxed = true)
        val consumer = ReservationPaymentKafkaConsumer(dataPlatformPort, sagaService)

        every { sagaService.isCompleted("saga-1") } returns false
        every { dataPlatformPort.sendReservationPayment(any()) } throws IllegalStateException("downstream timeout")
        every { sagaService.markFailed("saga-1", "downstream timeout") } just runs

        assertThrows<IllegalStateException> {
            consumer.consume(message(), acknowledgment)
        }

        verify { sagaService.markFailed("saga-1", "downstream timeout") }
        verify(exactly = 0) { acknowledgment.acknowledge() }
    }

    private fun message() = ReservationPaymentKafkaMessage(
        eventKey = "event-key",
        sagaId = "saga-1",
        reservationId = 10L,
        payload = ReservationPaymentPayload(
            reservationId = 10L,
            userId = 1L,
            concertId = 1L,
            scheduleId = 101L,
            seatIds = listOf(1L, 2L),
            paymentId = 33L,
            amount = 120000L,
            method = "CARD",
            paidAt = LocalDateTime.parse("2026-03-01T10:15:30"),
        ),
    )
}
