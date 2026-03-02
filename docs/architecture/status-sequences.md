# 상태 변경 시퀀스 다이어그램

서비스의 주요 상태 종류별 상태 전이와 트리거 흐름을 정리한 문서입니다.

## 1. 대기열 토큰 상태 (`queue_tokens.queue_status`)

```mermaid
sequenceDiagram
    autonumber
    actor U as User(Client)
    participant API as API Server
    participant REDIS as Redis(Queue)
    participant RDSW as MySQL Primary

    U->>API: POST /api/v1/queue/tokens
    API->>REDIS: 대기열 엔트리 생성
    API->>RDSW: queue_token 저장
    Note over RDSW: queue_status = WAITING

    API->>REDIS: admission 처리
    API->>RDSW: 상태 반영
    Note over RDSW: queue_status = ADMITTED

    opt 예매 플로우 진입(관측용)
        U->>API: 좌석 조회/홀드 시작
        API->>RDSW: 상태 반영
        Note over RDSW: queue_status = IN_PROGRESS
    end

    alt ADMITTED TTL 만료
        API->>RDSW: 만료 처리
        Note over RDSW: queue_status = EXPIRED
    else 사용자 취소/이탈
        U->>API: 취소/이탈 요청
        API->>RDSW: 상태 반영
        Note over RDSW: queue_status = CANCELED
    else 정책 위반 탐지
        API->>RDSW: 차단 처리
        Note over RDSW: queue_status = BLOCKED
    end
```

### 상태 전이 규칙

- `WAITING` -> `ADMITTED`: 입장권 발급
- `ADMITTED` -> `IN_PROGRESS`: 예매 플로우 진입(옵션)
- `ADMITTED` -> `EXPIRED`: 입장권 TTL 만료
- `WAITING`/`ADMITTED`/`IN_PROGRESS` -> `CANCELED`: 사용자 취소/이탈
- `*` -> `BLOCKED`: 정책 위반/봇 탐지

## 2. 좌석 상태 (`seats.seat_status`)

```mermaid
sequenceDiagram
    autonumber
    actor U as User(Client)
    participant API as API Server
    participant REDIS as Redis(Hold/Lock)
    participant RDSW as MySQL Primary

    U->>API: POST /api/v1/reservations/holds
    API->>REDIS: 좌석 hold(TTL)
    API->>RDSW: 상태 반영
    Note over RDSW: seat_status = HELD

    alt 결제 성공
        U->>API: POST /api/v1/payments
        API->>RDSW: 좌석 확정
        Note over RDSW: seat_status = SOLD
    else 홀드 만료/해제
        API->>REDIS: hold 제거
        API->>RDSW: 좌석 반환
        Note over RDSW: seat_status = AVAILABLE
    else 결제 후 취소/환불
        U->>API: 예약/결제 취소
        API->>RDSW: 좌석 취소 처리
        Note over RDSW: seat_status = CANCELED
        opt 재판매 허용 정책
            API->>RDSW: 좌석 재오픈
            Note over RDSW: seat_status = AVAILABLE
        end
    end
```

### 상태 전이 규칙

- `AVAILABLE` -> `HELD`: 선점 성공
- `HELD` -> `SOLD`: 결제 성공
- `HELD` -> `AVAILABLE`: 홀드 만료/해제
- `SOLD` -> `CANCELED`: 취소/환불
- `CANCELED` -> `AVAILABLE`: 재판매 정책 허용 시

## 3. 좌석 홀드 상태 (`seat_holds.hold_status`)

```mermaid
sequenceDiagram
    autonumber
    actor U as User(Client)
    participant API as API Server
    participant REDIS as Redis(Hold)
    participant RDSW as MySQL Primary

    U->>API: POST /api/v1/reservations/holds
    API->>REDIS: hold 생성(TTL)
    API->>RDSW: hold 저장
    Note over RDSW: hold_status = ACTIVE

    alt 예약 생성 성공
        U->>API: POST /api/v1/reservations
        API->>RDSW: hold 확정
        Note over RDSW: hold_status = CONFIRMED
    else TTL 만료
        API->>REDIS: 만료 처리
        API->>RDSW: hold 만료
        Note over RDSW: hold_status = EXPIRED
    else 사용자 취소
        U->>API: 취소 요청
        API->>REDIS: hold 제거
        API->>RDSW: hold 취소
        Note over RDSW: hold_status = CANCELED
    end
```

### 상태 전이 규칙

- `ACTIVE` -> `CONFIRMED`: 예약 생성 시 확정
- `ACTIVE` -> `EXPIRED`: hold TTL 만료
- `ACTIVE` -> `CANCELED`: 사용자 취소/이탈

## 4. 예약 상태 (`reservations.reservation_status`)

```mermaid
sequenceDiagram
    autonumber
    actor U as User(Client)
    participant API as API Server
    participant REDIS as Redis(Hold/Queue)
    participant RDSW as MySQL Primary
    participant MQ as Message Queue

    U->>API: POST /api/v1/reservations (holdId)
    API->>REDIS: hold 유효성 검증
    API->>RDSW: reservation 생성
    Note over RDSW: reservation_status = PENDING_PAYMENT

    alt 결제 성공
        U->>API: POST /api/v1/payments
        API->>RDSW: 결제 + 좌석 확정
        Note over RDSW: reservation_status = CONFIRMED
        API->>MQ: PaymentCompleted 발행
    else 사용자 취소
        U->>API: PATCH /api/v1/reservations/{reservationId}
        API->>RDSW: 예약 취소
        Note over RDSW: reservation_status = CANCELED
    else 결제 미완료 만료
        API->>RDSW: 만료 배치
        Note over RDSW: reservation_status = EXPIRED
    end
```

### 상태 전이 규칙

- `PENDING_PAYMENT` -> `CONFIRMED`: 결제 성공
- `PENDING_PAYMENT` -> `CANCELED`: 사용자 취소
- `PENDING_PAYMENT` -> `EXPIRED`: 결제 미완료 만료
- `CONFIRMED` -> `CANCELED`: 확정 후 취소 정책 허용 시

## 5. 결제 상태 (`payments.payment_status`)

```mermaid
sequenceDiagram
    autonumber
    actor U as User(Client)
    participant API as API Server
    participant RDSW as MySQL Primary
    participant MQ as Message Queue

    U->>API: POST /api/v1/payments
    alt 결제 승인
        API->>RDSW: 결제 성공 저장
        Note over RDSW: payment_status = SUCCESS
        API->>MQ: PaymentCompleted 발행
    else 결제 실패
        API->>RDSW: 결제 실패 저장
        Note over RDSW: payment_status = FAILED
    end

    opt 취소/환불
        U->>API: 결제 취소 요청
        API->>RDSW: 결제 취소 저장
        Note over RDSW: payment_status = CANCELED
    end
```

### 상태 전이 규칙

- `SUCCESS`: 결제 승인 완료
- `FAILED`: 결제 실패
- `SUCCESS` -> `CANCELED`: 취소/환불 처리
