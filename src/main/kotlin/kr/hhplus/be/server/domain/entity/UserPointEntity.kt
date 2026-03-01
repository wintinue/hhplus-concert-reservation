package kr.hhplus.be.server.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.MapsId
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import org.hibernate.annotations.Check
import java.time.LocalDateTime

@Entity
@Table(name = "user_points")
@Check(constraints = "balance >= 0")
class UserPointEntity(
    @Id
    @Column(name = "user_id")
    var userId: Long? = null,
    @OneToOne(optional = false)
    @MapsId
    var user: UserEntity,
    @Column(nullable = false)
    var balance: Long,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime,
)
