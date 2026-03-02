# DB 분석 보고서

## 1. 목적

이 문서는 콘서트 예약 서비스에서 조회가 오래 걸릴 가능성이 있는 기능과 동시성 이슈에 민감한 기능을 식별하고, 테이블 재설계 및 인덱스 관점의 개선안을 정리한다.

목적은 다음 두 가지다.

- 현재 구현에서 병목이 될 수 있는 조회 경로를 미리 식별하고 개선 방향을 제시한다.
- 동시성 이슈가 예민한 기능을 테스트로 고정해 회귀를 방지한다.

## 2. 조회 병목 후보 분석

### 2.1 콘서트 회차 조회

대상 기능:

- `GET /api/v1/concerts/{concertId}/schedules`

현재 구현 특성:

- 콘서트의 회차 목록을 조회한 뒤
- 회차별로 다시 좌석 목록 전체를 조회해서
- `totalSeat`, `availableSeat` 를 계산한다

예상 병목:

- 회차 수가 많을수록 회차별 좌석 조회가 반복되는 N+1 패턴
- 좌석 수가 많을수록 좌석 전체 row를 읽어서 카운트하는 비용 증가

현재 관련 코드:

- [ConcertFacadeService.kt](/Users/work/Development/hhplus-concert-reservation/src/main/kotlin/kr/hhplus/be/server/service/ConcertFacadeService.kt#L61)

개선안:

1. 집계 쿼리 도입
- `schedule_id` 기준으로 `count(*)`, `sum(case when seat_status='AVAILABLE' then 1 else 0 end)` 집계
- 좌석 전체 row를 애플리케이션으로 가져오지 않고 DB에서 바로 계산

2. 읽기 모델 분리
- `schedule_seat_summary` 같은 집계 테이블 또는 materialized read model 도입
- 좌석 상태 변경 시 summary를 함께 갱신

3. 인덱스 유지
- `idx_seats_schedule_status(schedule_id, seat_status)` 유지

권장 방향:

- 현재 단계에서는 집계 쿼리 우선
- 트래픽이 커지면 summary table 도입 검토

### 2.2 좌석 현황 조회

대상 기능:

- `GET /api/v1/schedules/{scheduleId}/seats`

현재 구현 특성:

- 회차별 좌석 전체 목록을 그대로 조회

예상 병목:

- 좌석 수가 많아질수록 payload 증가
- 클라이언트가 실제 필요한 컬럼보다 많은 엔티티 로드 비용 발생 가능

현재 관련 코드:

- [ConcertFacadeService.kt](/Users/work/Development/hhplus-concert-reservation/src/main/kotlin/kr/hhplus/be/server/service/ConcertFacadeService.kt#L81)

개선안:

1. projection 조회
- 엔티티 전체 로드 대신 필요한 컬럼만 select

2. 캐시 도입 후보
- 추후 Redis 또는 애플리케이션 캐시로 회차별 좌석 조회 캐시 검토
- 단, hold / sold 반영 지연 허용 범위를 먼저 정의해야 함

3. 페이지네이션 검토
- 좌석 수가 매우 큰 공연장을 고려하면 구역별/섹션별 조회로 분리 가능

### 2.3 대기열 위치 조회

대상 기능:

- `GET /api/v1/queue/tokens/{queueToken}`

현재 구현 특성:

- 현재 토큰 조회 후
- 같은 콘서트의 `WAITING` 토큰 전체를 불러와
- 앞선 순번 수를 메모리에서 계산

예상 병목:

- 대기열 길이가 길어질수록 waiting 전체 스캔 비용 증가
- 폴링 빈도가 높아지면 DB 읽기 부하가 빠르게 증가

현재 관련 코드:

- [QueueService.kt](/Users/work/Development/hhplus-concert-reservation/src/main/kotlin/kr/hhplus/be/server/queue/QueueService.kt#L57)

개선안:

1. count 기반 쿼리로 변경
- `queue_number < current.queue_number` 조건으로 ahead count만 계산

2. active token 구조 단순화
- 활성 토큰만 따로 관리하거나 `active_slot` 전략 도입 시 조회 범위 축소 가능

3. 추후 Redis 전환
- 대기열 규모가 커지면 Redis sorted set 기반 전환 검토

권장 방향:

- 현재는 count 쿼리 우선
- 대기열 대규모화 시 Redis 전환

### 2.4 예약 목록 조회

대상 기능:

- `GET /api/v1/reservations`

현재 구현 특성:

- 예약 페이지 조회 후
- 예약별로 다시 좌석 item 목록을 조회

예상 병목:

- 예약 건수가 많아질수록 N+1 조회 발생

현재 관련 코드:

- [ReservationPersistenceAdapter.kt](/Users/work/Development/hhplus-concert-reservation/src/main/kotlin/kr/hhplus/be/server/reservation/infra/ReservationPersistenceAdapter.kt#L166)

개선안:

1. reservation item batch 조회
- 예약 ID 목록을 한 번에 받아 `where reservation_id in (...)`

2. projection / fetch 전략 조정
- 목록 응답에 좌석 상세가 꼭 필요하지 않다면 요약 응답으로 축소

3. 인덱스 유지
- `idx_reservations_user_created(user_id, created_at)` 유지

### 2.5 만료 hold / 예약 정리

대상 기능:

- hold 만료 처리
- 미결제 예약 만료 처리

현재 구현 특성:

- 만료 조건을 만족하는 엔터티를 조회한 후
- 각 row에 대해 좌석 복구와 상태 변경 수행

예상 병목:

- 만료 대상이 몰리면 짧은 시간에 write 부하가 집중
- hold별/예약별 seat id 조회가 반복됨

현재 관련 코드:

- [ReservationPersistenceAdapter.kt](/Users/work/Development/hhplus-concert-reservation/src/main/kotlin/kr/hhplus/be/server/reservation/infra/ReservationPersistenceAdapter.kt#L91)
- [ReservationPersistenceAdapter.kt](/Users/work/Development/hhplus-concert-reservation/src/main/kotlin/kr/hhplus/be/server/reservation/infra/ReservationPersistenceAdapter.kt#L197)

개선안:

1. 만료 배치 분리
- API 진입 시 정리 + 별도 스케줄러 병행

2. 대상 건수 제한 배치
- 한 번에 처리하는 건수를 제한해 lock 집중 완화

3. 만료 인덱스 유지
- `idx_holds_expiry(hold_expires_at)` 유지

## 3. 테이블 재설계 / 인덱스 개선안 요약

### 3.1 즉시 적용 우선순위

1. 대기열 ahead count 전용 count 쿼리 추가
2. 회차 조회에서 좌석 전체 로드 대신 집계 쿼리 사용
3. 예약 목록 조회 시 reservation item batch 조회 도입

### 3.2 중기 개선안

1. `schedule_seat_summary` 읽기 모델 도입
2. 활성 queue token 구조 단순화
3. 좌석 조회 projection 최적화

### 3.3 장기 개선안

1. Redis 기반 queue 전환
2. Redis 기반 seat read cache
3. Outbox / MQ 기반 비동기 후처리

## 4. 동시성 이슈 민감 기능 리스트

다음 기능은 동시성 이슈에 민감하므로 테스트로 고정해야 한다.

1. 대기열 토큰 발급
- 동일 사용자와 동일 콘서트에 대해 활성 토큰이 1개만 유지되어야 한다.

2. 좌석 선점
- 동일 좌석에 대한 동시 선점 요청 중 하나만 성공해야 한다.

3. 예약 생성
- 동일 hold에 대한 동시 예약 생성 중 하나만 성공해야 한다.

4. 결제
- 동일 예약에 대한 동시 결제 요청 중 하나만 성공해야 한다.

5. 포인트 차감
- 동시에 여러 결제가 들어와도 잔액 음수가 되면 안 된다.

6. 예약 취소 / 환불
- 동일 예약에 대한 중복 취소 시 최종 상태와 환불 결과가 일관돼야 한다.

현재 테스트 반영 상태:

- 규칙성 테스트 반영
  - 동일 좌석 동시 선점
  - 동일 hold 동시 예약 생성
  - 동일 예약 동시 결제
  - 동일 사용자 포인트 동시 차감
  - 동일 예약 동시 취소 / 환불
- 실제 DB 통합 테스트 반영
  - 동일 사용자 / 동일 콘서트 대기열 토큰 동시 발급
  - 다중 유저 동시 좌석 요청 시 한 명만 성공

## 5. 테스트 전략

이번 과제에서 동시성 테스트의 목적은 "모든 테스트를 완벽히 통과시키는 것"보다 "동시성 이슈에 민감한 기능을 목록화하고, 그 규칙을 테스트로 고정하는 것"에 있다.

따라서 다음 원칙을 따른다.

- 성공 시나리오 1건만 보장돼야 하는 기능을 우선 테스트
- 테스트는 실제 DB 락 전체를 재현하기보다 핵심 불변식을 검증
- 회귀 시 어떤 규칙이 깨졌는지 바로 드러나도록 명확한 이름 사용
- 가능하면 핵심 경쟁 조건 중 최소 1개 이상은 실제 DB 기반 통합 테스트로 보완

## 6. 결론

현재 구현은 Step 04 핵심 과제 범위에서는 충분히 유효하다.
심화 과제 관점에서는 다음 세 가지를 우선 개선 대상으로 본다.

1. 회차 조회 집계 최적화
2. 대기열 위치 조회 count 쿼리 최적화
3. 동시성 민감 기능에 대한 규칙성 테스트 유지
