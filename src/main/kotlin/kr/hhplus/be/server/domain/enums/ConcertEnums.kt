package kr.hhplus.be.server.domain.enums

enum class QueueStatus {
    WAITING,
    ADMITTED,
    IN_PROGRESS,
    EXPIRED,
    CANCELED,
    BLOCKED,
}

enum class SeatStatus {
    AVAILABLE,
    HELD,
    SOLD,
    CANCELED,
}

enum class HoldStatus {
    ACTIVE,
    CONFIRMED,
    EXPIRED,
    CANCELED,
}

enum class ReservationStatus {
    PENDING_PAYMENT,
    CONFIRMED,
    CANCELED,
    EXPIRED,
}

enum class PaymentStatus {
    SUCCESS,
    FAILED,
    CANCELED,
}

enum class PaymentMethod {
    CARD,
    BANK_TRANSFER,
    SIMPLE_PAY,
}

enum class PointTransactionType {
    CHARGE,
    USE,
    REFUND,
}

enum class ScheduleStatus {
    OPEN,
    CLOSED,
    CANCELED,
}

enum class OutboxEventStatus {
    PENDING,
    PROCESSING,
    PUBLISHED,
    FAILED,
}

enum class BookingSagaStatus {
    STARTED,
    COMPLETED,
    FAILED,
}
