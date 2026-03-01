package kr.hhplus.be.server

import kr.hhplus.be.server.api.LoginRequest
import kr.hhplus.be.server.api.SignUpRequest
import kr.hhplus.be.server.auth.AuthService
import kr.hhplus.be.server.common.UnauthorizedException
import kr.hhplus.be.server.common.ValidationException
import kr.hhplus.be.server.domain.entity.UserEntity
import kr.hhplus.be.server.domain.entity.UserPointEntity
import kr.hhplus.be.server.domain.entity.UserSessionEntity
import kr.hhplus.be.server.domain.repository.UserPointRepository
import kr.hhplus.be.server.domain.repository.UserRepository
import kr.hhplus.be.server.domain.repository.UserSessionRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class AuthServiceTest {
    private val clock = Clock.fixed(Instant.parse("2026-03-01T00:00:00Z"), ZoneOffset.UTC)
    private val userRepository = mockk<UserRepository>()
    private val userPointRepository = mockk<UserPointRepository>()
    private val userSessionRepository = mockk<UserSessionRepository>()
    private val service = AuthService(userRepository, userPointRepository, userSessionRepository, clock)

    @Test
    fun `signUp은 사용자와 포인트 계정 그리고 세션을 생성한다`() {
        val request = SignUpRequest("tester", "tester@hhplus.kr", "password123")
        val savedUser = UserEntity(request.email, request.name, request.password).apply { id = 1L }

        every { userRepository.existsByEmail(request.email) } returns false
        every { userRepository.save(any<UserEntity>()) } returns savedUser
        every { userPointRepository.save(any<UserPointEntity>()) } answers { firstArg() }
        every { userSessionRepository.save(any<UserSessionEntity>()) } answers { firstArg() }

        val result = service.signUp(request)

        assertNotNull(result.accessToken)
        assertNotNull(result.refreshToken)
        assertEquals(3600, result.expiresIn)
        verify { userRepository.save(any<UserEntity>()) }
        verify { userPointRepository.save(any<UserPointEntity>()) }
        verify { userSessionRepository.save(any<UserSessionEntity>()) }
    }

    @Test
    fun `login은 이메일과 비밀번호가 맞으면 세션을 재발급한다`() {
        val user = UserEntity("tester@hhplus.kr", "tester", "password123").apply { id = 1L }
        every { userRepository.findByEmail("tester@hhplus.kr") } returns user
        every { userSessionRepository.save(any<UserSessionEntity>()) } answers { firstArg() }

        val result = service.login(LoginRequest("tester@hhplus.kr", "password123"))

        assertNotNull(result.accessToken)
        assertEquals(3600, result.expiresIn)
        verify { userSessionRepository.save(any<UserSessionEntity>()) }
    }

    @Test
    fun `requireUser는 활성 세션의 사용자를 반환한다`() {
        val user = UserEntity("tester@hhplus.kr", "tester", "password123").apply { id = 1L }
        every { userSessionRepository.findActiveSession("access-token") } returns
            UserSessionEntity("access-token", user, "refresh-token", LocalDateTime.now(clock).plusMinutes(30))

        val result = service.requireUser("Bearer access-token")

        assertEquals(1L, result.id)
        assertEquals("tester@hhplus.kr", result.email)
    }

    @Test
    fun `signUp은 잘못된 입력이면 ValidationException을 던진다`() {
        assertThrows(ValidationException::class.java) {
            service.signUp(SignUpRequest("", "invalid-email", "short"))
        }
    }

    @Test
    fun `login은 비밀번호가 틀리면 UnauthorizedException을 던진다`() {
        val user = UserEntity("tester@hhplus.kr", "tester", "password123").apply { id = 1L }
        every { userRepository.findByEmail("tester@hhplus.kr") } returns user

        assertThrows(UnauthorizedException::class.java) {
            service.login(LoginRequest("tester@hhplus.kr", "wrong-password"))
        }
    }
}
