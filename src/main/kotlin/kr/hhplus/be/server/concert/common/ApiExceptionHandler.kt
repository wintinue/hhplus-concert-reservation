package kr.hhplus.be.server.concert.common

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.LocalDateTime

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(ApiException::class)
    fun handleApiException(ex: ApiException): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.status(ex.status).body(
            linkedMapOf(
                "code" to ex.code,
                "message" to ex.message,
                "timestamp" to LocalDateTime.now(),
            ) + ex.extra.filterValues { it != null },
        )

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.badRequest().body(
            mapOf(
                "code" to "BAD_REQUEST",
                "message" to (ex.message ?: "잘못된 요청입니다."),
                "timestamp" to LocalDateTime.now(),
            ),
        )
}
