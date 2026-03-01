package kr.hhplus.be.server.auth

import kr.hhplus.be.server.api.AuthTokenResponse
import kr.hhplus.be.server.api.LoginRequest
import kr.hhplus.be.server.api.SignUpRequest
import kr.hhplus.be.server.common.ConflictException
import kr.hhplus.be.server.common.UnauthorizedException
import kr.hhplus.be.server.common.ValidationException
import kr.hhplus.be.server.domain.entity.UserEntity
import kr.hhplus.be.server.domain.entity.UserPointEntity
import kr.hhplus.be.server.domain.entity.UserSessionEntity
import kr.hhplus.be.server.domain.repository.UserPointRepository
import kr.hhplus.be.server.domain.repository.UserRepository
import kr.hhplus.be.server.domain.repository.UserSessionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val userPointRepository: UserPointRepository,
    private val userSessionRepository: UserSessionRepository,
    private val clock: Clock,
) {
    @Transactional
    fun signUp(request: SignUpRequest): AuthTokenResponse {
        validateSignUp(request)
        if (userRepository.existsByEmail(request.email)) {
            throw ConflictException("이미 가입된 이메일입니다.", conflictType = "DUPLICATE_EMAIL")
        }
        val user = userRepository.save(UserEntity(request.email, request.name, request.password.trim()))
        userPointRepository.save(UserPointEntity(user = user, balance = 0L, updatedAt = LocalDateTime.now(clock)))
        return createSession(user)
    }

    @Transactional
    fun login(request: LoginRequest): AuthTokenResponse {
        val user = userRepository.findByEmail(request.email) ?: throw UnauthorizedException("이메일 또는 비밀번호가 올바르지 않습니다.")
        if (user.passwordHash != request.password) {
            throw UnauthorizedException("이메일 또는 비밀번호가 올바르지 않습니다.")
        }
        return createSession(user)
    }

    fun requireUser(authorizationHeader: String?): UserEntity {
        val token = authorizationHeader?.removePrefix("Bearer ")?.trim().orEmpty()
        if (token.isBlank()) {
            throw UnauthorizedException("Bearer 토큰이 필요합니다.")
        }
        val session = userSessionRepository.findActiveSession(token) ?: throw UnauthorizedException("유효하지 않은 토큰입니다.")
        if (session.expiresAt.isBefore(LocalDateTime.now(clock))) {
            throw UnauthorizedException("만료된 토큰입니다.")
        }
        return session.user
    }

    private fun validateSignUp(request: SignUpRequest) {
        val errors = mutableListOf<Map<String, String>>()
        if (request.name.isBlank()) errors += mapOf("field" to "name", "reason" to "must not be blank")
        if (!request.email.contains("@")) errors += mapOf("field" to "email", "reason" to "must be email")
        if (request.password.length < 8) errors += mapOf("field" to "password", "reason" to "min length is 8")
        if (errors.isNotEmpty()) {
            throw ValidationException("회원가입 요청이 올바르지 않습니다.", errors)
        }
    }

    private fun createSession(user: UserEntity): AuthTokenResponse {
        val now = LocalDateTime.now(clock)
        val accessToken = UUID.randomUUID().toString()
        val refreshToken = UUID.randomUUID().toString()
        userSessionRepository.save(
            UserSessionEntity(
                accessToken = accessToken,
                user = user,
                refreshToken = refreshToken,
                expiresAt = now.plusHours(1),
            ),
        )
        return AuthTokenResponse(accessToken, refreshToken, 3600)
    }
}
