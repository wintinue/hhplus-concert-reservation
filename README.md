## 콘서트 예약 서비스

## 설계서

- API 명세서: [`docs/openapi.yaml`](docs/openapi.yaml)
- ERD: [`docs/erd.md`](docs/erd.md)
- 인프라 구성도: [`docs/infra.md`](docs/infra.md)
- 서비스 시퀀스 다이어그램: [`docs/service-sequence.md`](docs/service-sequence.md)
- 상태 종류별 상태 시퀀스/전이 규칙: [`docs/status-sequences.md`](docs/status-sequences.md)

## Getting Started

### Prerequisites

#### Running Docker Containers

`local` profile 로 실행하기 위하여 인프라가 설정되어 있는 Docker 컨테이너를 실행해주셔야 합니다.

```bash
docker-compose up -d
```


---
## 개발환경
- Kotlin + Spring Boot 기반 백엔드 API 서버 
- 빌드: Gradle Wrapper (8.11.1)
- 런타임 타깃: Java 17 (Toolchain 고정)
- 로컬 DB: MySQL 8 (docker-compose)
- 테스트: JUnit5 + Spring Boot Test + Testcontainers(MySQL)
