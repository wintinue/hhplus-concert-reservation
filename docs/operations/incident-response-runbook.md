# Incident Response Runbook

## 1. 목적

- 콘서트 예약 서비스에서 장애를 탐지, 분류, 복구, 회고하기 위한 최소 운영 절차를 정리한다.
- Step 10 강의의 장애 대응 라이프사이클을 현재 애플리케이션 구조에 맞게 적용한다.

## 2. 탐지 포인트

### 기본 확인

- `GET /actuator/health`
- 애플리케이션 로그
- MySQL/Redis 컨테이너 상태

### 우선 모니터링해야 하는 지표

1. API error rate
2. P95/P99 latency
3. Hikari connection pool 사용량
4. Redis 응답 지연
5. Queue token 발급/조회 TPS
6. Outbox `FAILED` 건수

## 3. 장애 등급 예시

### SEV-1

- 전체 예약/결제 기능 불가
- 앱 기동 실패
- DB 연결 불가

### SEV-2

- 일부 핵심 API latency 급증
- 대기열 발급/조회 실패율 증가
- 포인트 충전/결제 실패율 급증

### SEV-3

- 백오피스성 조회 지연
- outbox 재시도 증가 but 사용자 영향 제한적

## 4. 초기 대응 절차

1. `actuator/health`와 컨테이너 상태를 확인한다.
2. 최근 5분 기준 에러 로그와 latency 급등 API를 식별한다.
3. 장애 범위를 `조회`, `대기열`, `예약`, `결제`, `외부 연동` 중 어디인지 분류한다.
4. 고객 영향도가 있으면 장애 전파 메시지를 먼저 발송한다.
5. 임시 완화책 적용 후 근본 원인을 추적한다.

## 5. 주요 시나리오별 대응

### 시나리오 A. 조회 API 지연 급증

증상:

- `GET /api/v1/concerts`
- `GET /api/v1/concerts/{concertId}/schedules`
- `GET /api/v1/schedules/{scheduleId}/seats`

점검:

1. DB connection pool 고갈 여부 확인
2. 만료 정리 스케줄러/요청 경로의 실행량 확인
3. 캐시 적중률과 Redis 상태 확인

즉시 대응:

1. 트래픽이 과도하면 조회 API rate limit 또는 임시 캐시 TTL 확대 검토
2. 스케줄러 주기와 cleanup interval 설정 확인
3. 필요 시 앱 인스턴스 증설

### 시나리오 B. 대기열 발급 또는 순번 조회 실패 증가

증상:

- `POST /api/v1/queue/tokens`
- `GET /api/v1/queue/tokens/{queueToken}`

점검:

1. Redis 연결 상태
2. queue token 테이블 insert/조회 latency
3. 특정 concert에 waiting queue가 과도하게 누적되었는지 확인

즉시 대응:

1. Redis 장애면 앱 재시작보다 Redis 상태 복구 우선
2. 폴링 빈도가 과도하면 클라이언트 폴링 간격 상향 또는 임시 throttling 적용
3. 단일 concert 집중 트래픽이면 queue issue API만 우선 보호

### 시나리오 C. 포인트 충전/결제 latency 급증

증상:

- `POST /api/v1/users/me/points/charges`
- `POST /api/v1/payments`

점검:

1. 동일 사용자 단위 락 경합 여부
2. Hikari pool 소진 여부
3. 결제 실패 후 좌석 해제/예약 만료가 정상 동작하는지 확인

즉시 대응:

1. connection pool, app replica, DB 리소스 상태 확인
2. 특정 사용자 또는 특정 콘서트로 락 경합이 집중되는지 로그 확인
3. 필요 시 포인트 충전/결제 API를 순차 처리 또는 임시 제한

### 시나리오 D. Outbox 적재 후 외부 전송 실패

증상:

- `OutboxEventStatus.FAILED` 증가
- 예약은 성공했으나 외부 데이터 플랫폼 반영이 지연됨

점검:

1. `/api/v1/ops/outbox-events`
2. `/api/v1/ops/booking-sagas/{reservationId}`
3. 외부 데이터 플랫폼 응답 상태

즉시 대응:

1. 사용자 핵심 기능 영향 여부부터 분리 판단
2. 실패 이벤트 수와 retry count를 확인
3. 외부 시스템 복구 후 relay 재처리 확인

## 6. 보고 템플릿

```text
[장애 전파]
- 시각:
- 등급:
- 영향 범위:
- 고객 영향:
- 현재 증상:
- 임시 조치:
- 다음 업데이트 예정 시각:
```

## 7. 회고 템플릿

```yaml
현상:
  - 타임라인
  - 영향 범위
  - 고객 영향도

조치 내용:
  - 장애 원인
  - 해소 타임라인
  - 단기 대응책
  - 후속 계획

상세 분석:
  - 5 whys

액션 아이템:
  - short-term
  - mid-term
  - long-term
```

## 8. 현재 기준의 운영상 취약점

1. DB connection pool 크기가 3으로 작아 쓰기 경로 tail latency에 취약하다.
2. queue polling 빈도 제어가 없어서 특정 이벤트 시 누적 호출이 급증할 수 있다.
3. 좌석 선점/결제의 고강도 동시 부하는 추가 계측이 더 필요하다.

## 9. 후속 권장 작업

1. Prometheus/Grafana 또는 Datadog 대시보드 연결
2. 좌석 선점/결제 고강도 동시성 시나리오 확장
3. queue polling client 제어 정책 수립
4. 장애 전파 메시지 템플릿을 실제 협업 채널 기준으로 구체화
