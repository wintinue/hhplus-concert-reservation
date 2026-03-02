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
import kr.hhplus.be.server.domain.enums.OutboxEventStatus
import java.time.LocalDateTime

@Entity
@Table(
    name = "outbox_events",
    indexes = [
        Index(name = "idx_outbox_status_created", columnList = "event_status, created_at"),
        Index(name = "idx_outbox_saga", columnList = "saga_id"),
    ],
)
class OutboxEventEntity(
    @Column(name = "event_key", nullable = false, unique = true, length = 100)
    var eventKey: String,
    @Column(name = "saga_id", nullable = false, length = 100)
    var sagaId: String,
    @Column(name = "aggregate_type", nullable = false, length = 50)
    var aggregateType: String,
    @Column(name = "aggregate_id", nullable = false)
    var aggregateId: Long,
    @Column(name = "event_type", nullable = false, length = 100)
    var eventType: String,
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    var payload: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "event_status", nullable = false, length = 20)
    var eventStatus: OutboxEventStatus,
    @Column(name = "retry_count", nullable = false)
    var retryCount: Int = 0,
    @Column(name = "last_error", columnDefinition = "TEXT")
    var lastError: String? = null,
    @Column(name = "published_at")
    var publishedAt: LocalDateTime? = null,
) : BaseTimeEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "outbox_event_id")
    var id: Long? = null
}
