package kr.hhplus.be.server.api

import kr.hhplus.be.server.reservation.infra.MockReservationDataPlatformClient
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

data class MockReservationPaymentRequest(
    val reservationId: Long,
    val userId: Long,
    val concertId: Long,
    val scheduleId: Long,
    val seatIds: List<Long>,
    val paymentId: Long,
    val amount: Long,
    val method: String,
    val paidAt: LocalDateTime,
)

@RestController
@RequestMapping("/api/mock/data-platform")
class MockDataPlatformController(
    private val mockReservationDataPlatformClient: MockReservationDataPlatformClient,
) {
    @PostMapping("/reservations/payments")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun receiveReservationPayment(
        @RequestBody request: MockReservationPaymentRequest,
    ) {
        mockReservationDataPlatformClient.record(request)
    }
}
