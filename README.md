## Concert Reservation Service

## Design Docs

- API Specification: [`docs/openapi.yaml`](docs/openapi.yaml)
- ERD: [`docs/erd.md`](docs/erd.md)
- Infrastructure Diagram: [`docs/infra.md`](docs/infra.md)
- Service Sequence Diagram: [`docs/service-sequence.md`](docs/service-sequence.md)
- Status Sequence and Transition Rules: [`docs/status-sequences.md`](docs/status-sequences.md)
- Database Analysis Report: [`docs/advanced-db-report.md`](docs/advanced-db-report.md)
- Concurrency Control Report: [`docs/concurrency-control-report.md`](docs/concurrency-control-report.md)
- Redis Caching Strategy and Performance Report: [`docs/redis-caching-strategy-and-performance-report.md`](docs/redis-caching-strategy-and-performance-report.md)
- Redis Ranking and Queue Design Report: [`docs/redis-ranking-and-queue-design-report.md`](docs/redis-ranking-and-queue-design-report.md)

## Getting Started

### Overview

- Backend API server built with Kotlin and Spring Boot
- Build tool: Gradle Wrapper (8.11.1)
- Runtime target: Java 17 (toolchain fixed)
- Local database: MySQL 8 via `docker-compose`
- Local cache/lock store: Redis 7 via `docker-compose`
- Test stack: JUnit5, Spring Boot Test, and Testcontainers(MySQL, Redis)

### Prerequisites

- Java 17
- Docker / Docker Compose

### Quick Start

#### 1. Start Infrastructure

Run the Docker containers required for the `local` profile.

```bash
docker-compose up -d
```

#### 2. Start Spring Server

The default active profile is `local`. Start the Spring server after the local MySQL and Redis containers are running.

```bash
./gradlew bootRun
```

On Windows, use:

```bash
gradlew.bat bootRun
```

The application runs on `http://localhost:8080` by default.

You can also verify the server with the actuator endpoint:

```text
http://localhost:8080/actuator
```

#### 3. Run Tests

```bash
./gradlew test
```

실제 로컬 인프라(MySQL, Redis) 기반 통합 시나리오만 빠르게 검증하려면 현재 떠 있는 Docker 컨테이너를 사용하는 아래 테스트를 실행할 수 있습니다.

```bash
docker-compose up -d
./gradlew test --tests kr.hhplus.be.server.LocalReservationIntegrationTest -Dlocal.integration.enabled=true
```
