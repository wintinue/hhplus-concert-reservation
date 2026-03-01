package kr.hhplus.be.server.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "user_sessions")
class UserSessionEntity(
    @Id
    @Column(name = "access_token", length = 120)
    var accessToken: String,
    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    var user: UserEntity,
    @Column(name = "refresh_token", nullable = false, length = 120)
    var refreshToken: String,
    @Column(name = "expires_at", nullable = false)
    var expiresAt: LocalDateTime,
)
