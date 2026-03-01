package kr.hhplus.be.server.concert.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "users")
class UserEntity(
    @Column(nullable = false, unique = true, length = 255)
    var email: String,
    @Column(nullable = false, length = 50)
    var name: String,
    @Column(name = "password_hash", nullable = false, length = 100)
    var passwordHash: String,
) : BaseTimeEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
}
