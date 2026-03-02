# Redis Caching Strategy and Performance Report

## 1. Distributed Lock

### 적용 목적

- 단일 DB 트랜잭션만으로는 여러 애플리케이션 인스턴스에서 동일 자원에 동시에 접근하는 상황을 완전히 제어하기 어렵다.
- Redis 기반 분산락으로 DB 진입 전 경쟁을 줄이고, 불필요한 DB 커넥션 점유를 막는다.

### 구현 방식

- 구현 파일: `src/main/kotlin/kr/hhplus/be/server/common/lock/RedisSpinLockExecutor.kt`
- 방식: `SET NX EX/PX` 기반의 spin lock
- 해제 방식: Lua script 로 락 소유자 확인 후 삭제
- 실패 처리: 지정 시간 안에 락 획득 실패 시 `409 CONFLICT`

### 락 키와 범위

- `seat:{seatId}`
  - 대상: 좌석 선점(`HoldSeatsUseCase`)
  - 이유: 동일 좌석은 한 번에 한 요청만 선점 가능해야 한다.
  - 범위: 다중 좌석 요청 시 seatId 오름차순으로 순차 락 획득

- `hold:{holdId}`
  - 대상: 예약 확정(`CreateReservationUseCase`)
  - 이유: 동일 hold 로 예약 확정이 중복 생성되는 것을 막는다.

- `point:user:{userId}`
  - 대상: 포인트 충전, 결제 차감, 예약 취소 환불
  - 이유: 동일 사용자 포인트 잔액은 하나의 공유 자원이며, 충전/차감/환불이 서로 경쟁할 수 있다.

### DB Tx 와의 혼용 원칙

- 잘못된 방식: `@Transactional` 메서드 안에서 Redis 락 획득
  - 트랜잭션이 먼저 열려 DB 커넥션을 점유한 채 락 대기 가능
  - 앞선 트랜잭션 커밋 전의 데이터를 읽을 위험이 있음

- 적용 방식: `분산락 획득 -> TransactionTemplate 으로 DB 트랜잭션 시작`
  - 락 선점 후 트랜잭션 시작 순서를 강제
  - Step 6 요구사항의 핵심 주의사항을 반영

### 검증

- `DistributedLockIntegrationTest`
  - 동일 키는 직렬 실행
  - 다른 키는 병렬 실행

- 기존 예약/결제 동시성 테스트 보강
  - 좌석 선점, hold 확정, 결제, 취소 흐름에서 락 적용 후 전체 테스트 통과

## 2. Cache

### 캐시 대상 선정

- `GET /concerts`
  - 콘서트 목록은 자주 바뀌지 않고 반복 조회 가능성이 높음
  - TTL: 10분

- `GET /concerts/{concertId}/schedules`
  - 스케줄 정보와 예약 가능 좌석 수는 조회 빈도가 높음
  - TTL: 30초

- `GET /schedules/{scheduleId}/seats`
  - 좌석 상태 조회는 가장 빈번하지만 상태 변화도 잦음
  - TTL: 15초

### 구현 방식

- 구현 파일: `src/main/kotlin/kr/hhplus/be/server/common/cache/ConcertCacheService.kt`
- Redis CacheManager 기반 캐시
- 검증/인가/만료 정리는 캐시 전에 수행하고, 실제 조회 결과만 캐시에 저장

### 무효화 전략

- 좌석 상태 변경 시 아래 캐시를 함께 제거
  - 해당 `scheduleId` 의 좌석 목록 캐시
  - 해당 `concertId` 의 스케줄/잔여좌석 캐시

- 적용 지점
  - `markSeatsHeld`
  - `markSeatsSold`
  - `markSeatsAvailable`

### 검증

- `ConcertCacheIntegrationTest`
  - 같은 키 재조회 시 loader 재실행 없이 캐시 재사용
  - 좌석 상태 변경을 가정한 evict 후 재조회 시 loader 재실행 확인

### 성능 측정

#### 측정 방법

- 실행 테스트: `ConcertCachePerformanceTest`
- 방식:
  - cold path: 매 요청 전 캐시 제거 후 조회
  - warm path: 1회 조회로 캐시 적재 후 반복 조회
  - 각 시나리오 30회 평균
- 측정 대상:
  - 평균 응답 시간(ms)
  - cache miss 시 loader 호출 수
- 주의:
  - 현재 실행 환경에서는 Docker 기반 Testcontainers 성능 테스트가 스킵되어, 성능 수치는 캐시 서비스 레벨의 synthetic benchmark 로 측정했다.
  - loader 에는 조회 비용을 재현하기 위한 지연을 부여했다.

#### 측정 결과

| Scenario | Cold Avg (ms) | Warm Avg (ms) | Cold Loader Calls | Warm Loader Calls |
| --- | ---: | ---: | ---: | ---: |
| concert-list | 17.88 | 0.01 | 1.0 | 0.0 |
| concert-schedule | 24.14 | 0.01 | 1.0 | 0.0 |
| schedule-seat | 28.82 | 0.01 | 1.0 | 0.0 |

#### 해석

- 캐시 hit 시 3개 시나리오 모두 평균 응답시간이 거의 0ms 수준으로 감소했다.
- loader 호출 수가 `1.0 -> 0.0` 으로 줄어, 반복 조회에서 DB 접근이 제거되는 효과를 확인했다.
- 실제 운영/로컬 인프라에서는 Redis 네트워크 비용이 추가되므로 warm path 시간이 완전히 0에 수렴하지는 않겠지만, DB 조회 비용보다 훨씬 낮은 수준일 것으로 기대된다.

## 3. 결론

- 분산락은 좌석, hold, 포인트 자원에 각각 다른 키를 적용해 범위를 분리했다.
- 캐시는 조회 빈도와 변경 빈도를 기준으로 캐시 대상을 선정하고 TTL/무효화 전략을 적용했다.
- 성능 측정 기준으로도 cache hit 시 반복 조회 비용이 유의미하게 감소함을 확인했다.
- 최종적으로 전체 테스트(`./gradlew test --no-daemon`)를 통과했다.
