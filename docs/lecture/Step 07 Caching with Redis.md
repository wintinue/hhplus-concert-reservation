# Step 07. Caching with Redis

## 챕터1-1 : 들어가기에 앞서

<aside>
🔄 **지난 주차 돌아보기**

</aside>

- 여러분들이 항상 바라고 바라는 대용량 트래픽을 마주하는 데에 있어 가장 중요한 것은 **동시에 수많은 요청이 들어오더라도 정확하게 데이터를 관리하고, 처리하는 것**
- 만약 쿠폰 발급과 같이 수많은 사용자들에게 한정적인 수량의 상품을 제공해야 한다거나 콘서트 시나리오와 같이 동일한 좌석에 대한 중복 예약을 막는다던가 하는 부분은 **서비스의 성패**로 이어지기 때문
    - 만약 여러분들이 구현한 비즈니스 로직에서 데이터에 대한 동시성 문제가 발생해, 회사가 막대한 금전적 손해를 입게 된다면?
    - 아쉽지만 …
        
        ## **You’re fired!**
        
        ![105446446-stevejobs_pitch_1.jpg](Step%2007%20Caching%20with%20Redis/105446446-stevejobs_pitch_1.jpg)
        
        까지는 가지 않더라도 꽤나 심각한 손해로 이어질 수 있고, 평판에도 심각한 훼손이 생기겠죠… ☹️
        
- 이러한 상황에서 가장 중요한 솔루션 2가지
    - 정확한 트랜잭션 → DB 락으로 충분하지 않은 경우 분산락으로 해결을 보자!
    - 시스템 운영 비용과 성능을 고려 → 적합한 캐싱 전략을 적용한다!

## 챕터1-2 : 이번 강의에서 배울 것

<aside>
⛵ **이번 주차 목표**

</aside>

- **Redis의 특성에 따른 활용 방식을 고민해보고 효율적이고 올바른 사용 방법을 고민해봅니다.**
- **다량의 트래픽을 처리하기 위해 DB에 적은 부하를 주면서 정확하게 기능을 구현해봅니다.**
    - 즉, DB에 일부 기능을 대체하거나 DB의 일부 데이터를 캐싱하여 효율을 추구해봅니다.

## 챕터1-3 : Redis 개요

<aside>
💡

**본격적인 내용으로 들어가볼까요?**

</aside>

### 1. 왜 Redis는 DB에 비해 가볍고 빠른가

| 비교 항목 | **Redis** | **DB** |
| --- | --- | --- |
| 기본 저장소 | 메모리 | 스토리지 |
| 지원 기능 | 단순함 | 복잡함 |
| 서비스의 위치 | 내부/외부 | 외부 |

### 인메모리

- Redis는 데이터를 디스크가 아닌 RAM에 저장 (RAM은 디스크보다 접근 속도가 1000배 빠름)
- 기본적으로  대부분 소프트웨어의 병목은 IO 작업에서 일어남
    - 실제로 DB 지표를 볼 때도 **IOPS**를 상당히 중요하게 보는 이유

### 단순함

- 지원하는 데이터 구조, 명령어가 DB에 비해 매우 단순한 수준
    - 기본적으로 모든 데이터가 Key-Value 형태로 관리되며, 복잡한 자료형이라고 해도 최대가 리스트나 해시맵 같은 것들임 (복잡한 Join이나 스키마가 없음)
    - DB는 Join 등의 고급 명령어를 지원하고, 인덱싱이나 스키마 관리로 인한 엄청난 오버헤드가 있음
- 직관적인 내부 동작
    - 단일 스레드로 동작하기 때문에 Redis 내부에서 일어나는 일들에 대한 동시성 고려를 하지 않아도 됨
        - 명령한 순서대로 동작한다는 것을 보장
        - 단, 이러한 특징 때문에 `KEYS *`와 같은 명령을 함부로 날려서는 안됨!
    - 트랜잭션같은 기능이 빈약하고 격리 수준이나 롤백 프로세스 같은 것을 고려하지 않아도 됨
        - 결국 DB보다 쿼리 한번 날릴 때마다 생각할게 적다는 의미
        - 단, 명령어 하나는 원자성이 보장된다라는 사실과 혼동하면 안됨 (SETNX는 원자적인 명령)

### 네트워킹

- 서비스와 통신을 위한 프로토콜이 DB에 비해 훨씬 단순하기 때문에 네트워킹 오버헤드가 적음
- Redis는 로컬이나 클러스터 내부에 두는 경우도 흔하기 때문에 이 경우 통신 자체에 드는 오버헤드도 훨씬 적음
    - 일반적으로 DB는 백엔드 앱이 도는 클러스터 밖에 위치하는 경우가 대부분

![image.png](Step%2007%20Caching%20with%20Redis/image.png)

![image.png](Step%2007%20Caching%20with%20Redis/image%201.png)

![image.png](Step%2007%20Caching%20with%20Redis/image%202.png)

### ⚠️ 단점

- 기본적으로 영속성 보장이 약함 (안되는건 아니지만, 부가 기능으로써 동작하기 때문에 DB보다 불안정하고 비효율적일 수밖에 없음)
- 비즈니스 레벨의 정합성을 보장시키거나 복잡한 쿼리 구성 어려움 (스키마 정의, Join 같은 기능이 지원되지 않기 때문에)

## 챕터1-4 : 자료구조

### 2. Redis 자료구조

<aside>
ℹ️

Redis는 기본적으로 Key-Value 기반의 데이터 저장소입니다.

Value로는 단순히 String 뿐 아니라 **다양한 데이터 타입 ( Collections )**를 지원하는데, 이를 활용해 현업에서 단순히 캐시 뿐 아니라 다양한 용도로 활용하고 있어요. 

</aside>

![image.png](Step%2007%20Caching%20with%20Redis/image%203.png)

[Understand Redis data types](https://redis.io/docs/latest/develop/data-types/)

### Strings

- 일반 문자열 ( 최대 512MB )
- **단순 증감 연산** 혹은 **문자열로 표현 가능한 모든 자료** 저장하는 목적으로 활용
- 주요 명령어
    - 수정 계열 : set, setnx, setex ..
    - 조회 계열 : get, mget, ..
    - INCR 계열 ( 단순 증감 )

![img1.daumcdn.png](Step%2007%20Caching%20with%20Redis/img1.daumcdn.png)

### Sets

- 저장된 원소의 Unique 함을 보장하는 자료구조
- Set은 정렬을 보장하지 않음
- Set 자료구조를 위한 연산 지원 ( 교집합 / 합집합 / 차집합 등 )
- 주요 명령어
    - 수정 계열 : sadd, smove
    - 조회 계열 : smembers, scard, ..
    - 제거 계열 : spop, srem
    - 집합 계열 : sunion, sinter, sdif, ..
- 주요 사용 사례: 알림 수신 대상자 처리

![img1.daumcdn.png](Step%2007%20Caching%20with%20Redis/img1.daumcdn%201.png)

### Sorted Sets

- set + score 가중치 필드가 추가된 자료구조
- 데이터 저장 시, score 기반 오름차순 정렬로 저장됨
- score 값이 같은 경우, 사전 순으로 정렬
- 주요 명령어
    - 수정 계열 : ZADD
    - 조회 계열 : ZRANGE, ZRANGEBYSCORE, ZRANK, ZSCORE, ..
    - 제거 계열 : ZPOPMIN, ZPOPMAX, ZREM, ..
    - 증감 계열 : ZINCRBY
    - 집합 계열 : ZUNIONSTORE, ZINTERSTORE
- 주요 사용 사례: 랭킹 기능 구현

![img1.daumcdn.png](Step%2007%20Caching%20with%20Redis/img1.daumcdn%202.png)

### 이외 자료구조

- List(Linked List)
- Hash
- Geo
- Stream

## 챕터1-5 : Redis 사용 사례

### 3. Redis 기반의 랭킹 시스템

## 개요

- 실시간 랭킹이 필요한 시스템(게임 점수, 인기 콘텐츠, 상품 조회 등)에 적합
- Redis의 `Sorted Set`을 활용하여 효율적이고 빠른 랭킹 처리 가능

---

## 핵심 개념: Redis Sorted Set

- 구조: `ZADD key score member`
- 정렬: score 기준 오름차순 (낮은 점수 → 높은 점수)
- 특징:
    - 같은 점수면 사전 순 정렬
    - 점수 기반으로 순위 계산이 가능함
    - 조회 및 수정이 매우 빠름 (O(log N))

---

## 기본 명령어 예시

### 1. 점수 추가 및 갱신

```
ZADD game_ranking 1500 user123
ZADD game_ranking 3000 user456
ZADD game_ranking 2500 user789
```

### 2. 특정 사용자 점수 조회

```
ZSCORE game_ranking user123
```

### 3. 전체 랭킹 조회 (높은 점수가 1등)

```
ZREVRANGE game_ranking 0 9 WITHSCORES  # Top 10
```

### 4. 특정 사용자 순위 조회

```
ZREVRANK game_ranking user123
```

### 5. 점수 증가 (예: 게임 승리 시 점수 부여)

```
ZINCRBY game_ranking 100 user123
```

---

## 응용 예시: 일간 / 주간 랭킹 분리

### 키 구성 전략

- `ranking:daily:20240429`
- `ranking:weekly:2024-W18`

### TTL 설정

- 일간 랭킹은 하루 후 자동 만료
- 주간 랭킹은 일주일 후 만료

```
EXPIRE ranking:daily:20240429 86400
```

---

## ⚠️ 주의할 점

- Sorted Set에 너무 많은 데이터를 넣으면 메모리 부담 ↑
- TTL 없이 데이터 계속 쌓이면 만료 누락 가능성 존재
- 동일 점수 처리 로직 필요 시 보완 로직 필요

---

## 실전 활용 예시

### 인기 상품 조회 랭킹 (24시간 클릭 수 기준)

- `ZINCRBY product:ranking:clicks:20240429 1 product123`
- 매일 자정 배치로 새로운 키 생성 및 TTL 부여

### 사용자 게임 랭킹

- `ZADD game:ranking:user 5000 userA`
- 클라이언트에게 `ZREVRANK` 결과 전달하여 등수 노출

---

## 시각화 전략

- Redis에서 Top N을 가져와 DB 캐싱 → 사용자에게 제공
- 그래프 구성: 시간대 별 랭킹 변동, 내 순위 추이 등 추가 분석 가능

---

## 요약

| 기능 | Redis 명령어 |
| --- | --- |
| 점수 등록/수정 | ZADD, ZINCRBY |
| 순위 조회 | ZREVRANK, ZREVRANGE |
| 점수 조회 | ZSCORE |
| 데이터 만료 | EXPIRE |

---

## 참고

- Redis 공식 문서: https://redis.io/commands/zadd/
- Redis TTL 관리: https://redis.io/docs/manual/key-expiration/

## 챕터1-6 : 과제

<aside>
🚩 **이번 주차 과제**

</aside>

> **각 시스템(랭킹, 비동기) 디자인 설계 및 개발 후 회고 내용을 담은 보고서 제출**
> 

### **`[필수] Ranking Design`**

- **이커머스 시나리오**
    
    가장 많이 주문한 상품 랭킹을 Redis 기반으로 개발하고 설계 및 구현
    
- **콘서트 예약 시나리오**
    
    (인기도) 빠른 매진 랭킹을 Redis 기반으로 개발하고 설계 및 구현
    

### **`[선택] Asynchronous Design`**

- **이커머스 시나리오**
    
    선착순 쿠폰발급 기능에 대해 Redis 기반의 설계를 진행하고, 적절하게 동작할 수 있도록 쿠폰 발급 로직을 개선해 제출
    
- **콘서트 예약 시나리오**
    
    대기열 기능에 대해 Redis 기반의 설계를 진행하고, 적절하게 동작할 수 있도록하여 제출
    

<aside>
<img src="https://www.notion.so/icons/light-bulb_red.svg" alt="https://www.notion.so/icons/light-bulb_red.svg" width="40px" />

**참고 PR 링크**

| 스택 | STEP7 |
| --- | --- |
| Java | [https://github.com/BEpaul/hhplus-e-commerce/pull/40](https://github.com/BEpaul/hhplus-e-commerce/pull/40) |
| Java | [https://github.com/juny0955/hhplus-concert/pull/32](https://github.com/juny0955/hhplus-concert/pull/32) |
| Kotlin | [https://github.com/chapakook/kotlin-ecommerce/pull/44](https://github.com/chapakook/kotlin-ecommerce/pull/44) |
| Kotlin | [https://github.com/psh10066/hhplus-server-concert/pull/24](https://github.com/psh10066/hhplus-server-concert/pull/24) |
| TS | [https://github.com/psyoongsc/hhplus-ts-server/pull/49](https://github.com/psyoongsc/hhplus-ts-server/pull/49) |
| TS | [https://github.com/suji6707/nestjs-case-02-ticketing/pull/7](https://github.com/suji6707/nestjs-case-02-ticketing/pull/7) |
</aside>

### [참고] Redis 기반의 구조 개선

<aside>
🗡️ 만약 RDBMS가 주는 제약을 뛰어넘고 더 높은 처리량을 보장하고 싶다면? 
REDIS를 보조 Datasource 로서 잘 활용해볼 수 있지 않을까 !
- 빠른 연산 속도
- 원자성을 보장
- 다양한 자료 구조 및 기능

</aside>

### 선착순 쿠폰 발급

**쿠폰 선착순 요청 (first-come coupon issuance)**

- 힌트
    
    Sorted Set 혹은 List
    
    ```bash
    # Sorted Set
    ZADD coupon_requests 1748763903841 "user1" # 신청시각
    ZADD coupon_requests 1748763903842 "user2"
    ZRANGE coupon_requests 0 1 WITHSCORES # 선착순 2명 조회
    ```
    
    ```bash
    # List
    LPUSH coupon_queue "user1"
    LPUSH coupon_queue "user2"
    LPOP coupon_queue  # 반환: user1 (먼저 들어온 사용자)
    ```
    

**쿠폰 중복 발급 방지 (duplicate coupon issuance)**

- 힌트
    
    Set
    
    ```bash
    SADD issued_coupons "user1"
    SISMEMBER issued_coupons "user1"  # 반환: 1 (이미 발급됨)
    SISMEMBER issued_coupons "user2"  # 반환: 0 (발급되지 않음)
    ```
    

### 대기열 토큰 관리

**대기유저 (Waiting Tokens)**

**활성유저 (Active Tokens)**

### 부록

- Redis가 현업에서 어떤식으로 구현되고 안전하게 서비스 할 수 있는가? (자동복구 포함)

      : https://tech.kakaopay.com/post/kakaopaysec-redis-on-kubernetes/