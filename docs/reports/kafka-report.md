# Step 09 Kafka 적용 보고서

## 1. 과제 해석

- Step 09의 핵심은 Step 08에서 분리한 애플리케이션 이벤트를 서비스 외부로 확장 가능한 Kafka 기반 이벤트 파이프라인으로 바꾸는 것이다.
- 결제 핵심 트랜잭션은 그대로 유지하고, 예약 결제 완료 이후의 부가 처리만 Kafka를 통해 비동기 전달해야 한다.
- 로컬에서도 Kafka producer, consumer, topic 구성을 확인할 수 있어야 하므로 실행 방법과 토픽 설계를 함께 남긴다.

## 2. Kafka를 쓰는 이유

- 데이터 플랫폼 연동 지연이나 일시 장애가 예약 결제 트랜잭션에 직접 영향을 주지 않는다.
- 동일 이벤트를 데이터 플랫폼 외 다른 소비자에게도 같은 토픽으로 확장할 수 있다.
- 파티션 키를 `reservationId` 로 두면 예약 단위 순서를 유지하면서 병렬 처리도 확보할 수 있다.
- outbox 와 결합하면 DB 커밋 이후에만 메시지를 발행하도록 제어할 수 있어 메시지 유실 위험을 줄일 수 있다.

## 3. Kafka 핵심 개념 정리

- Producer: 토픽에 메시지를 발행하는 주체
- Consumer: 토픽에서 메시지를 읽어 비즈니스 처리를 수행하는 주체
- Broker: 메시지를 저장하고 전달하는 Kafka 서버
- Topic: 메시지 분류 단위
- Partition: 토픽 내부 병렬 처리 단위. 같은 key 는 같은 partition 으로 들어가 순서가 유지된다.
- Consumer Group: 같은 토픽을 읽는 소비자 묶음. 같은 그룹 내에서는 한 파티션을 한 컨슈머만 읽는다.
- Offset: 컨슈머가 어디까지 처리했는지 가리키는 위치 정보

## 4. 이번 구현 내용

### 결제 완료 이벤트 흐름

1. `PayReservationUseCase` 가 결제 성공 후 `ReservationPaymentCompletedEvent` 를 발행한다.
2. `ReservationPaymentSagaListener` 가 커밋 이후 saga/outbox 를 생성한다.
3. `ReservationOutboxRelay` 가 outbox 레코드를 읽어 메시지 퍼블리셔로 전달한다.
4. `kafka` 프로필이 활성화되면 `KafkaReservationPaymentMessagePublisher` 가 `reservation.payment.completed` 토픽으로 발행한다.
5. `ReservationPaymentKafkaConsumer` 가 메시지를 consume 한 뒤 mock 데이터 플랫폼 API 로 전달한다.
6. 전송 성공 시 saga 는 `DATA_PLATFORM_SENT` 로 완료된다.

### 메시지 설계

- 토픽: `reservation.payment.completed`
- 메시지 key: `reservationId`
- 메시지 payload: `eventKey`, `sagaId`, `reservationId`, `payload`
- payload 전략: Full payload 를 사용하되, 현재 과제 범위에서는 예약 결제 전달에 필요한 최소 필드만 포함했다.

## 5. 장애 대응 포인트

- Outbox 발행 실패 시 `retry_count` 를 증가시키고 `FAILED` 상태로 남긴다.
- Kafka consumer 는 수동 ack 를 사용하므로 데이터 플랫폼 전송이 성공한 뒤에만 offset 이 커밋된다.
- consumer 에는 `DefaultErrorHandler` 와 고정 간격 재시도 설정을 넣어 일시 장애에 대해 자동 재시도한다.
- Kafka 미사용 환경에서는 direct publisher 로 fallback 되어 기존 Step 08 흐름과 호환된다.

## 6. 로컬 실행 방법

### Kafka 클러스터 기동

```bash
docker compose -p hhplus-kafka -f docker-compose.kafka.yml up -d
```

### 애플리케이션 실행

```bash
SPRING_PROFILES_ACTIVE=local,kafka ./gradlew bootRun
```

- Kafka bootstrap servers 는 `localhost:9092,localhost:9093,localhost:9094` 로 설정했다.
- `kafka` 프로필을 켜지 않으면 direct publisher 가 동작하므로 기존 테스트와 로컬 개발 흐름을 유지할 수 있다.

## 7. 테스트 정리

- `ReservationOutboxRelayTest`
  - 최대 재시도 초과 제외
  - 발행 실패 시 재시도 증가 및 saga 실패 처리
  - Kafka 발행 성공 시 outbox published 및 saga dispatched 처리
- `ReservationPaymentKafkaConsumerTest`
  - consume 성공 시 saga 완료 및 ack
  - consume 실패 시 saga 실패 및 예외 재전파
- `ReservationKafkaIntegrationTest`
  - MySQL + Redis + Kafka Testcontainers 조합으로 결제 이후 outbox, Kafka, consumer, mock data platform 전송까지 end-to-end 검증

## 8. 실제 검증 결과

- `./gradlew test --tests kr.hhplus.be.server.ReservationKafkaIntegrationTest` 통과
- `SPRING_PROFILES_ACTIVE=local,kafka ./gradlew bootRun` 으로 로컬 Kafka consumer 구독과 서버 기동 확인
- 로컬 HTTP 흐름에서는 결제 API 자체는 성공했지만, `outbox_events`, `booking_sagas` 레코드가 남지 않는 현상을 확인했다.
- 같은 경로가 Testcontainers 통합 테스트에서는 통과하므로, 로컬 환경 특이점 또는 기존 코드의 별도 결함이 있는지 추가 확인이 필요하다.
- `/api/v1/concerts` 는 기존 캐시 역직렬화 이슈로 500이 발생해 로컬 시나리오에서 `concertId=1` 을 직접 사용해 우회했다.

## 9. 다음 개선 후보

- 데이터 플랫폼 전용 consumer group 외 알림/통계 consumer group 추가
- DLT 토픽과 재처리 배치 추가
- payload 를 zero-payload(`reservationId`) 중심으로 축소하고 consumer 조회 방식으로 전환
