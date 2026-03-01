## Concert Reservation Service

## Design Docs

- API Specification: [`docs/openapi.yaml`](docs/openapi.yaml)
- ERD: [`docs/erd.md`](docs/erd.md)
- Infrastructure Diagram: [`docs/infra.md`](docs/infra.md)
- Service Sequence Diagram: [`docs/service-sequence.md`](docs/service-sequence.md)
- Status Sequence and Transition Rules: [`docs/status-sequences.md`](docs/status-sequences.md)
- Database Analysis Report: [`docs/advanced-db-report.md`](docs/advanced-db-report.md)

## Getting Started

### Overview

- Backend API server built with Kotlin and Spring Boot
- Build tool: Gradle Wrapper (8.11.1)
- Runtime target: Java 17 (toolchain fixed)
- Local database: MySQL 8 via `docker-compose`
- Test stack: JUnit5, Spring Boot Test, and Testcontainers(MySQL)

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

The default active profile is `local`. Start the Spring server after the local MySQL container is running.

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
