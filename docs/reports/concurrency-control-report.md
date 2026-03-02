# Concurrency Control Report

## 1. 목적

이 문서는 콘서트 예약 서비스에서 동시성 충돌이 발생할 수 있는 지점을 식별하고, 현재 구현이 어떤 방식으로 정합성을 보장하는지 정리한다.

핵심 목표는 다음과 같다.

- 좌석 선점, 예약 생성, 결제, 취소/환불에서 race condition을 방지한다.
- DB 락 순서를 일관되게 유지해 deadlock 가능성을 낮춘다.
- 테스트로 주요 동시성 규칙을 고정해 회귀를 막는다.

이번 과제에서는 구현 및 보고서 요구사항의 3개 항목을 모두 반영했다.

1. 좌석 임시 배정 시 락 제어
2. 잔액 차감 동시성 제어
3. 배정 타임아웃 해제 스케줄러 + 테스트

## 2. 보호해야 하는 임계 구역

### 2.1 좌석 선점

대상:

- `HoldSeatsUseCase`

위험:

- 여러 사용자가 같은 좌석을 동시에 선점하면 oversell의 출발점이 된다.

현재 제어 방식:

- `SeatRepository.findAllForUpdate(...)` 로 좌석 row에 비관적 락을 건다.
- 락을 획득한 뒤 `AVAILABLE` 상태만 `HELD` 로 전이한다.
- 이미 상태가 바뀐 좌석이 포함되면 `ConflictException(SEAT_CONFLICT)` 을 반환한다.

### 2.2 hold 확정 후 예약 생성

대상:

- `CreateReservationUseCase`

위험:

- 같은 `holdId` 로 동시에 예약 생성이 들어오면 중복 예약이 생길 수 있다.

현재 제어 방식:

- `SeatHoldRepository.findByIdForUpdate(...)` 로 hold row를 잠근 뒤 상태를 검증한다.
- `reservations.hold_id` 에 unique 제약을 둬 DB가 마지막 방어선을 맡는다.
- 중복 insert 시 `DataIntegrityViolationException` 을 `ConflictException(HOLD_ALREADY_CONFIRMED)` 으로 변환한다.

### 2.3 결제

대상:

- `PayReservationUseCase`

위험:

- 같은 예약에 대한 중복 결제가 들어오면 포인트가 중복 차감되거나 결제 row가 중복 생성될 수 있다.

현재 제어 방식:

- `ReservationRepository.findByIdForUpdate(...)` 로 예약 row를 선점한다.
- 예약 상태가 `PENDING_PAYMENT` 일 때만 결제를 진행한다.
- 포인트 차감은 `UserPointRepository.findForUpdate(...)` 로 사용자 포인트 row를 잠근 뒤 처리한다.
- 실패 시 좌석, hold, reservation 상태를 롤백 성격으로 정리한다.

### 2.4 취소와 환불

대상:

- `CancelReservationUseCase`

위험:

- 동시에 두 번 취소되면 좌석 복구, 환불, 결제 취소가 중복 실행될 수 있다.

현재 제어 방식:

- 예약 row를 `for update` 로 잠근 뒤 상태를 확인한다.
- 선행 요청이 `CANCELED` 상태로 바꾸면 후행 요청은 `ConflictException(RESERVATION_STATE)` 로 종료된다.
- 환불 포인트도 사용자 포인트 row 락으로 직렬화된다.

### 2.5 배정 타임아웃 해제 스케줄러

대상:

- `ReservationCleanupScheduler`

위험:

- 만료된 hold가 제때 정리되지 않으면 이미 사용 불가능해야 할 좌석이 계속 `HELD` 상태로 남는다.
- 만료된 미결제 예약이 정리되지 않으면 좌석 재판매와 상태 정합성이 깨진다.

현재 제어 방식:

- `@Scheduled` 기반 스케줄러가 주기적으로 `expireActiveHolds(now)`, `expirePendingReservations(now)` 를 호출한다.
- 테스트에서는 스케줄러 메서드를 직접 호출해 만료 hold가 `EXPIRED` 로 전이되고 좌석이 `AVAILABLE` 로 복구되는지 검증한다.

## 3. 락 순서 원칙

현재 구현은 다음 순서를 기본 원칙으로 사용한다.

1. 예약 또는 hold 같은 상위 상태 row를 먼저 잠근다.
2. 좌석 row를 잠근다.
3. 포인트 row를 잠근다.

모든 유스케이스에서 같은 순서를 유지해야 deadlock 가능성을 낮출 수 있다. 특히 향후 복수 예약 또는 다중 사용자 자원을 한 트랜잭션에서 동시에 다룰 경우, id 오름차순 같은 추가 정렬 규칙이 필요하다.

## 4. 비관적 락을 선택한 이유

이번 서비스는 다음 특성을 가진다.

- 좌석, 예약, 포인트는 충돌 시 비용이 크다.
- 실패 후 재시도보다 선제 직렬화가 안전하다.
- 핵심 row의 cardinality 가 높지 않아 핫스팟 관리가 가능하다.

따라서 현재 단계에서는 낙관적 락보다 비관적 락이 더 단순하고 예측 가능하다.

이번 구현에서 실제 사용한 방식:

- `SELECT FOR UPDATE` 에 해당하는 JPA 비관적 락
  - 좌석 선점
  - hold 확정
  - 예약 결제/취소
  - 사용자 포인트 차감/환불
- 조건부 UPDATE
  - 별도 SQL 조건부 UPDATE 문은 아직 도입하지 않았다.
- 낙관적 락
  - `@Version` 기반 낙관적 락은 아직 도입하지 않았다.

낙관적 락 전환을 검토할 수 있는 시점:

- 결제 직전 검증 로직이 더 짧아지고 충돌 빈도가 낮을 때
- read-heavy, write-light 패턴으로 바뀔 때
- 애플리케이션 레벨 재시도 정책을 명확히 도입했을 때

## 5. Deadlock 대응 전략

현재 코드에는 자동 재시도를 아직 넣지 않았다. 이유는 다음과 같다.

- 지금 단계에서는 트랜잭션 경로가 짧고, 락 획득 순서를 통제하는 편이 더 단순하다.
- 무분별한 재시도는 중복 부하와 응답 지연을 키울 수 있다.

운영 단계 권장안:

1. `DeadlockLoserDataAccessException`, `CannotAcquireLockException` 을 관측한다.
2. 멱등성이 보장된 유스케이스에 한해 2~3회 지수 백오프 재시도를 도입한다.
3. MySQL deadlock 로그와 lock wait timeout 로그를 수집한다.

## 6. 테스트 커버리지

현재 테스트는 아래 규칙을 고정한다.

- 동일 사용자와 동일 콘서트의 대기열 토큰 동시 발급은 하나만 성공한다.
- 동일 좌석 동시 선점은 하나만 성공한다.
- 동일 hold 동시 예약 생성은 하나만 성공한다.
- 동일 예약 동시 결제는 하나만 성공한다.
- 동일 사용자 포인트 동시 차감은 잔액 음수를 만들지 않는다.
- 동일 예약 동시 취소는 한 번만 환불된다.
- 만료된 hold는 스케줄러 실행 시 해제된다.

구분:

- 단위 수준 규칙 테스트: `ConcurrencyRuleTest`
- Testcontainers 기반 MySQL 통합 테스트: `ReservationIntegrationTest`
- 로컬 Docker MySQL 기반 통합 테스트: `LocalReservationIntegrationTest`
- 스케줄러 테스트: `ReservationCleanupSchedulerTest`

## 7. 테스트 결과

2026-03-02 기준으로 아래 테스트를 실행했다.

실행 명령:

- `./gradlew test --tests kr.hhplus.be.server.ConcurrencyRuleTest --tests kr.hhplus.be.server.ReservationCleanupSchedulerTest`
- `./gradlew test --tests kr.hhplus.be.server.LocalReservationIntegrationTest --rerun-tasks --no-daemon`
- `./gradlew test --tests kr.hhplus.be.server.ReservationIntegrationTest --rerun-tasks --no-daemon`

실행 결과 요약:

| 테스트 클래스 | 결과 |
| --- | --- |
| `ConcurrencyRuleTest` | 5개 실행, 0 실패, 0 에러 |
| `ReservationCleanupSchedulerTest` | 1개 실행, 0 실패, 0 에러 |
| `LocalReservationIntegrationTest` | 12개 실행, 0 실패, 0 에러 |
| `ReservationIntegrationTest` | 12개 스킵, 0 실패, 0 에러 |

세부 결과:

1. 동일 좌석 동시 선점
- 결과: 성공 1건, 충돌 1건
- 근거 테스트: `동일 좌석에 대한 동시 선점 요청은 하나만 성공해야 한다`

2. 동일 hold 동시 예약 생성
- 결과: 성공 1건, 충돌 1건
- 근거 테스트: `동일 hold에 대한 동시 예약 생성 요청은 하나만 성공해야 한다`

3. 동일 예약 동시 결제
- 결과: 성공 1건, 충돌 1건
- 근거 테스트: `동일 예약에 대한 동시 결제 요청은 하나만 성공해야 한다`

4. 동일 사용자 포인트 동시 차감
- 결과: 성공 1건, 충돌 1건, 잔액 음수 없음
- 근거 테스트: `동일 사용자 포인트에 대한 동시 차감은 잔액 음수를 만들지 않아야 한다`

5. 동일 예약 동시 취소
- 결과: 성공 1건, 충돌 1건, 환불 1회만 발생
- 근거 테스트: `동일 예약에 대한 동시 취소 요청은 최종 상태가 일관되어야 한다`

6. 배정 타임아웃 해제 스케줄러
- 결과: 스케줄러 호출 후 만료 hold는 `EXPIRED`, 좌석은 `AVAILABLE` 로 복구
- 근거 테스트: `cleanupExpiredResources는 만료된 hold와 reservation 정리를 호출한다`
- 통합 시나리오: `배정 타임아웃 해제 스케줄러가 만료된 hold를 정리한다`

7. 전체 운영 흐름 및 조회 API
- 결과: 로컬 Docker MySQL 기반 통합 테스트 12건 전부 통과
- 근거 테스트: `LocalReservationIntegrationTest`
- 포함 시나리오:
  - 회원가입 / 로그인 / 인증 사용자 조회
  - 대기열 위치 조회
  - 콘서트 / 회차 / 좌석 조회
  - 예약 목록 / 상세 조회
  - 좌석 선점 / 예약 생성 / 결제 / 취소 / 만료 처리

환경 메모:

- `ReservationIntegrationTest` 는 `@Testcontainers(disabledWithoutDocker = true)` 로 작성되어 있다.
- `LocalReservationIntegrationTest` 는 `application.yml` 의 `test` 프로필을 사용해 로컬 Docker MySQL(`localhost:3306/hhplus`)에 직접 연결한다.
- Docker 컨테이너 자체는 실행 중인 것을 확인했다.
- 다만 현재 Gradle/Testcontainers 실행에서는 Docker daemon 사용 가능 상태로 판별되지 않아 12개 케이스가 `skipped` 처리되었다.
- 따라서 현재 제출 기준의 실행 가능한 MySQL 통합 검증은 `LocalReservationIntegrationTest` 로 확보했고, 환경 이슈가 해소되면 동일 시나리오를 `ReservationIntegrationTest` 로도 검증할 수 있다.

## 8. 남은 개선 과제

1. 만료 hold / 예약 정리를 API 진입 시 즉시 처리하는 방식에서 배치 + 이벤트 기반으로 분리
2. 대기열과 hold 를 Redis 기반 TTL 구조로 이전해 DB 핫스팟 완화
3. 부하 테스트 도구(k6)로 P95, P99, lock wait time 측정
4. 결제 및 환불에 멱등 키를 도입해 외부 결제 연동 대비
