package kr.hhplus.be.server.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import kr.hhplus.be.server.domain.enums.PaymentMethod
import kr.hhplus.be.server.domain.enums.PaymentStatus
import java.time.LocalDateTime

@Entity
@Table(name = "payments")
class PaymentEntity(
    @ManyToOne(optional = false)
    @JoinColumn(name = "reservation_id")
    var reservation: ReservationEntity,
    @Column(nullable = false)
    var amount: Long,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var method: PaymentMethod,
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    var paymentStatus: PaymentStatus,
    @Column(name = "paid_at")
    var paidAt: LocalDateTime? = null,
    @Column(name = "canceled_at")
    var canceledAt: LocalDateTime? = null,
    @Column(name = "failure_reason")
    var failureReason: String? = null,
) : BaseTimeEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    var id: Long? = null
}
