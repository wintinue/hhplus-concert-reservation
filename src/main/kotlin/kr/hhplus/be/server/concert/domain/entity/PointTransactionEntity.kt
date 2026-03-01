package kr.hhplus.be.server.concert.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import kr.hhplus.be.server.concert.domain.enums.PointTransactionType
import java.time.LocalDateTime

@Entity
@Table(name = "point_transactions")
class PointTransactionEntity(
    @Id
    @Column(name = "transaction_id", length = 120)
    var transactionId: String,
    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    var user: UserEntity,
    @Column(nullable = false)
    var amount: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    var transactionType: PointTransactionType,
    @Column(name = "balance_after", nullable = false)
    var balanceAfter: Long,
    @Column(name = "transacted_at", nullable = false)
    var transactedAt: LocalDateTime,
) : BaseTimeEntity()
