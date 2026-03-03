# Reservation Service Operations Presentation Draft

## 1. 서비스 개요

- 서비스명: 콘서트 예약 서비스
- 핵심 플로우: 인증 → 콘서트 조회 → 대기열 발급 → 좌석 선점 → 예약 생성 → 결제 → 외부 데이터 플랫폼 전송
- 이번 발표 목표: 서비스의 수용 한계, 병목, 대응 방안을 이해관계자에게 공유

## 2. 시스템 구조

- 애플리케이션: Spring Boot + Kotlin
- 영속 저장소: MySQL 8
- 캐시/분산락/대기열 상태: Redis 7
- 비동기 후속 처리: Outbox + Saga 기반 외부 데이터 플랫폼 연동
- 모니터링: Actuator health, metrics, Prometheus endpoint

## 3. 주요 사용자 시나리오

1. 사용자는 콘서트 목록과 좌석 현황을 조회한다.
2. 예약 가능 상태를 얻기 위해 대기열 토큰을 발급받고 폴링한다.
3. 좌석을 선점하고 예약을 생성한다.
4. 결제를 완료하면 예약이 확정되고 후속 이벤트가 외부 시스템으로 전달된다.

## 4. 부하 테스트 대상과 이유

### 우선 검증 API

- `GET /api/v1/concerts`
  - 진입 트래픽 집중 구간
- `POST /api/v1/queue/tokens`
  - 예약 플로우 시작점
- `GET /api/v1/queue/tokens/{queueToken}`
  - 폴링성 고빈도 호출 구간
- `POST /api/v1/users/me/points/charges`
  - 락과 쓰기 트랜잭션 경합 확인 구간

### 보조 검증 플로우

- 좌석 선점/예약/결제 end-to-end smoke flow
- reset script 기반 반복 검증

## 5. 실측 결과 요약

### 기본 부하 테스트

- 콘서트 조회: 약 `321 req/s`, 실패 0
- 대기열 발급: 약 `145 req/s`, 실패 0
- 대기열 순번 조회: 약 `235 req/s`, 실패 0
- 포인트 충전: 약 `48 req/s`, 실패 0, tail latency 큼

### 시사점

- 조회와 대기열 읽기는 안정적이다.
- 쓰기 API는 connection pool과 사용자 단위 락의 영향으로 tail latency가 크다.
- 대기열 폴링 빈도는 운영상 주의가 필요하다.

## 6. 이번에 확인한 병목과 개선

### 문제

- 조회 API가 요청마다 만료 hold/reservation 정리 작업을 수행하고 있었다.
- 읽기 부하가 커질수록 불필요한 DB 정리 작업이 증가할 수 있었다.

### 조치

- `ReservationExpirationCoordinator` 도입
- 조회 경로 cleanup 호출을 최소 간격 기반으로 제한
- 스케줄러 중심 정리와 쓰기 경로 정합성은 유지

## 7. 운영 대응 체계

### 탐지

- `/actuator/health`
- `/actuator/metrics`
- `/actuator/prometheus`
- Hikari active/pending connection
- API latency/error rate
- Outbox failed count

### 대응

1. 장애 탐지
2. 영향 범위 분류
3. 즉시 완화책 적용
4. 복구 진행 공유
5. 회고 및 액션 아이템 정리

## 8. 현재 취약점

1. DB pool 크기가 작아 쓰기 tail latency에 취약
2. queue polling 과다 호출 시 누적 부하 우려
3. 좌석 선점/결제의 고강도 동시 부하는 추가 검증 필요

## 9. 다음 단계

1. 좌석 선점/결제 시나리오를 확장한 부하 테스트 자동화
2. Prometheus/Grafana 대시보드 연결
3. connection pool, 락 대기시간, 폴링 제어 정책 튜닝

## 10. 발표 시 강조 포인트

- 서비스는 기본 조회/대기열 부하에 안정적으로 동작한다.
- 쓰기 경로에서 tail latency가 관찰되어 운영 튜닝 우선순위가 명확하다.
- 장애 대응 매뉴얼과 메트릭 수집 경로를 함께 준비해 단순 구현 수준을 넘어 운영 준비도를 높였다.
