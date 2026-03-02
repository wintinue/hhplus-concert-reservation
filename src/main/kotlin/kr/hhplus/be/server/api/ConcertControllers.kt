package kr.hhplus.be.server.api

import kr.hhplus.be.server.auth.AuthService
import kr.hhplus.be.server.queue.QueueService
import kr.hhplus.be.server.reservation.application.CancelReservationUseCase
import kr.hhplus.be.server.reservation.application.CreateReservationUseCase
import kr.hhplus.be.server.reservation.application.GetReservationUseCase
import kr.hhplus.be.server.reservation.application.GetReservationsUseCase
import kr.hhplus.be.server.reservation.application.HoldSeatsUseCase
import kr.hhplus.be.server.reservation.application.PayReservationUseCase
import kr.hhplus.be.server.service.ConcertFacadeService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService,
) {
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    fun signUp(@RequestBody request: SignUpRequest): AuthTokenResponse = authService.signUp(request)

    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): AuthTokenResponse = authService.login(request)
}

@RestController
@RequestMapping("/api/v1")
class ConcertController(
    private val authService: AuthService,
    private val queueService: QueueService,
    private val concertFacadeService: ConcertFacadeService,
    private val holdSeatsUseCase: HoldSeatsUseCase,
    private val createReservationUseCase: CreateReservationUseCase,
    private val getReservationsUseCase: GetReservationsUseCase,
    private val getReservationUseCase: GetReservationUseCase,
    private val cancelReservationUseCase: CancelReservationUseCase,
    private val payReservationUseCase: PayReservationUseCase,
) {
    @GetMapping("/concerts")
    fun getConcerts(
        @RequestHeader("Authorization") authorization: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ConcertListResponse {
        authService.requireUser(authorization)
        return concertFacadeService.getConcerts(page, size)
    }

    @GetMapping("/concerts/rankings/fast-sold-out")
    fun getFastSoldOutConcerts(
        @RequestHeader("Authorization") authorization: String,
        @RequestParam(defaultValue = "10") limit: Int,
    ): FastSoldOutConcertListResponse {
        authService.requireUser(authorization)
        return concertFacadeService.getFastSoldOutConcerts(limit)
    }

    @GetMapping("/concerts/{concertId}/schedules")
    fun getSchedules(
        @RequestHeader("Authorization") authorization: String,
        @RequestHeader("X-Queue-Token") queueToken: String,
        @PathVariable concertId: Long,
    ): ScheduleListResponse = concertFacadeService.getSchedules(authService.requireUser(authorization), concertId, queueToken)

    @GetMapping("/schedules/{scheduleId}/seats")
    fun getSeats(
        @RequestHeader("Authorization") authorization: String,
        @RequestHeader("X-Queue-Token") queueToken: String,
        @PathVariable scheduleId: Long,
    ): SeatListResponse = concertFacadeService.getSeats(authService.requireUser(authorization), scheduleId, queueToken)

    @PostMapping("/queue/tokens")
    @ResponseStatus(HttpStatus.CREATED)
    fun issueQueueToken(
        @RequestHeader("Authorization") authorization: String,
        @RequestBody request: QueueTokenIssueRequest,
    ): QueueTokenIssueResponse = queueService.issueToken(authService.requireUser(authorization), request.concertId)

    @GetMapping("/queue/tokens/{queueToken}")
    fun getQueuePosition(
        @RequestHeader("Authorization") authorization: String,
        @PathVariable queueToken: String,
    ): QueuePositionResponse {
        return queueService.getPosition(authService.requireUser(authorization).id!!, queueToken)
    }

    @PostMapping("/reservations/holds")
    @ResponseStatus(HttpStatus.CREATED)
    fun holdSeats(
        @RequestHeader("Authorization") authorization: String,
        @RequestHeader("X-Queue-Token") queueToken: String,
        @RequestBody request: HoldSeatRequest,
    ): HoldSeatResponse = holdSeatsUseCase.execute(authService.requireUser(authorization).id!!, queueToken, request.scheduleId, request.seatIds)

    @PostMapping("/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    fun createReservation(
        @RequestHeader("Authorization") authorization: String,
        @RequestHeader("X-Queue-Token") queueToken: String,
        @RequestBody request: CreateReservationRequest,
    ): ReservationResponse = createReservationUseCase.execute(authService.requireUser(authorization).id!!, queueToken, request.holdId)

    @GetMapping("/reservations")
    fun getReservations(
        @RequestHeader("Authorization") authorization: String,
        @RequestHeader("X-Queue-Token") queueToken: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ReservationListResponse = getReservationsUseCase.execute(authService.requireUser(authorization).id!!, queueToken, page, size)

    @GetMapping("/reservations/{reservationId}")
    fun getReservation(
        @RequestHeader("Authorization") authorization: String,
        @RequestHeader("X-Queue-Token") queueToken: String,
        @PathVariable reservationId: Long,
    ): ReservationResponse = getReservationUseCase.execute(authService.requireUser(authorization).id!!, queueToken, reservationId)

    @PatchMapping("/reservations/{reservationId}")
    fun cancelReservation(
        @RequestHeader("Authorization") authorization: String,
        @RequestHeader("X-Queue-Token") queueToken: String,
        @PathVariable reservationId: Long,
        @RequestBody request: CancelReservationRequest,
    ): ReservationResponse = cancelReservationUseCase.execute(authService.requireUser(authorization).id!!, queueToken, reservationId, request.status)

    @GetMapping("/users/me/points")
    fun getMyPoints(
        @RequestHeader("Authorization") authorization: String,
    ): PointBalanceResponse = concertFacadeService.getMyPoints(authService.requireUser(authorization))

    @PostMapping("/users/me/points/charges")
    @ResponseStatus(HttpStatus.CREATED)
    fun chargeMyPoints(
        @RequestHeader("Authorization") authorization: String,
        @RequestBody request: PointChargeRequest,
    ): PointChargeResponse = concertFacadeService.charge(authService.requireUser(authorization), request.amount)

    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.CREATED)
    fun pay(
        @RequestHeader("Authorization") authorization: String,
        @RequestHeader("X-Queue-Token") queueToken: String,
        @RequestBody request: PaymentRequest,
    ): PaymentResponse = payReservationUseCase.execute(authService.requireUser(authorization).id!!, queueToken, request.reservationId, request.amount, request.method)
}
