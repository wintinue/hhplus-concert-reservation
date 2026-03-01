package kr.hhplus.be.server.concert.common

import org.springframework.http.HttpStatus

open class ApiException(
    val status: HttpStatus,
    val code: String,
    override val message: String,
    val extra: Map<String, Any?> = emptyMap(),
) : RuntimeException(message)

class UnauthorizedException(message: String) : ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message, mapOf("authType" to "BEARER"))

class ForbiddenException(message: String) : ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", message, mapOf("reason" to "RESOURCE_OWNER_MISMATCH"))

class NotFoundException(resource: String, resourceId: Any? = null) :
    ApiException(
        HttpStatus.NOT_FOUND,
        "NOT_FOUND",
        "$resource 를 찾을 수 없습니다.",
        mapOf("resource" to resource, "resourceId" to resourceId?.toString()),
    )

class ConflictException(message: String, conflictType: String = "CONFLICT", retryable: Boolean = false, extra: Map<String, Any?> = emptyMap()) :
    ApiException(HttpStatus.CONFLICT, "CONFLICT", message, mapOf("conflictType" to conflictType, "retryable" to retryable) + extra)

class ValidationException(message: String, errors: List<Map<String, String>>) :
    ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", message, mapOf("errors" to errors))

class QueueNotReadyException(message: String, queueStatus: String, retryAfterSeconds: Long) :
    ApiException(
        HttpStatus.LOCKED,
        "QUEUE_NOT_READY",
        message,
        mapOf("queueStatus" to queueStatus, "retryAfterSeconds" to retryAfterSeconds),
    )

class QueueExpiredException(message: String, queueStatus: String, expiredAt: Any?) :
    ApiException(
        HttpStatus.GONE,
        "QUEUE_TOKEN_EXPIRED",
        message,
        mapOf("queueStatus" to queueStatus, "retryAfterSeconds" to 1, "expiredAt" to expiredAt),
    )
