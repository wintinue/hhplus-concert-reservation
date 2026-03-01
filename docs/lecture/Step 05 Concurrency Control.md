# Step 05. Concurrency Control

## 챕터1-1 : 들어가기에 앞서

### **지난 강의 리마인드**

<aside>
💡

**지난 강의 한눈에 리마인드**

</aside>

1. DB 설계란 무엇이었나요?
    - 단순히 ERD를 그리는 게 아니라, **비즈니스 데이터를 어떻게 저장·연결·보호·확장할지**를 결정하는 설계의 총합이었습니다.
    - ANSI/SPARC 3계층 모델 관점에서:
        - **Conceptual**: 정규화, 도메인 모델 설계
        - **Internal**: 인덱스, 파티션, 트랜잭션, 락
        - **External**: API 응답용 읽기 모델, 뷰 등
2. **우리가 다룬 핵심 주제**
- **트랜잭션 기초 (ACID)**
    
    → 주문·결제와 같은 흐름에서 **정합성과 원자성**을 지키는 방법
    
- **정규화 vs 반정규화**
    
    → 1NF~BCNF 단계별 정리와 실전 반정규화 패턴(집계 테이블, 중복 컬럼 등)
    
- **키와 제약 조건 설계**
    
    → Surrogate 키, 복합 Unique, CHECK, FK, 멱등성 제약 설계
    
- **인덱스 및 통계**
    
    → WHERE, JOIN, ORDER 절 기준 인덱스 설계 / EXPLAIN 분석법 / 커버링 인덱스 개념
    
- **읽기 스케일아웃 전략**
    
    → Master/Replica 구조, 복제 지연 문제 해결법, DB 선택 기준 분리
    
- **파티셔닝 & 샤딩**
    
    → Range, Hash 기반 파티션 / 샤딩 키 선정 기준 / Cross-Shard 주의점
    
- **CQRS & Outbox 패턴**
    
    → 쓰기/읽기 모델 분리 및 메시지 발행 정합성 보장 방법
    
- **거버넌스 설계**
    
    → Online DDL, 스키마 명명 규칙, 암호화, 개인정보 수명 주기 정책
    
1. **실무 기준에서 중요한 포인트**

| **주제** | **핵심 포인트** |
| --- | --- |
| 트랜잭션 | COMMIT 전에 모든 상태가 완결돼야 함 |
| 정규화 | 무결성 확보, 하지만 성능 저하 시 반정규화 병행 |
| 인덱스 | 범위 조건 이후 컬럼은 인덱스 무시됨 주의 |
| CQRS | 쓰기 모델과 읽기 모델을 분리해 확장성 확보 |
| 리플리카 | 쓰기 직후 읽기는 반드시 Master에서 |
| 샤딩 | Join이 필요한 테이블은 같은 샤드에 묶어야 |
1. **우리가 최종적으로 얻고자 했던 것**
- **성능 ↔ 정합성 ↔ 확장성**의 균형을 잡는 DB 설계 관점
- **“왜 이런 스키마가 되었는가”를 설명할 수 있는 근거 있는 판단력**
- 애플리케이션 코드에 버그가 있어도 **DB가 마지막 방어선이 되도록 설계하는 실무 감각**

1. **실무형 인터뷰 질문  (각자 답변을 생각해보고 학습해보세요~!)**
    - **“정규화와 반정규화는 어떤 기준으로 판단하시나요?”**
    - **“인덱스가 많을수록 좋은 거 아닌가요?”**
    - **“리플리카를 붙였는데 저장 직후 데이터가 안 보입니다. 왜 그럴까요?”**
    - **“쓰기와 읽기 모델을 굳이 분리해야 하나요?”**

## 챕터1-2 : 이번 강의에서 배울 것

### 2.1 혹시 이런 경험 있으신가요?

- 파드(Pod) 1개로만 돌렸는데 **재고 1개 상품을 2명이 동시에 결제**해 CS가 폭발했습니다.
- `@Transactional`을 달았지만 **멀티스레드 테스트**에서 재고가 98→102까지 들쭉날쭉했습니다.
- **쿠폰 중복 발급**이 가끔 발생해 UNIQUE 제약을 걸었더니 이번엔 **Deadlock** 로깅이 쏟아졌습니다.
- 배치 직후 **리플리카에서 방금 쓴 데이터가 안 보여** QA에게 매번 잡히는데, 슬레이브 지연인지 팬텀 리드인지 헷갈립니다.

### 2.2 동시성이란 무엇일까요?

| 개념 | 한 줄 정의 | 실무 맥락 |
| --- | --- | --- |
| **동시성 (Concurrency)** | "같은 시간대에 여러 요청이 **논리적으로 겹쳐 실행**되는 현상" | Spring Tomcat 스레드 풀, Nest 이벤트 루프 + 워커 스레드 |
| **Race Condition** | 실행 순서·타이밍 차이로 **결과가 달라지는 문제** | 재고 ‑1, 쿠폰 중복 사용, 잔액 음수 |
| **Deadlock** | 서로 상대 락을 기다리며 **영원히 대기** | 계좌 A→B, B→A 교차 업데이트 |

> 서버가 1대여도, 요청당 스레드나 이벤트 루프 덕분에 **“수십‑수백 개 트랜잭션이 같은 DB 행”**을 동시에 건드리므로 동시성 이슈는 피할 수 없습니다.
> 

---

### 2.3 왜 중요할까요?

1. **정합성 붕괴 → 금전 손실** : Oversell·잔액 음수·쿠폰 초과 발급
2. **TPS 급락 & 장애** : 락 대기·Deadlock → 타임아웃·503
3. **확장 한계** : 샤딩·레플리카를 붙여도 **핫스팟**이 사라지지 않으면 병목
4. **운영 비용** : 재현 안 되는 버그 → 새벽 배포·Hotfix 루프
- **성능 , 정합성 , 확장성** 세 축을 동시에 만족하려면 동시성 설계가 필수입니다.

---

<aside>
⛵

**이번 주차 목표**

</aside>

- 멀티스레드 테스트로 **Race Condition / Deadlock 재현 로그 캡처**
- 트랜잭션 격리 수준 변경 후 **TPS & 이상 현상 비교 실험**
- Spring / Prisma 기반의 **비관적 락, 낙관적 락 구현 실습**
- 자동 재시도 로직 구현 (Spring Retry / p-retry)
- k6 기반 **부하 테스트 및 P95·P99 분석**
- **Deadlock 로그 분석** 및 innodb_lock_wait_timeout 튜닝 실습
- 상품 단위 샤딩, 리플리카 라우팅 등 **확장 전략 설계 인사이트 도출**

## 챕터1-3 : **동시성 문제 이해하기**

<aside>
💡

DB 이론에서 다루는 대표 동시성 문제 유형들

- **Lost Update (분실 갱신)**: 동시에 같은 데이터를 수정해, 하나의 작업 결과가 사라짐
- **Uncommitted Dependency (더티 리드)**: 커밋되지 않은 데이터를 읽어 잘못된 판단 유발
- **Inconsistency (불일치)**: 트랜잭션 중간에 다른 트랜잭션이 개입해 데이터 정합성 붕괴
</aside>

### 3.1 동시성 이슈 발생 배경

- 동시성 문제는 흔히 **여러 서버가 있어서 생기는 것**으로 오해하지만, 실제로는 **서버 1대, 파드 1개만 있어도 충분히 발생**함.
- 이유는 대부분의 웹 서버(Spring Boot, NestJS 등)는 **멀티스레드 or 비동기 처리 구조**를 갖고 있음.
- 즉, 하나의 서버 인스턴스 안에서도 **동시에 여러 요청을 처리**함.
- Spring 기반 애플리케이션은 Tomcat, Netty 같은 내장 WAS가 **요청당 별도 스레드 할당**함.
    
    NestJS도 비록 싱글 스레드 기반이지만 **이벤트 루프 + 워커 스레드 + DB 커넥션 풀** 구조로
    
    결국 **DB에는 동시에 수십~수백 개의 요청**이 날아감.
    
- 그 결과, 파드 1개만 실행했더라도 **여러 요청이 동시에 같은 데이터에 접근**하여 **Race Condition(경쟁 상태)**이나 **Deadlock(교착 상태)** 같은 동시성 문제가 발생함.

![[https://d2.naver.com/helloworld/1203723](https://d2.naver.com/helloworld/1203723)](Step%2005%20Concurrency%20Control/image.png)

[https://d2.naver.com/helloworld/1203723](https://d2.naver.com/helloworld/1203723)

### 3.2 Race Condition vs Deadlock

- **Race Condition (경쟁 상태)**
    - **정의:** 여러 트랜잭션이 동시에 같은 데이터를 처리하며,
        
        **수행 순서나 타이밍에 따라 결과가 달라지는 문제**를 의미함.
        
    - **발생 원인:** 상태를 읽고 난 후 상태를 변경하기까지 → **다른 요청이 중간에 값을 바꾸면 검증이 무효**가 됨
    - 트랜잭션 경계가 애매하거나, 락이 없을 때 잘 발생함
    - **해결 방법:**
        - 트랜잭션 경계 명확히 지정 (@Transactional)
        - DB 락 사용 (SELECT ... FOR UPDATE)
        - 낙관적 락 (@Version) 또는 조건부 UPDATE (WHERE version=?)
        - DB 유니크 제약 활용 (e.g. 쿠폰 중복 사용 방지)
    - 대표적인 문제 상황:
        
        ```
        Tx1: 상품 재고 1개 조회 → 구매 처리
        Tx2: 상품 재고 1개 조회 → 구매 처리
        → 두 트랜잭션 모두 stock=1을 읽고 각자 stock = stock - 1 실행 ⇒ 최종 -1
        ```
        

- **Deadlock (교착 상태)**
    - 정의: 두 개 이상의 트랜잭션이 서로 상대방의 락을 기다리며 무한 대기 상태에 빠지는 문제를 말함.
    - **발생 원인:**
        - 여러 자원에 락을 걸 때 **획득 순서가 일관되지 않을 경우**
        - 트랜잭션이 길어져 **락이 오래 유지될 경우**
    - **해결 방법:**
        - 락 획득 순서를 항상 고정함 (e.g. A → B 순서로만 처리)
        - 트랜잭션은 가능한 짧게 유지함
        - 재시도 로직 추가 (@Retryable)
        - DB의 Deadlock 감지 로직을 활용하고, 롤백 후 재시도 처리
    - 문제 상황:
        
        ```
        Tx1: A 자원 락 → B 자원 락 요청 (대기)
        Tx2: B 자원 락 → A 자원 락 요청 (대기)
        → 둘 다 상대가 풀어주기를 기다림 → 교착 상태
        ```
        
        ![Untitled](Step%2005%20Concurrency%20Control/Untitled.png)
        

- 요약비교
    
    
    | **항목** | **Race Condition** | **Deadlock** |
    | --- | --- | --- |
    | 발생 조건 | 동시에 같은 데이터에 접근 | 서로 락을 걸고 대기 |
    | 결과 | 데이터 정합성 깨짐 | 트랜잭션 정지, 에러 발생 |
    | 예방 방법 | 트랜잭션/락으로 원자성 확보 | 락 순서 통일, 재시도 처리 |
    | 관련 예시 | 재고 -1, 쿠폰 중복 사용 | 계좌이체에서 송신/수신 락 교차 |

## 챕터1-4 : **트랜잭션 격리 수준 짚어보기**

### 4.1 격리 수준이 중요한 이유

- 서비스에 트래픽이 몰리면, 동시에 여러 요청이 같은 데이터를 만짐.
- 이때 트랜잭션마다 격리 수준이 다르면 **데이터 정합성**이 깨질 수 있음.
- 격리를 낮추면 처리량(TPS)이 늘지만, 값이 엉킬 위험도 커짐.
- 격리를 높이면 데이터는 정확해지지만, 속도가 느려지고 Deadlock이 발생함.
- 특히 재고, 금액, 쿠폰 같은 **핵심 자원**은 정합성이 매우 중요함.
- 예: 재고가 5인데 두 사용자가 동시에 주문 → 재고 -1 문제 발생 가능함.

### 4.2 ANSI 4대 격리 수준 요약

- **이상 현상 정리 – 예시와 함께 쉽게 이해하기**
    - **Dirty Read (더티 리드)**
        - 설명: 아직 커밋되지 않은 데이터를 다른 트랜잭션이 읽어버리는 현상임.
        - 예시: 트랜잭션 A가 금액을 100 → 0으로 수정하고 아직 커밋하지 않았는데, 트랜잭션 B가 이 값을 읽어 “잔액 부족”으로 처리함. 그런데 나중에 A가 롤백되면, B는 존재하지 않는 정보를 기반으로 처리한 셈이 됨.
    - **Non-Repeatable Read (반복 불가능한 읽기)**
        - 설명: 같은 트랜잭션 내에서 같은 조건으로 두 번 읽었는데 값이 달라짐.
        - 예시: 트랜잭션 A가 제품 가격을 조회했을 때 10,000원이었는데, 트랜잭션 B가 그 사이에 가격을 8,000원으로 수정 후 커밋함. A가 다시 조회하니 8,000원이 나옴. 가격이 트랜잭션 중에 바뀐 것처럼 보이게 됨.
    - **Phantom Read (팬텀 리드)**
        - 설명: 같은 조건으로 조회했는데 처음엔 없던 행이 두 번째엔 생기거나, 반대로 사라지는 현상임.
        - 예시: 트랜잭션 A가 “서울 지역 주문”을 조회했더니 5건이 나왔는데, 트랜잭션 B가 새로운 서울 주문을 등록하고 커밋함. A가 같은 조건으로 다시 조회하니 6건이 나옴. 중간에 유령(팬텀) 같은 행이 튀어나온 셈임.

트랜잭션 격리는 총 네 단계로 나뉨. 단계가 낮을수록 처리량은 늘지만 데이터가 뒤틀릴 위험이 커짐. 단계가 높을수록 데이터는 안전해지지만 TPS가 줄고 Deadlock도 늘어남.

**1) READ UNCOMMITTED (RU)**

- 커밋되지 않은 변경도 읽을 수 있음 → Dirty Read 발생함.
- 같은 트랜잭션 안에서 다시 읽으면 값이 달라질 수 있음 → Non‑Repeatable Read 생김.
- 조건이 같은데 새 행이 튀어나옴 → Phantom Read도 허용됨.
- 속도가 가장 빠름. *모니터링·통계* 같이 약간의 오차를 감수할 때만 잠깐 씀.
- **운영 트랜잭션에서는 사실상 사용 X**

**2) READ COMMITTED (RC)**

- Dirty Read 막음. 커밋된 데이터만 보여 줌.
- Non‑Repeatable Read와 Phantom Read는 여전히 생길 수 있음.
- Oracle·PostgreSQL·SQL Server 기본값임. 대부분의 일반 업무가 이 수준이면 충분함.
- 빠른 응답이 필요하고, 트랜잭션 안에서 값이 조금 변해도 치명적이지 않을 때 적합함.

**3) REPEATABLE READ (RR)**

- 트랜잭션 내에서 읽은 행 값은 끝날 때까지 고정됨 → Non‑Repeatable Read 방지됨.
- 조건에 맞는 새 행이 뒤늦게 나타날 수 있음 → Phantom Read 발생 가능함.
- 재고·잔액처럼 **“읽고 바로 수정”** 하는 로직에 잘 맞음.

**4) SERIALIZABLE (SR)**

- 가장 엄격함. 모든 이상 현상(DIRTY/NON‑REPEATABLE/PHANTOM) 전부 차단함.
- 논리적으로 트랜잭션을 순차 실행한 것과 같은 결과를 보장함.
- SELECT조차 락을 걸어야 해서 TPS가 크게 줄고 Deadlock 가능성도 올라감.
- 은행 이체, 회계 마감 등 **1원 오차도 허용 못 하는** 상황에서만 선택함.

### 4.3 이상 현상 직접 보기 (예시 쿼리) 이상 현상 직접 보기 (예시 쿼리)

```
-- 터미널 1
START TRANSACTION;
SELECT stock FROM product WHERE id = 10;  -- 5 나옴
-- 터미널 2
UPDATE product SET stock = 0 WHERE id = 10; COMMIT;
-- 터미널 1
SELECT stock FROM product WHERE id = 10;  -- 5 또는 0 나옴 (격리 수준 따라 달라짐)
```

- READ COMMITTED이면 0 나옴 (최신값 반영됨)
- REPEATABLE READ이면 5 유지됨 (스냅샷 유지됨)
- SERIALIZABLE이면 동시 접근 자체가 차단됨

### 4.4 격리 수준 선택 기준

1. **정합성이 얼마나 중요한가?**
    - 돈, 재고, 쿠폰 등 정확해야 함 → RR 이상 써야 함.
    - 약간의 오차 허용됨 → RC만으로 충분함.
2. **경합이 얼마나 잦은가?**
    - 같은 데이터를 자주 건드림 → 비관적 락 + RC 조합 권장됨.
    - 거의 충돌 없음 → 낙관적 락 + RC 조합도 가능함.
3. **읽기/쓰기 비율은 어떤가?**
    - 읽기 90% 이상 → RC 수준으로도 커버 가능함. 캐시나 리플리카 활용 권장됨.
    - 쓰기도 많고 값이 민감함 → 높은 격리 수준 고려함.
4. **시스템 여유, TPS 요구사항 확인함**
    - TPS 중요하면 RC + 조건부 UPDATE로 타협함.
    - TPS보다 정합성이 우선이면 RR이나 SR도 고려함.

> Thumb Rule 요약
> 
> - Dirty Read만 막으면 RC 쓰면 됨.
> - 같은 데이터를 반복 조회한다면 RR 필요함.
> - 팬텀 리드까지 막고 싶으면 SR 고려함. 대신 TPS 감소 주의함.

### 4.5 코드스니펫 (환경별 적용 예시)

- **[코드스니펫] Spring Boot + JPA**
    
    ```java
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void decrease(Long id) {
        Product p = repo.findByIdForUpdate(id); // SELECT … FOR UPDATE 실행됨
        p.decrease();
        repo.save(p);
    }
    ```
    
    - `@Transactional`로 트랜잭션 전체를 감쌈.
    - `findByIdForUpdate`로 X-lock 획득.
- **[코드스니펫] NestJS + Prisma**
    
    ```tsx
    import { PrismaClient } from '@prisma/client';
    const prisma = new PrismaClient();
    
    await prisma.$transaction(async (tx) => {
      const user = await tx.user.findUnique({
        where: { id },
        select: { id: true, balance: true }
      });
    
      if (!user) throw new Error("User not found");
      if (user.balance < 1000) throw new Error("Insufficient balance");
    
      await tx.user.update({
        where: { id },
        data: { balance: { decrement: 1000 } }
      });
    }, {
      isolationLevel: 'Serializable'
    });
    
    ```
    

---

### 4.6 치트 시트

| 판단 질문 | RC | RR | SR |
| --- | --- | --- | --- |
| 금액·재고·쿠폰처럼 중요한 데이터? | △ | ◎ | ◎ |
| 로그·배치 통계용 데이터? | ◎ | △ | △ |
| Deadlock 위험 | 낮음 | 중간 | 높음 |
| TPS 요구량 | 높음 | 중간 | 낮음 |
| 실무 난이도 | 낮음 | 중간 | 높음 |

> ◎ = 적극 추천, △ = 상황 따라 가능함
> 

---

### 4.7 기억해야 할 핵심

- 트랜잭션 격리는 성능과 정합성의 밸런스를 맞추는 도구임.
- 기본값이 항상 맞는 건 아님. 상황 따라 다르게 설정해야 함.
- **@Transactional + 격리 수준 설정 + 락 전략**을 함께 사용해야 제대로 작동함.
- 실무에서는 격리 수준과 락만으로 안 될 땐 **DB 제약 조건, 멱등 처리, 중복 방지 토큰**까지 종합적으로 써야 함.

## 챕터1-5 : **데이터베이스 Lock 전략**

### 5.1 S-lock / X-lock 개념

![image.png](Step%2005%20Concurrency%20Control/image%201.png)

- **S-lock (Shared Lock, 공유 잠금)**
    - 여러 트랜잭션이 **동시에 읽기 전용** 작업을 할 때 사용되는 락임.
    - 예를 들어, 책을 여러 학생이 동시에 빌려서 읽기만 하는 상황과 같음.
    - 데이터를 변경하지 않는 `SELECT` 쿼리에서 `FOR SHARE` 옵션을 붙이면 공유 락이 걸림.
    - 이 상태에선 다른 트랜잭션도 계속 읽을 수 있지만, **수정은 모두 금지됨**.
- **X-lock (Exclusive Lock, 배타 잠금)**
    
    ![image.png](Step%2005%20Concurrency%20Control/image%202.png)
    
    - 트랜잭션이 데이터를 **변경하려 할 때** 걸리는 락임.
    - 누군가 책에 필기하거나 내용을 바꾸고 있다면, 다른 사람은 그 책을 읽거나 수정할 수 없다는 비유로 생각할 수 있음.
    - `SELECT ... FOR UPDATE`, `UPDATE`, `DELETE` 같은 쿼리에서 자동 적용됨.
- **락 충돌 정리**
    - **S vs S**: 둘 다 읽기만 하므로 충돌 없음.
    - **S vs X**: X가 들어오면 기다려야 함.
    - **X vs X**: 둘 중 하나가 먼저 끝날 때까지 기다려야 함.
- **정리**
    - DB에서 동시에 여러 작업이 들어올 때 **정합성**을 유지하기 위해 꼭 필요한 제어 장치임.
    - 성능과 정확성의 균형을 잡기 위한 핵심 개념이기도 함.

---

### 5.2 비관적 Lock (Pessimistic Lock)

![Untitled](Step%2005%20Concurrency%20Control/Untitled%201.png)

- **철학**: "충돌이 일어날 가능성이 있다면, 미리 잠그고 안전하게 작업하자."
- **작동 방식**
    - 트랜잭션 시작 시, 필요한 데이터에 **X-lock을 먼저 걸고 시작**함.
    - 다른 트랜잭션은 락이 해제될 때까지 해당 데이터에 접근 불가.
- **SQL 예시**:
    
    ```
    SELECT * FROM stock WHERE id = 1 FOR UPDATE;
    ```
    
    - 이 쿼리를 실행하면 `id = 1`인 행에 배타 락이 걸림.
- **ORM 예시**:
    - Spring JPA:
        
        ```
        @Lock(PESSIMISTIC_WRITE)
        Member findById(Long id);
        ```
        
    - Prisma:
        
        ```
        await prisma.$queryRaw`SELECT * FROM "Member" WHERE id = ${id} FOR UPDATE`;
        ```
        
- **장점**
    - 데이터 충돌 가능성이 완전히 차단됨.
    - 동시 수정 시에도 안전하게 순차 처리 가능.
- **단점**
    - 락 대기 시간으로 인해 전체 처리 속도가 느려질 수 있음.
    - Deadlock 발생 확률이 높아짐.
- **적용 예시**
    - 상품 재고 감소, 좌석 예약, 포인트 차감 등 **서로 동시에 처리되면 안 되는 경우**.
    - 특히 선착순이나 한정 수량 처리 로직에 적합함.

---

### 5.3 낙관적 Lock (Optimistic Lock)

![Untitled](Step%2005%20Concurrency%20Control/Untitled%202.png)

- **철학**: "웬만하면 충돌 안 나니까 그냥 진행하고, 나중에 문제가 생기면 그때 처리하자."
- **작동 원리**
    - 트랜잭션 동안 **락을 걸지 않음**.
    - 커밋 직전에, 이 데이터가 **다른 트랜잭션에 의해 수정되지 않았는지 검사**함.
- **방법 1: 버전 필드 사용**
    1. 테이블에 `version` 컬럼 추가함.
    2. 수정할 때 `WHERE id = ? AND version = ?` 조건을 붙임.
    3. 성공하면 `version`을 +1 증가시킴. 실패하면 예외 발생 → 재시도.
- **방법 2: 조건부 업데이트**
    
    ```
    UPDATE stock SET count = count - 1 WHERE id = 1 AND count > 0;
    ```
    
    - 조건이 맞는 경우에만 업데이트되므로, 충돌을 피해감.
- **장점**
    - 락을 걸지 않아 **TPS(초당 처리량)가 높음**.
    - Deadlock 위험 없음.
- **단점**
    - 충돌 시 재시도가 필요해 **성능이 불안정할 수 있음**.
    - 충돌이 잦은 환경에서는 오히려 비효율적일 수 있음.
- **적용 예시**
    - **데이터 충돌이 드문 경우** (ex: 마이페이지 수정, 프로필 업데이트)
    - **분산 시스템이나 서버가 여러 대일 때** (DB 락만으로 관리 어려움)

---

### 5.4 데드락 예방 & 재시도 패턴

> 데드락(Deadlock)이란?
> 
> 
> 트랜잭션 A는 B가 가진 락을 기다리고, B는 A가 가진 락을 기다리는 상황. → 둘 다 영원히 멈춤.
> 

**예방 및 대처 방법**

1. **락 순서 일관되게 잡기**
    - 모든 트랜잭션이 동일한 순서로 자원을 요청하도록 설계.
    - 예: "항상 사용자 → 상품 순으로 조회/수정"
2. **트랜잭션을 최대한 짧게 유지**
    - 처리 로직은 빠르게 끝내고, 외부 API 요청은 트랜잭션 밖으로 뺌.
3. **자동 재시도 로직 구현**
    - Java: `@Retryable`, NestJS/TS: `p-retry`, `prisma-retry` 등 사용.
    - 충돌 시 n회까지 재시도하고, 계속 실패하면 사용자에게 알림.
4. **DB 타임아웃 짧게 설정하기**
    - MySQL: `innodb_lock_wait_timeout`
    - PostgreSQL: `lock_timeout`
    - 일정 시간 지나면 에러로 돌리고 다음 트랜잭션 수행.
5. **문제 트랜잭션 모니터링**
    - DB의 데드락 로그, 느린 쿼리 로그를 정기적으로 분석함.
    - 문제가 반복되는 경우 쿼리 개선 또는 락 범위 조정.
6. **락 전략 혼합 사용**
    - 처음엔 낙관적 락으로 처리 → 충돌이 많아지면 비관적으로 전환.
    - 또는 리소스 별로 전략을 달리함 (예: 주문 = 비관적, 리뷰 작성 = 낙관적)

---

### 5.5 실전 판단 흐름 요약

**이 상황이라면 어떤 락을 써야 할까?**

- **충돌이 자주 일어나나?**
    - 그렇다면 **비관적 락**으로 미리 막는 게 안정적임.
- **TPS(초당 처리량)가 중요하나?**
    - 그렇다면 **낙관적 락**을 기본으로 쓰고, 충돌은 재시도로 대응함.
- **서버가 여러 대인가? (분산 환경)**
    - 단일 DB 락만으론 부족함 → **Redis Redlock** 같은 분산 락 도입 고려함.
- **실시간 장애나 지연이 발생하고 있나?**
    - 데드락 로그, 응답 지연 로그를 기반으로 **락 전략을 조정**해야 함.

> 실제로는 한 가지 전략만 쓰지 않고, 비관적 + 낙관적 혼합, 또는 락 + DB 무결성 제약 + 캐시까지 함께 고려해야 실무에 적합한 구조가 됨.
> 

## 챕터1-6 : **실무 사례 & 아키텍처 인사이트**

### 6.1 이커머스 재고 제어

**문제 배경**

- 쇼핑몰 특가·플래시세일 시 초당 수천 건의 요청이 몰림.
- 재고가 100개인데 105명이 결제 성공 → **Oversell 현상** 발생.

**DB 기반 대표 해결책 3종**

1. **조건부 UPDATE 단일 쿼리**

```sql
UPDATE product
SET stock = stock - 1
WHERE id = :pid AND stock >= 1;

```

- UPDATE 문 자체가 원자 연산이므로 다른 트랜잭션이 끼어들 수 없음.
- 영향 행 수 0 → 품절 처리. 영향 행 수 1 → 구매 성공.
- **장점**: 코드 간결, DB 하나로 충분.
- **단점**: 인기 상품 하나에 트래픽 집중 시 **DB 핫스팟** 가능.
1. **FOR UPDATE + 트랜잭션**

```sql
START TRANSACTION;
SELECT stock FROM product WHERE id = :pid FOR UPDATE;
-- 재고 검증 후 UPDATE
COMMIT;

```

- 조회 시점부터 락 보장. 다른 트랜잭션은 해당 행 접근 대기.
- **장점**: 동시 요청도 안정성 보장.
- **단점**: TPS 늘면 `innodb_lock_wait_timeout` 초과 → 지연 발생.
1. **낙관적 락 (version 컬럼 기반)**
- `product(version int)`
- UPDATE 시 조건: `WHERE id = :pid AND version = :ver`
- 성공 시 `version = version + 1` 갱신
- **장점**: 락 대기 없음. 성능 유리.
- **단점**: 충돌 시 재시도 필요. 충돌률 ↑일수록 비용 ↑.

**응답 처리 팁**

- 재시도 횟수는 2~3회 이내로 제한하고, 실패 시 사용자에 친절하게 “다시 시도해 주세요” 메시지 제공.

---

### 6.2 포인트·잔액 차감 흐름

**문제 예시**

- 동일 유저가 동시에 두 건 결제 요청.
- 각 요청이 5만 원씩 차감할 때, 초기 잔액 10만 원 → 최종 잔액이 -5만 원 되는 오류 발생.

**안정화 패턴 3가지**

1. **Row-Level X-Lock**

```sql
SELECT balance FROM wallet WHERE id = :uid FOR UPDATE;
-- 검증 및 차감

```

- 트랜잭션 안에서만 유효한 배타적 잠금.
- **장점**: 단순하고 안정적.
- **단점**: 병렬 처리 어려움.
1. **낙관적 락 기반 원장 방식**
- ledger 테이블에 `amount`, `version`, `type = CREDIT/DEBIT` 기록
- 잔액은 `SELECT SUM(amount) WHERE wallet_id = ?` 로 계산
- INSERT 시 버전 값 비교하여 낙관적 락 충돌 감지
- **장점**: 변경 이력 추적 가능. 회계 처리 적합.
- **단점**: 잔액 조회 시 비용 증가. 복잡한 트랜잭션 조합 필요.
1. **조건부 UPDATE**

```sql
UPDATE wallet
SET balance = balance - :amt
WHERE id = :uid AND balance >= :amt;

```

- 영향 행 수 0 → “잔액 부족” 처리
- **장점**: 빠름. 간단함.
- **단점**: 재시도 로직 필요.

**실무 적용 팁**

- 모바일 앱이나 간편결제 API 등에서는 **멱등 키**와 함께 조합하는 경우 많음.
- 트랜잭션은 반드시 **짧게 유지**. 외부 API 호출 포함 금지.

---

### 6.3 쿠폰·선착순 이벤트 처리

**문제 예시**

- “선착순 10,000명 쿠폰” 이벤트.
- 수만 명이 동시에 요청 → 중복 발급, 초과 발급 이슈 빈번.

**DB 기반 설계 3단계**

1. **쿠폰 수량 감소에 락 적용**

```sql
SELECT remain FROM coupon WHERE id = :cid FOR UPDATE;
-- remain > 0 조건 검사 후 감소

```

- 안전하지만 트래픽 많으면 지연.
1. **조건부 UPDATE**

```sql
UPDATE coupon SET remain = remain - 1
WHERE id = :cid AND remain > 0;

```

- 영향 행 수 0 → 품절 처리
- 이 방식이 가장 실무에서 많이 쓰임
1. **중복 발급 방지 → UNIQUE 제약**

```sql
CREATE UNIQUE INDEX ux_coupon ON coupon_usage(user_id, coupon_id);

```

- INSERT 실패 → 중복 사용 감지
- 트랜잭션 안에서 remain 감소 + 사용 기록 INSERT 함께 실행

**운영 체크리스트**

- **트랜잭션 격리 수준**은 기본 `READ COMMITTED` 유지, 필요 시 쿠폰 관련 쿼리만 `REPEATABLE READ`로 조정
- **성공 시점 명확히 정의**: 잔여 수량 감소 + 사용 이력 INSERT 완료 시점이 기준
- **시스템 에러 발생 시**: 롤백되는 트랜잭션 안에서 모든 작업 처리 권장

---

### 6.4 공통 인사이트 정리

1. **경합 자원 = 락 핵심 대상**
    - 재고, 잔액, 쿠폰 → 한 행이 많은 트랜잭션에 노출되므로 주의 필요
2. **격리 수준 최소화**
    - 시스템 전체를 SERIALIZABLE로 잡지 말고, 꼭 필요한 영역만 SELECT FOR UPDATE or 낙관적 락
3. **단일 쿼리 UPDATE 패턴** 활용
    - 조건부 UPDATE로 트랜잭션을 단축하면 TPS 효율 개선
4. **트랜잭션은 짧게, 명확하게**
    - 외부 API, 파일 I/O 등 포함 시 성능 저하 + Deadlock 유발 위험
5. **실험과 재현이 중요**
    - JMeter, **locust**로 동시성 부하 시나리오 작성하여 재현 테스트 필수

## 챕터1-7 : **실습: 주문 API 동시성 처리**

### 7.1 시나리오 & DB 스키마

```
product(id PK, stock INT, price INT, version INT)
wallet(id PK, balance INT)
order(id PK, user_id, product_id, amount)

```

1. 사용자가 상품 주문 요청 →
2. 잔액 확인 후 차감 →
3. 재고 확인 후 감소 →
4. 주문 레코드 생성 → 커밋.

---

### 7.2 AS‑IS 문제 코드 분석 (Spring Boot JPA)

```java
public Order placeOrder(Long userId, Long productId) {
    Wallet w = walletRepo.findById(userId).orElseThrow();
    Product p = productRepo.findById(productId).orElseThrow();

    if (w.getBalance() < p.getPrice() || p.getStock() <= 0)
        throw new IllegalStateException();

    w.setBalance(w.getBalance() - p.getPrice());
    p.setStock(p.getStock() - 1);

    walletRepo.save(w);  // ▲ 트랜잭션 없음
    productRepo.save(p); // ▲ 트랜잭션 없음

    Order order = new Order(userId, productId, p.getPrice());
    return orderRepo.save(order);
}
```

- **문제 1**: 트랜잭션 범위 없음 → 3개 UPDATE 중 1개만 성공 가능.
- **문제 2**: 다른 스레드가 `p.getStock()`을 읽고 동시에 차감 → Oversell.
- **문제 3**: 동시 요청 시 `w.getBalance()` 두 번 차감 → 음수 가능.

멀티 스레드 테스트(JUnit 5) 실행 시, 재고 100 → 결과 98\~102 불일치 발생.

---

### 7.3 TO‑BE 설계 (Spring Boot + JPA, 비관적 락)

```java
@Service
public class OrderService {

    // 트랜잭션 전체에 REPEATABLE_READ 격리 수준 적용
    // → 트랜잭션 내 조회된 데이터는 트랜잭션이 끝날 때까지 값이 고정됨 (스냅샷 유지)
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Order placeOrder(Long userId, Long productId) {

        // 상품 정보 조회 시 FOR UPDATE 락 걸기
        // → 해당 product 행에 X-Lock (배타 락) 발생
        // → 다른 트랜잭션은 이 행에 접근(읽기/쓰기)할 수 없음 → Oversell 방지
        Product p = productRepo.findByIdForUpdate(productId)
                               .orElseThrow();

        // 유저 지갑 정보 조회 시에도 FOR UPDATE 적용
        // → 동시에 다른 결제 트랜잭션이 이 유저의 지갑을 수정하지 못하도록 막음
        Wallet w = walletRepo.findByIdForUpdate(userId)
                              .orElseThrow();

        // 검증 로직 1: 잔액 부족한 경우 예외 발생
        if (w.getBalance() < p.getPrice()) throw new InsufficientFunds();

        // 검증 로직 2: 재고가 0 이하인 경우 예외 발생
        if (p.getStock() <= 0)             throw new SoldOut();

        // 도메인 로직: 잔액 차감
        w.decrease(p.getPrice());

        // 도메인 로직: 재고 차감
        p.decreaseStock(1);

        // 주문 객체 생성 및 저장
        Order order = new Order(userId, productId, p.getPrice());
        orderRepo.save(order);

        // 트랜잭션 커밋 시점에 DB 반영됨
        return order;
    }
}
```

- **핵심**: `findByIdForUpdate()` 가 **SELECT … FOR UPDATE** 실행 → 행 X‑Lock.
- **격리 수준**: `REPEATABLE_READ` 로 한 트랜잭션 내 스냅샷 고정.
- **검증과 갱신** 한 번에 이루어져 레이스 조건 차단.

### 동시성 테스트

```java
@Test
void 동시_100건_주문() throws Exception {
    // 1. 동시에 요청을 날릴 스레드 수 정의 (100개 주문 요청을 동시에 실행)
    int thread = 100;

    // 2. 고정된 크기의 스레드 풀 생성 (병렬 실행을 위한 Executor)
    ExecutorService es = Executors.newFixedThreadPool(thread);

    // 3. 모든 스레드 작업이 완료될 때까지 대기할 CountDownLatch
    CountDownLatch latch = new CountDownLatch(thread);

    // 4. 100개의 주문 요청을 비동기로 실행
    for (int i = 0; i < thread; i++) {
        es.submit(() -> {
            try {
                // 상품 ID 1번, 사용자 ID 1번에 대해 주문 요청
                // → 동시에 실행되므로 Race Condition 또는 Deadlock 가능성 테스트
                orderSvc.placeOrder(1L, 1L);
            } catch (Exception ignored) {
                // 에러 발생 시 테스트 실패가 아님 → 실패 주문은 무시 (예: 잔액 부족, 재고 없음 등)
            } finally {
                // 스레드 하나가 끝날 때마다 카운트 감소
                latch.countDown();
            }
        });
    }

    // 5. 모든 요청이 완료될 때까지 대기 (latch가 0이 될 때까지)
    latch.await();

    // 6. 최종 재고 상태 출력
    // AS-IS (트랜잭션 없음): 예상 결과는 stock != 0 → 정합성 깨짐
    // TO-BE (락 적용): stock = 0 → 모든 요청이 안전하게 처리됨
    System.out.println("남은 재고: " + productRepo.findById(1L).get().getStock());
}
```

결과: 재고 정확히 0, 잔액 계산 일치, 예외 없는지 확인.

### NestJS

- TO‑BE 설계 (NestJS + Prisma, 낙관적 락)

```tsx
// schema.prisma
model Product {
  id      Int  @id                     // 상품 ID
  stock   Int                          // 현재 재고 수량
  price   Int                          // 가격
  version Int  @default(1)             // 낙관적 락용 버전 필드

  @@unique([id, version])              // 낙관적 락을 위한 복합 유니크 제약 조건
}

// order.service.ts
async placeOrder(uid: number, pid: number) {
  // Prisma 트랜잭션 실행
  // → 모든 쿼리는 하나의 트랜잭션 내에서 실행되며, isolationLevel 지정 가능
  await this.prisma.$transaction(async (tx) => {

    // 1. 상품 정보 조회 (version 포함)
    const product = await tx.product.findUnique({ where: { id: pid } });

    // 2. 유저 지갑 정보 조회
    const wallet  = await tx.wallet.findUnique({ where: { id: uid } });

    // 3. 유효성 검사: 재고 없음 → 예외 발생
    if (!product || product.stock <= 0)
      throw new Error('SOLD_OUT');

    // 4. 유효성 검사: 잔액 부족 → 예외 발생
    if (!wallet || wallet.balance < product.price)
      throw new Error('NO_MONEY');

    // 5. 낙관적 락 조건부 업데이트
    // - stock ≥ 1 확인
    // - version 일치 조건 확인
    // 성공 시 stock -1, version +1 → 다른 트랜잭션이 이미 수정했다면 실패
    const updated = await tx.product.updateMany({
      where: {
        id: pid,
        version: product.version,
        stock: { gte: 1 }, // 재고 조건 함께 체크
      },
      data: {
        stock:   { decrement: 1 },
        version: { increment: 1 },
      },
    });

    // 6. 낙관적 락 실패 시 → 아무 행도 업데이트되지 않음 (충돌 발생)
    if (updated.count !== 1)
      throw new Error('CONFLICT');

    // 7. 유저 지갑에서 잔액 차감
    await tx.wallet.update({
      where: { id: uid },
      data: { balance: { decrement: product.price } },
    });

    // 8. 주문 기록 저장
    await tx.order.create({
      data: {
        userId: uid,
        productId: pid,
        amount: product.price,
      },
    });

  }, {
    // 트랜잭션 격리 수준을 SERIALIZABLE로 지정
    // → 다른 트랜잭션의 간섭을 방지 (NestJS에서는 기본값이 보장 안 될 수 있어 명시적으로 지정)
    isolationLevel: 'Serializable'
  });
}
```

- **version** 복합 PK (`@@id([id, version])`) 혹은 Unique 인덱스 필요.
- `id_version` 기준 UPDATE 실패 → **OptimisticLockError** 역할.
- 서비스 레이어에서 최대 3회 재시도(`p-retry`) 로 충돌 완화.

### 동시성 테스트 (Jest)

```tsx
it('100 concurrent orders, stock exactly 0', async () => {
  // 1. 100개의 주문 요청을 동시에 보내기 위한 Promise 배열 생성
  // - userId = 1, productId = 1
  // - 예외 발생 시 catch → null로 무시 (테스트 실패 방지용)
  const promises = [...Array(100)].map(() =>
    svc.placeOrder(1, 1).catch(() => null)
  );

  // 2. 모든 요청을 동시에 실행하고 대기
  await Promise.all(promises);

  // 3. 트랜잭션 종료 후 product 재고를 조회
  const product = await prisma.product.findUnique({ where: { id: 1 } });

  // 4. 기대 결과: 재고가 정확히 0이어야 함
  // - 낙관적 락으로 인해 동시에 stock=1 조건을 충족하는 트랜잭션은 단 하나만 성공
  // - 실패한 요청은 CONFLICT 예외로 종료됨
  expect(product?.stock).toBe(0);
});
```

결과: stock = 0, 실패 요청은 ‘CONFLICT’ 에러로 회수 → 재시도 로직 포함 시 모두 성공 가능.

---

### 7.5 테스트 패턴 비교 & 선택 가이드

| 전략 | 장점 | 단점 | 적합 환경 |
| --- | --- | --- | --- |
| **비관적(X‑Lock)** | 충돌 완전 차단, 구현 단순 | TPS↓, 데드락↑ | 충돌 빈도 높음, DB 단일 노드 서비스 |
| **낙관적(Version)** | 락 대기 0, TPS↑ | 재시도 비용, 충돌률 높으면 불리 | 충돌 드뭄, 분산·샤딩 구조 |

## 챕터1-8 : 과제

### `필수 과제`

1. Concurrency 
- 진행중인 시나리오내에서 발생할 수 있는 동시성 이슈를 식별
- **“ e-커머스 상품 주문 서비스”**
    - **예상 동시성 이슈**
        - 다수 사용자가 동시에 같은 상품을 주문 → **재고 oversell**
        - 동일 유저가 두 번 결제 요청 → **잔액 음수 오류**
        - 선착순 쿠폰 발급 요청 몰림 → **쿠폰 초과 발급 / 중복 발급**
    - **구현 및 보고서 요구사항**
        - 다음 항목 중 2개 이상 구현 및 테스트
            - 재고 감소 동시성 제어
            - 잔액 차감 동시성 제어
            - 쿠폰 발급 동시성 제어
        - **조건부 UPDATE / SELECT FOR UPDATE / 낙관적 락** 중 하나 이상 사용
        - **멀티스레드 테스트** 작성
        - md에 문제 상황, 해결 전략, 테스트 결과를 정리
- **“콘서트 예약 서비스”**
    - **예상 동시성 이슈**
        - 같은 좌석에 대해 동시에 예약 요청 → **중복 예약 발생**
        - 잔액 차감 중 충돌 발생 → **음수 잔액**
        - 예약 후 결제 지연 → **임시 배정 해제 로직 부정확**
    - **구현 및 보고서 요구사항**
        - 다음 항목 중 2개 이상 구현 및 테스트
            - 좌석 임시 배정 시 락 제어
            - 잔액 차감 동시성 제어
            - 배정 타임아웃 해제 스케줄러 + 테스트
        - **조건부 UPDATE / SELECT FOR UPDATE / 낙관적 락** 중 하나 이상 사용
        - **멀티스레드 테스트** 작성
        - md에 문제 상황, 해결 전략, 테스트 결과를 정리
    
1. Finalize
- 지금까지 구현한 API를 기반으로 **서비스 전체가 실제 운영 환경에서도 안정적으로 동작할 수 있도록 마무리 작업을 수행**합니다.
- 모든 기능이 **정상 동작**하고, **예외 상황에 유연하게 대응**할 수 있어야 하며, **테스트를 통해 안정성**을 검증해야 합니다.
- 각 기능에 대해 **테스트 케이스 1개 이상** 작성 필수

<aside>
<img src="https://www.notion.so/icons/light-bulb_red.svg" alt="https://www.notion.so/icons/light-bulb_red.svg" width="40px" />

**참고 PR 링크**

| 스택 | STEP5 |
| --- | --- |
| Java | [https://github.com/BEpaul/hhplus-e-commerce/pull/32](https://github.com/BEpaul/hhplus-e-commerce/pull/32) |
| Java | [https://github.com/juny0955/hhplus-concert/pull/25](https://github.com/juny0955/hhplus-concert/pull/25) |
| Kotlin | [https://github.com/chapakook/kotlin-ecommerce/pull/34](https://github.com/chapakook/kotlin-ecommerce/pull/34) |
| Kotlin | [https://github.com/psh10066/hhplus-server-concert/pull/21](https://github.com/psh10066/hhplus-server-concert/pull/21) |
| TS | [https://github.com/psyoongsc/hhplus-ts-server/pull/41](https://github.com/psyoongsc/hhplus-ts-server/pull/41) |
| TS | [https://github.com/suji6707/nestjs-case-02-ticketing/pull/5](https://github.com/suji6707/nestjs-case-02-ticketing/pull/5) |
</aside>

## 챕터1-9 : 서버 구축 마무리

### **1.시스템 구성은 상황에 따라 달라진다**

- 어떤 기능을 어떤 방식으로 구현할지는 **인프라 환경, 시간, 비용 등 현실적인 제약**에 따라 달라집니다.
- 이상적인 아키텍처가 항상 현실적인 선택은 아닙니다. **“왜 이렇게 구성했는가?”**에 대한 맥락 설명이 중요합니다.

---

### **2.기술은 빠르게 바뀐다**

- 우리가 “정답”이라 생각했던 기술 스택이나 구조도 몇 년 지나면 구식이 됩니다.
- 최신 트렌드를 무작정 따라가기보다는, **기술의 본질과 선택 이유**를 이해하는 태도가 더 중요합니다.

---

### **3.인프라는 더 이상 남의 일이 아니다**

- 물론 인프라팀이 메인으로 운영하겠지만, **성능 측정**이나 **리소스 할당에 대한 판단**은 백엔드 개발자의 몫이 되기도 합니다.
- 예를 들어, 병목이 어디에서 발생하는지 확인하고 적절한 인프라 자원을 요청할 수 있어야 합니다.

---

### **4.문서화와 공유는 유지보수의 기본**

- 코드를 잘 짜는 것도 중요하지만, **의도와 맥락을 문서화하는 일**이 앞으로 더 중요해집니다.
- 프로젝트가 커질수록, 팀원이 많아질수록 **문서의 유무가 생산성을 좌우**합니다.

---

### **5.AI 코딩 시대, 우리는 팀장이 되어야 한다**

- 코파일럿, ChatGPT, CodeWhisperer 등 **AI가 코드 작성 보조를 넘어 중요한 팀원**이 되어가고 있습니다.
- 단순히 코드를 치는 능력보다는, **시스템을 설계하고 AI를 잘 활용할 수 있는 능력**이 중요해집니다.
- 즉, “팀장처럼 생각하고, AI를 팀원처럼 사용하는 시대”가 오고 있습니다.

---

### **🚀 마무리하며**

서버 구축은 단지 코드를 배포하는 일이 아니라, **전체 시스템을 설계하고 운영하는 종합적인 과정**입니다.

이제 여러분은 하나의 기능을 넘어서, **서비스 전체의 흐름과 구조를 설계할 수 있는 시야를 갖춘 개발자**가 되어야 합니다.

---

Copyright ⓒ TeamSparta All rights reserved.