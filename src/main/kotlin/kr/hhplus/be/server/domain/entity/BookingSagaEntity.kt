package kr.hhplus.be.server.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import kr.hhplus.be.server.domain.enums.BookingSagaStatus
import java.time.LocalDateTime

@Entity
@Table(
    name = "booking_sagas",
    indexes = [
        Index(name = "idx_booking_saga_reservation", columnList = "reservation_id", unique = true),
        Index(name = "idx_booking_saga_status", columnList = "saga_status"),
    ],
)
class BookingSagaEntity(
    @Column(name = "saga_id", nullable = false, unique = true, length = 100)
    var sagaId: String,
    @Column(name = "reservation_id", nullable = false)
    var reservationId: Long,
    @Column(name = "saga_type", nullable = false, length = 100)
    var sagaType: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "saga_status", nullable = false, length = 20)
    var sagaStatus: BookingSagaStatus,
    @Column(name = "current_step", nullable = false, length = 100)
    var currentStep: String,
    @Column(name = "started_at", nullable = false)
    var startedAt: LocalDateTime,
    @Column(name = "completed_at")
    var completedAt: LocalDateTime? = null,
    @Column(name = "failed_at")
    var failedAt: LocalDateTime? = null,
    @Column(name = "failure_reason", columnDefinition = "TEXT")
    var failureReason: String? = null,
) : BaseTimeEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "booking_saga_id")
    var id: Long? = null
}
