package kr.hhplus.be.server.reservation.infra

import kr.hhplus.be.server.api.MockReservationPaymentRequest
import kr.hhplus.be.server.reservation.application.ReservationDataPlatformPort
import kr.hhplus.be.server.reservation.application.ReservationPaymentPayload
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class ReservationDataPlatformHttpClient(
    private val restClientBuilder: RestClient.Builder,
    private val environment: Environment,
) : ReservationDataPlatformPort {
    override fun sendReservationPayment(payload: ReservationPaymentPayload) {
        restClientBuilder.build()
            .post()
            .uri("${resolveBaseUrl()}/api/mock/data-platform/reservations/payments")
            .body(
                MockReservationPaymentRequest(
                    reservationId = payload.reservationId,
                    userId = payload.userId,
                    concertId = payload.concertId,
                    scheduleId = payload.scheduleId,
                    seatIds = payload.seatIds,
                    paymentId = payload.paymentId,
                    amount = payload.amount,
                    method = payload.method,
                    paidAt = payload.paidAt,
                ),
            )
            .retrieve()
            .toBodilessEntity()
    }

    private fun resolveBaseUrl(): String {
        val explicit = environment.getProperty("app.data-platform.base-url")
        if (!explicit.isNullOrBlank()) return explicit.removeSuffix("/")

        val localServerPort = environment.getProperty("local.server.port")
        if (!localServerPort.isNullOrBlank()) return "http://127.0.0.1:$localServerPort"

        val serverPort = environment.getProperty("server.port")
        if (!serverPort.isNullOrBlank() && serverPort != "0") return "http://127.0.0.1:$serverPort"

        return "http://127.0.0.1:8080"
    }
}
