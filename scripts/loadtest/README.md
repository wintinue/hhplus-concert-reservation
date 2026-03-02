# Step 10 Load Test Scripts

## Prerequisites

1. `docker-compose up -d`
2. `./gradlew bootRun`

## Scenarios

- `concert-read`: 콘서트 목록 조회 부하 테스트
- `queue-issue`: 대기열 토큰 발급 부하 테스트
- `queue-poll`: 대기열 순번 조회 부하 테스트
- `seat-read`: 좌석 조회 API 부하 테스트
- `point-charge`: 동일 사용자 포인트 충전 쓰기 경로 부하 테스트
- `hold-seat-conflict`: 동일 좌석 선점 충돌 테스트
- `reservation_flow_smoke.py`: 좌석 선점 → 예약 생성 → 결제까지 반복 가능한 end-to-end smoke flow

## Test Data Reset

좌석 선점/예약/결제 실험을 반복하려면 아래 스크립트로 테스트 데이터를 초기화할 수 있습니다.

```bash
./scripts/loadtest/reset_reservation_test_data.sh
```

기본값은 `concert_id=1`, `schedule_id=1`, `loadtest-` 이메일 prefix 기준 정리입니다.

## Metrics Capture

`actuator` 메트릭과 Prometheus 샘플을 확인하려면:

```bash
./scripts/loadtest/measure_reservation_metrics.sh
```

## Examples

```bash
./scripts/loadtest/reset_reservation_test_data.sh
python3 scripts/loadtest/reservation_flow_smoke.py --schedule-id 1 --seat-id 1
python3 scripts/loadtest/basic_load_test.py --scenario concert-read --requests 300 --workers 30
python3 scripts/loadtest/basic_load_test.py --scenario queue-issue --users 60 --workers 20
python3 scripts/loadtest/basic_load_test.py --scenario queue-poll --users 60 --workers 20
python3 scripts/loadtest/basic_load_test.py --scenario seat-read --requests 120 --workers 12
python3 scripts/loadtest/basic_load_test.py --scenario point-charge --requests 80 --workers 20
python3 scripts/loadtest/basic_load_test.py --scenario hold-seat-conflict --requests 40 --workers 20
./scripts/loadtest/measure_reservation_metrics.sh
```
