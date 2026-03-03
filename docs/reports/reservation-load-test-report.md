# Reservation Load Test Report

## 1. 목표

- 콘서트 예약 서비스에서 Step 10 과제의 필수 요구사항인 기본 부하 테스트를 수행한다.
- 실제 운영 관점에서 우선순위가 높은 읽기/대기열/쓰기 API의 처리량과 지연 시간을 확인한다.
- 병목이 의심되는 구간을 코드 수준에서 보완하고, 보완 이후 기준으로 다시 검증한다.

## 2. 테스트 대상 선정

### 우선 선정 API

1. `GET /api/v1/concerts`
- 사용자 진입 초기에 가장 많이 호출되는 조회 API다.
- 캐시 유무와 무관하게 트래픽이 집중되기 쉬운 진입점이라 기준치 확인이 필요하다.

2. `POST /api/v1/queue/tokens`
- 대기열 발급은 예약 플로우의 시작점이다.
- 동시 요청이 몰릴 때 DB/Redis를 함께 사용하므로 병목 가능성이 높다.

3. `GET /api/v1/queue/tokens/{queueToken}`
- 발급 이후 폴링이 반복되므로 누적 트래픽이 크게 발생한다.
- 단건 응답이지만 호출 빈도가 매우 높아 장애 전파 포인트가 될 수 있다.

4. `POST /api/v1/users/me/points/charges`
- 쓰기 트랜잭션과 사용자 단위 락을 사용한다.
- 공통 기능이면서 동시성 제어와 DB connection 사용량을 같이 확인할 수 있다.

### 설계만 포함한 시나리오

- `GET /api/v1/schedules/{scheduleId}/seats`
- `POST /api/v1/reservations/holds`

위 2개는 스크립트까지 작성했지만, 대기열이 단일 admit 슬롯 구조라 공유 로컬 DB 상태의 영향을 크게 받는다. 반복 가능한 수치 제출을 위해 본 보고서의 실측 결과는 위 우선 선정 API 위주로 정리했다.

## 3. 사전 점검과 보완

### 확인된 병목 후보

- 조회 API인 `getSchedules`, `getSeats`가 요청마다 만료된 hold/reservation 정리 쿼리를 실행하고 있었다.
- 이미 `ReservationCleanupScheduler`가 주기 정리를 수행하는데도, 읽기 트래픽이 들어올 때마다 동일한 정리 작업을 반복했다.
- 이 구조는 좌석/회차 조회 부하에서 불필요한 DB 스캔과 connection 점유를 늘릴 수 있다.

### 적용한 보완

- `ReservationExpirationCoordinator`를 추가했다.
- 조회 경로에서는 만료 정리를 `reservation.cleanup.min-interval-ms` 단위로만 수행하도록 바꿨다.
- 쓰기 경로와 스케줄러는 기존처럼 만료 정리를 수행해 정합성을 유지했다.

관련 코드:

- `src/main/kotlin/kr/hhplus/be/server/reservation/application/ReservationExpirationCoordinator.kt`
- `src/main/kotlin/kr/hhplus/be/server/service/ConcertFacadeService.kt`
- `src/main/kotlin/kr/hhplus/be/server/reservation/application/ReservationCleanupScheduler.kt`

## 4. 실행 환경

- 실행 일시: 2026-03-02
- 환경: 로컬 Mac 개발환경
- 애플리케이션: Spring Boot + Kotlin
- DB: MySQL 8 (`docker-compose`)
- Cache/Lock: Redis 7 (`docker-compose`)
- DB pool: Hikari `maximum-pool-size=3`
- 부하 스크립트: `scripts/loadtest/basic_load_test.py`

## 5. 실행 방법

```bash
docker-compose up -d
./gradlew bootRun

./scripts/loadtest/reset_reservation_test_data.sh
python3 scripts/loadtest/basic_load_test.py --scenario concert-read --requests 200 --workers 20
python3 scripts/loadtest/basic_load_test.py --scenario queue-issue --users 40 --workers 20
python3 scripts/loadtest/basic_load_test.py --scenario queue-poll --users 40 --workers 20
python3 scripts/loadtest/basic_load_test.py --scenario point-charge --requests 80 --workers 20
./scripts/loadtest/measure_reservation_metrics.sh
```

### 부가 도구

- `scripts/loadtest/reset_reservation_test_data.sh`
  - 좌석/예약/결제/대기열 관련 테스트 데이터를 재실행 가능한 상태로 초기화한다.
- `scripts/loadtest/measure_reservation_metrics.sh`
  - `actuator/metrics`, `actuator/prometheus`를 이용해 HTTP 요청과 Hikari pool 상태를 함께 확인한다.
- `scripts/loadtest/reservation_flow_smoke.py`
  - 좌석 선점 → 예약 생성 → 결제의 전체 흐름을 반복 가능하게 검증한다.

## 6. 실측 결과

### 6-1. 콘서트 목록 조회

- 시나리오: `concert-read`
- 요청 수: 200
- 동시 작업자: 20
- 결과:
  - 성공: 200
  - 실패: 0
  - 처리량: `321.24 req/s`
  - 평균 지연: `60.80 ms`
  - P50: `36.08 ms`
  - P95: `276.22 ms`
  - Max: `292.65 ms`

### 6-2. 대기열 토큰 발급

- 시나리오: `queue-issue`
- 사용자 수: 40
- 동시 작업자: 20
- 결과:
  - 성공: 40
  - 실패: 0
  - 처리량: `145.61 req/s`
  - 평균 지연: `109.53 ms`
  - P50: `119.62 ms`
  - P95: `146.23 ms`
  - Max: `155.91 ms`

### 6-3. 대기열 순번 조회

- 시나리오: `queue-poll`
- 요청 수: 40
- 동시 작업자: 20
- 결과:
  - 성공: 40
  - 실패: 0
  - 처리량: `235.52 req/s`
  - 평균 지연: `65.64 ms`
  - P50: `73.64 ms`
  - P95: `85.10 ms`
  - Max: `91.50 ms`

### 6-4. 포인트 충전

- 시나리오: `point-charge`
- 요청 수: 80
- 동시 작업자: 20
- 결과:
  - 성공: 80
  - 실패: 0
  - 처리량: `48.79 req/s`
  - 평균 지연: `326.42 ms`
  - P50: `71.13 ms`
  - P95: `1099.28 ms`
  - Max: `1638.04 ms`

### 6-5. 좌석 선점/예약/결제 smoke flow

- 실행 전: `./scripts/loadtest/reset_reservation_test_data.sh`
- 실행: `python3 scripts/loadtest/reservation_flow_smoke.py --schedule-id 1 --seat-id 1`
- 결과:
  - 대기열 발급 성공
  - 좌석 선점 성공
  - 예약 생성 성공
  - 결제 성공
  - 외부 이벤트 발행은 애플리케이션 outbox/saga 흐름으로 후속 처리

## 7. 해석

### 읽기 API

- `concert-read`는 캐시 도움을 받는 진입 조회 API답게 처리량이 높고 실패가 없었다.
- 다만 P95가 평균보다 크게 튀는 구간이 있어 순간적인 thread/connection 경쟁이 남아 있다.

### 대기열 API

- `queue-issue`, `queue-poll` 모두 실패 없이 처리되었다.
- 발급 API가 조회 API보다 느린 것은 DB 기록과 Redis 반영이 함께 일어나기 때문이다.
- 실제 서비스에서는 폴링 빈도 제한이 없으면 queue-poll이 누적 트래픽의 주 원인이 될 수 있다.

### 쓰기 API

- `point-charge`는 전부 성공했지만 tail latency가 매우 컸다.
- 동일 사용자에 대한 분산락과 DB connection pool 3개 제한이 겹치면서 P95가 1초를 넘겼다.
- 이 값은 장애라기보다 쓰기 병목의 조기 신호로 보는 것이 적절하다.

## 8. 개선/후속 조치

### 이번에 반영한 개선

- 읽기 API의 과도한 만료 정리 호출을 완화했다.
- 주기 정리 스케줄러와 요청 경로 간 역할을 분리했다.

### 추가로 권장하는 개선

1. 좌석/회차 조회 시 만료 정리 책임을 스케줄러 중심으로 더 명확히 이동
2. point charge 부하를 줄이기 위한 connection pool 및 락 대기시간 튜닝
3. actuator metrics, DB pool, Redis latency를 함께 수집하도록 모니터링 보강
4. 좌석 선점/결제 부하 테스트용 독립 테스트 데이터 초기화 스크립트 추가

## 9. 제출 체크

- 부하 테스트 대상 선정 및 목적 문서화: 완료
- 테스트 시나리오와 실행 방법 문서화: 완료
- 실제 실행 가능한 테스트 스크립트 작성: 완료
- 실제 수행 결과 정리: 완료
- 병목 탐색 및 개선 반영: 완료
- 좌석 선점/결제 재실행을 위한 테스트 데이터 초기화 스크립트 제공: 완료
- actuator 기반 메트릭/Prometheus 노출 및 수집 경로 제공: 완료
- 좌석 선점/예약/결제 전체 흐름 smoke 검증 수단 제공: 완료
