# Step 08 Application Event 적용 정리

## 요구사항 해석

- Step 08 필수과제의 핵심은 예약 결제 성공 정보를 데이터 플랫폼으로 전송하는 부가 기능을 핵심 트랜잭션에서 분리하는 것이다.
- 전송 실패가 예약 확정, 좌석 판매, 포인트 차감의 원자성을 깨뜨리면 안 되므로 커밋 이후 처리로 이동해야 한다.

## 적용 내용

- `PayReservationUseCase` 는 결제 성공 시 `ReservationPaymentCompletedEvent` 만 발행한다.
- `ReservationPaymentSagaListener` 는 `@TransactionalEventListener(AFTER_COMMIT)` 로 이벤트를 수신해 Saga 와 Outbox 를 시작한다.
- `ReservationOutboxRelay` 는 outbox 에 저장된 이벤트를 읽어 실제 HTTP POST 로 mock 데이터 플랫폼 API 를 호출한다.
- `/api/mock/data-platform/reservations/payments` endpoint 가 mock API 역할을 수행하고, 수신 결과는 메모리에 기록해 검증 가능하게 만들었다.

## 기대 효과

- 결제 유스케이스는 예약 확정에 필요한 핵심 로직만 담당한다.
- 데이터 플랫폼 전송은 결제 트랜잭션과 분리되어 관심사가 명확해진다.
- outbox 가 있어 전송 실패 시 재시도 지점을 확보할 수 있다.
- Saga 상태를 남겨 후속 고도화의 시작점을 마련했다.
