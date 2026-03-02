# Redis Ranking and Queue Design Report

## 1. 개요

콘서트 예약 시나리오를 기준으로 아래 두 가지를 Redis 기반으로 설계하고 구현했다.

- 필수 과제: 빠른 매진 랭킹 설계 및 구현
- 선택 과제: 대기열 기능의 Redis 기반 비동기 설계 및 개선

목표는 단순히 Redis를 붙이는 것이 아니라, 조회 부하가 큰 영역과 순서 보장이 중요한 영역에 Redis 자료구조를 적절히 배치해 시스템 처리량을 높이는 것이었다.

## 2. 필수 과제 - Ranking Design

### 2.1 요구사항 해석

- 콘서트 시나리오에서 인기도를 나타낼 수 있는 기준으로 `빠른 매진 랭킹`을 선택했다.
- 랭킹은 단순 조회 시점 집계가 아니라, 실제 매진 이벤트가 발생하는 순간 Redis에 기록되도록 설계했다.

### 2.2 랭킹 기준

- 랭킹 단위: 콘서트
- 매진 기준: 특정 회차의 마지막 좌석 결제가 성공해 전체 좌석 상태가 `SOLD` 가 되는 시점
- 점수 기준: `bookingOpenAt -> soldOutAt` 경과 시간
- 정렬 방식: 더 빨리 매진될수록 상위

같은 콘서트에 여러 회차가 존재할 수 있으므로, 동일 콘서트는 가장 빠르게 매진된 기록만 유지하도록 처리했다.

### 2.3 Redis 설계

- 자료구조: Sorted Set
- key: `concert:ranking:fast-sold-out`
- member: `concertId`
- score: `-(bookingOpenAt ~ soldOutAt 경과 ms)`

빠르게 매진된 콘서트일수록 더 큰 score 를 갖도록 음수 기반 점수를 사용했고, 조회 시에는 `ZREVRANGE` 기준으로 상위 랭킹을 가져오도록 했다.

추가 메타데이터는 별도 key 로 저장했다.

- key: `concert:ranking:fast-sold-out:meta:{concertId}`
- 값: `scheduleId`, `soldOutAt`, `soldOutSeconds`

### 2.4 구현 방식

- 결제 성공 후 좌석 상태를 `SOLD` 로 변경
- 해당 회차에 `SOLD` 가 아닌 좌석이 남아있는지 확인
- 남은 좌석이 없으면 매진 이벤트로 간주
- Redis Sorted Set에 랭킹 기록

구현 파일:

- `src/main/kotlin/kr/hhplus/be/server/common/ranking/ConcertRankingService.kt`
- `src/main/kotlin/kr/hhplus/be/server/reservation/infra/ReservationPersistenceAdapter.kt`
- `src/main/kotlin/kr/hhplus/be/server/api/ConcertControllers.kt`

### 2.5 조회 API

- `GET /api/v1/concerts/rankings/fast-sold-out?limit=10`

응답에는 아래 정보를 포함했다.

- rank
- concertId
- scheduleId
- title
- venueName
- bookingOpenAt
- soldOutAt
- soldOutSeconds

### 2.6 검증

- `ConcertRankingIntegrationTest`
  - 마지막 좌석 결제 시 랭킹이 생성되는지 검증
  - 조회 결과에 기대한 콘서트/회차/순위가 포함되는지 검증

## 3. 선택 과제 - Asynchronous Design

### 3.1 요구사항 해석

- 콘서트 예약 시나리오의 대기열 기능을 Redis 기반으로 재설계했다.
- 핵심은 대기 순번 계산, 활성 사용자 승급, 만료 처리를 DB 조회 중심 구조에서 Redis 중심 구조로 옮기는 것이었다.

### 3.2 기존 구조의 한계

기존 구현은 MySQL에 저장된 대기열 토큰을 기준으로 다음과 같은 방식으로 동작했다.

- waiting/admitted 상태를 DB에서 직접 조회
- 대기 순번 계산 시 waiting 목록을 스캔
- 활성 사용자가 비면 다음 사용자를 DB 조회 후 승급

이 구조는 기능적으로는 동작하지만, polling 요청이 많아질수록 DB 조회 부담이 커지는 문제가 있다.

### 3.3 개선 방향

완전한 Redis 단일 저장소로 전환하기보다는, 현재 시스템에 무리 없이 적용 가능한 하이브리드 구조를 선택했다.

- DB: 토큰 영속성, 사용자/콘서트 검증, 이력 보관
- Redis: 실시간 queue 상태 저장, 순번 계산, 승급 처리

즉, DB를 시스템 오브 레코드로 유지하면서 Redis를 실시간 상태 저장소로 사용했다.

### 3.4 Redis 설계

#### Waiting Queue

- 자료구조: Sorted Set
- key: `queue:concert:{concertId}:waiting`
- member: `queueToken`
- score: `queueNumber`

대기 순서가 score 로 고정되므로 `ZRANK` 로 현재 순번을 빠르게 계산할 수 있다.

#### Active Queue

- 자료구조: Sorted Set
- key: `queue:concert:{concertId}:active`
- member: `queueToken`
- score: `reservationWindowExpiresAt`

활성 토큰의 만료 시점을 score 로 사용해, 만료 처리와 다음 사용자 승급의 기준으로 활용했다.

### 3.5 동작 흐름

#### 토큰 발급

- DB에서 중복 활성 토큰 존재 여부를 확인
- 활성 사용자가 없으면 `ADMITTED`, 있으면 `WAITING` 으로 발급
- 발급 직후 Redis waiting/active sorted set 에 반영

#### 대기열 위치 조회

- Redis waiting zset 의 `ZRANK` 로 앞선 인원 수 계산
- DB 스캔 없이 현재 순번, ahead count, 예상 대기시간 계산

#### 활성 토큰 만료 처리

- active zset 에서 현재 시각보다 만료 score 가 작은 토큰을 만료 처리
- 만료된 토큰은 DB 상태도 함께 `EXPIRED` 로 반영

#### 다음 사용자 승급

- 활성 사용자가 없으면 waiting zset 의 `ZPOPMIN` 으로 가장 앞선 사용자 승급
- 승급된 토큰은 DB에 `ADMITTED` 로 반영 후 active zset 으로 이동

#### 결제 완료 후 종료

- 결제 성공 시 queue token 을 DB와 Redis 양쪽에서 즉시 제거/종료
- 이후 다음 polling 시 waiting 사용자 승급 가능

### 3.6 구현 파일

- `src/main/kotlin/kr/hhplus/be/server/queue/QueueService.kt`

### 3.7 검증

- `QueueServiceTest`
  - Redis 기반 순번 계산 검증
  - 만료 처리 검증
- `QueueRedisIntegrationTest`
  - 활성 사용자가 종료되면 다음 waiting 사용자가 Redis queue 에서 승급되는지 검증

## 4. 설계 판단 근거

### 랭킹에 Sorted Set을 쓴 이유

- 점수 기반 정렬이 자연스럽다.
- 상위 N개 조회가 빠르다.
- 랭킹 갱신 시 전체 집계를 다시 하지 않아도 된다.

### 대기열에 Sorted Set을 쓴 이유

- waiting 은 순서 보장이 중요하다.
- active 는 만료 시점 기준 정렬이 유용하다.
- `ZRANK`, `ZPOPMIN`, score 기반 조회 조합으로 queue 요구사항에 맞는 모델링이 가능하다.

## 5. 테스트 및 검증 결과

실행한 주요 검증은 다음과 같다.

- `./gradlew --no-daemon test --tests kr.hhplus.be.server.ConcertRankingIntegrationTest`
- `./gradlew --no-daemon test --tests kr.hhplus.be.server.QueueRedisIntegrationTest`
- `./gradlew --no-daemon test --tests kr.hhplus.be.server.QueueServiceTest`
- `./gradlew --no-daemon test`

최종적으로 전체 테스트를 통과했다.

## 6. 회고

### 좋았던 점

- Redis를 단순 캐시가 아니라 `랭킹`과 `대기열 상태 관리`에 각각 다른 자료구조로 적용하면서 자료구조 선택의 중요성을 체감할 수 있었다.
- 빠른 매진 랭킹은 조회 시점 집계가 아니라 이벤트 시점 기록 방식으로 바꾸면서 읽기 부하를 줄이는 구조를 만들 수 있었다.
- 대기열은 DB 조회 기반 구조보다 Redis 연산 기반 구조가 polling API 에 더 잘 맞는다는 점을 구현으로 확인할 수 있었다.

### 아쉬웠던 점

- 대기열을 완전한 Redis 단일 저장소로 만들지 않고, DB 영속성과 함께 사용하는 하이브리드 구조를 택했다.
- 현재 승급/만료 로직은 서비스 코드에서 여러 연산을 조합하고 있어, 초고트래픽 환경에서는 Lua script 기반 원자화가 더 적합할 수 있다.
- 랭킹도 현재는 빠른 매진 기준 하나만 구현했기 때문에, 기간별/회차별 분석까지는 확장하지 않았다.

### 다음 개선 방향

- queue 발급/승급/만료를 Lua script 로 묶어 더 강한 원자성을 확보
- Redis 재시작 시 queue 상태 복구 전략 명확화
- 랭킹 API 에 기간 조건, 통계 기준, 캐시 계층 추가
