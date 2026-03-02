# Step 09. Large-Scale Expansion Design

## 챕터1-1 : 들어가기에 앞서

<aside>
🔄 Summary : 지난 챕터 돌아보기

</aside>

- Summary 지난 챕터 돌아보기
    
    ![슬라이드1.PNG](Step%2009%20Large-Scale%20Expansion%20Design/slide1.png)
    
    ![슬라이드2.PNG](Step%2009%20Large-Scale%20Expansion%20Design/slide2.png)
    
    <aside>
    🚧 **트랜잭션과 관심사 분리 알고가기**
    
    </aside>
    
    ```jsx
    class OrderPaymentService {
    	Transaction {
    		fun 주문_결제() {
    			유저_포인트_차감();
    			결제_정보_저장();
    			주문_상태_변경();
    			주문_정보_전달();
    		}
    	}
    }
    ```
    
    - 주문 결제 서비스의 책임은 ****유저 포인트 차감, 결제 정보 저장, 주문 상태 변경**으로 결제 처리를 진행하는 것
    - 주문 정보 전달 **이 어떤 이유에 의해 오래 걸릴 경우 전체 트랜잭션에 영향을 끼침
    - 주문 정보 전달 이 실패할 경우 결제 처리 전체가 실패하게 됨
    
    > 주문 정보의 전달은 **부가 로직**이므로 **핵심 로직**인 결제 처리에 영향을 끼치면 안됩니다.
    > 
    
    ### 이벤트를 활용하여 개선한 코드
    
    ```java
    class OrderPaymentService {
    	Transaction {
    		fun 주문_결제() {
    			유저_포인트_차감();
    			결제_정보_저장();
    			주문_상태_변경();
    			
    			결제_완료_이벤트_발행();			
    		}
    	}
    }
    
    class OrderPaymentEventListener {
    
    	TransactionalEventListener(AFTER_COMMIT) {
    		Async {
    			fun 주문_정보_전달(결제_완료_이벤트)
    		}
    	}
    	
    	TransactionalEventListener(AFTER_COMMIT) {
    		Async {
    			fun 결제_완료_알림톡_발송(결제_완료_이벤트)
    		}
    	}
    }
    ```
    
    - 어플리케이션에서 트랜잭션을 쉽게 분리할 수도 있고, 비즈니스 로직의 코드 결합도가 확 낮춰진게 너무 좋지 않나요?
    
    <aside>
    ✂️ 분산 트랜잭션의 한계와 극복
    
    </aside>
    
    관심사 & 책임 분리를 통해 작은 단위의 트랜잭션 & Lock을 설계할 수 있습니다. 다만 이 작업이 어려운 이유는 아래와 같은데요. 
    
    1. **분산된 트랜잭션을 동기화하기 어려움**
        
        > **[ 고민 포인트 ]** 동시에 수행되어야 할 트랜잭션 중 하나가 실패한 경우 모두를 롤백시킬 방법 ?  ( keyword : 보상 트랜잭션, 2PC, SAGA 패턴 )
        > 
        - [AWS Summit Seoul 2023 - 12가지 디자인 패턴으로 알아보는 클라우드 네이티브 마이크로서비스 아키텍처](https://www.youtube.com/watch?v=8OFTB57G9IU&t=1856s)
        - [토스ㅣSLASH 24 - 보상 트랜잭션으로 분산 환경에서도 안전하게 환전하기](https://www.youtube.com/watch?v=xpwRTu47fqY)
        
    2. **비동기 처리로 인한 요청-응답 불일치 문제**
        
        <aside>
        💡
        
        쿠폰 발급 서비스의 부하를 줄이는 목적으로 발급 처리를 별도의 스레드에서 처리할 수 있도록 설계를 해본다면?
        
        </aside>
        
        ```mermaid
        sequenceDiagram
            participant Client
            participant CouponService
            participant Executor as ThreadExecutor
            participant Worker as Coupon Worker
            
            Client->>CouponService: 1. 쿠폰 발급 요청
            
            CouponService->>Executor: 3. 쿠폰 발급 작업 제출 (submit)
            Executor->>Worker: 4. 작업 실행 (스레드 할당)
            
            CouponService-->>Client: 5. 즉시 응답 (요청 ID 반환)
        		
        		Note over Worker: CouponWorker에서 비동기 처리
        		
        		opt 클라이언트가 상태 확인
                Client->>CouponService: 6. 쿠폰 발급 상태 확인 요청
                CouponService-->>Database: 7. 현재 처리 상태 확인
                CouponService-->>Client: 8. 쿠폰 상태 전달
            end
        ```
        
        일반적인 API의 응답과 다른 점은 쿠폰 요청의 응답은 쿠폰의 “**발급 성공 여부”**가 아닌 “**발급 요청 ID**”라는 점입니다. 고객은 쿠폰의 발급 여부를 확인하기 위해 추가적으로 쿠폰 발급 상태를 확인하는 요청을 통해 결과를 확인해야하는 부가적인 작업이 필요합니다.
        
    
    ### 마치며..
    
    트랜잭션이 분리되었을때 고민해야하는 문제들과 해결과정들을 학습해 봤어요. 분산 트랜잭션과 해결과정은 원래도 어려운 주제이니까 모든 내용을 마스터하지 못했더라도 괜찮아요. 일주일만에 학습하기에는 너무 어려우니 정확하게 이해하지 못했다면 차근차근 다시 공부해보면 좋겠습니다.
    

## 챕터1-2 : 이번 강의에서 배울 것

<aside>
⛵ **이번 챕터 목표**

</aside>

![슬라이드3.PNG](Step%2009%20Large-Scale%20Expansion%20Design/slide3.png)

- 카프카란 무엇인지, 왜 대량의 트래픽을 처리하는 서비스에서 사용하고 있는지 알아봅니다.
- 카프카를 활용해서 이벤트를 서비스 단위로 확장하고, 안정적인 이벤트 처리를 위한 방법을 알아봅니다.

## 챕터1-3 : Kafka 알아보기 (1)

<aside>
🚩 **What to do: 이번 주에 해야 할 것. 이것만 집중하세요!**

</aside>

![슬라이드5.PNG](Step%2009%20Large-Scale%20Expansion%20Design/slide5.png)

<aside>
💡 요즘 왜 다들 **카프카, 카프카** 하는 거지?

- 대규모 실시간 데이터 스트리밍을 위한 **분산 메세징 시스템**
- 높은 처리량 및 개발 효율을 위한 분산 시스템에서 **고가용성과 유연함**을 갖춘 연계시스템이 필요

</aside>

<aside>
🫠 기술면접에서 이런 식으로 물어볼 수 있습니다.

- Kafka에서 메시지 순서 보장과 처리량을 동시에 확보하기 위해 어떻게 설계해야 하나요?

답변 예시: 순서 보장의 기준이 되는 값을 **메시지 키**로 설정하고, 토픽의 **파티션 수**를 늘려 병렬 처리를 극대화하는 것이 메시지 순서와 처리량을 동시에 확보하는 핵심 설계 전략입니다.

- Kafka를 활용한 시스템에서 장애나 메시지 유실 방지를 위해 어떤 전략을 사용하나요?

답변 예시: Kafka는 **Replication** 기능을 기본적으로 제공하여 특정 브로커에 장애가 생겨도 다른 브로커가 장애가 발생한 브로커의 역할을 즉시 대신할 수 있도록 하는 매커니즘이 적용되어 있습니다. 또한, **Offset, Commit**개념을 통해 컨슈머가 메시지를 **‘최소 한 번 이상 처리(At-Least-Once)’**할 수 있도록 하며 메시지를 처리하는데 문제가 계속해서 발생하는 경우 **DLQ**를 활용하여 Fallback 프로세스를 구성할 수 있습니다.

</aside>

## 카프카 Overview

![Untitled](Step%2009%20Large-Scale%20Expansion%20Design/Untitled.png)

1. **Producer & Consumer**
    
    ![슬라이드6.PNG](Step%2009%20Large-Scale%20Expansion%20Design/slide6.png)
    
    - `Producer` - 메세지를 카프카 브로커에 적재(발행)하는 서비스
    - `Consumer` - 카프카 브로커에 적재된 메시지를 읽어오는(소비) 서비스
        - 메세지를 읽을 때마다 파티션 별로 offset을 유지해 처리했던 메세지의 위치를 추적
        - `CURRENT-OFFSET`
            
            컨슈머가 어디까지 처리했는지를 나타내는 offset 이며,  동일한 메세지를 재처리하지 않고, 처리하지 않은 메세지를 건너뛰지 않기위해 마지막까지 처리한 offset을 저장(커밋)해야함
            
        - 만약 오류가 발생하거나 문제가 발생할 경우, 컨슈머 그룹 차원에서 `--reset-offsets`  옵션을 통해 특정시점으로 offset을 되돌릴 수 있음
2. **Broker**
    
    ![슬라이드7.PNG](Step%2009%20Large-Scale%20Expansion%20Design/slide7.png)
    
    - 카프카 서버 Unit
    - Producer의 메세지를 받아 offset 지정 후 디스크에 저장
    - Consumer의 파티션 Read에 응답해 디스크의 메세지 전송
    - 카프카 클러스터 내에서 각 1개씩 존재하는 특별한 역할을 하는 브로커가 있음
        - **Controller**: 다른 브로커를 모니터링하고, 장애가 발생한 브로커에 특정 토픽의 Leader 파티션이 존재할 경우, 다른 브로커의 파티션 중 하나를 Leader로 재분배하는 역할을 수행
        - **Coordinator**: 컨슈머 그룹을 모니터링하고, 해당 그룹 내 특정 컨슈머가 장애로 인해 매칭된 파티션의 메시지를 소비할 수 없는 경우, 해당 파티션을 다른 컨슈머에게 매칭해주는 역할(Rebalance)을 수행
    - **Bootstrap Servers**: 클라이언트가 카프카 클러스터에 처음 연결할 때 사용하는 진입점이 되는 브로커들
        - 이를 통해 클라이언트는 전체 클러스터의 메타데이터를 얻고 다른 브로커에도 연결을 하게 됨
3. **Message**
    
    ![슬라이드8.PNG](Step%2009%20Large-Scale%20Expansion%20Design/slide8.png)
    
    - 카프카에서 취급하는 데이터의 단위로 <`Key` , `Message`> 형태로 구성
4. **Topic & Partition**
    
    ![슬라이드9.PNG](Step%2009%20Large-Scale%20Expansion%20Design/slide9.png)
    
    ![Untitled](Step%2009%20Large-Scale%20Expansion%20Design/Untitled%201.png)
    
    - `Topic` 은 메세지를 분류하는 기준이며 N개의 `Partition` 으로 구성
    - `Partition` 에 발행된 순서대로 컨슘함으로써 순차처리를 보장
        - 대용량 트래픽을 **파티션의 개수만큼 병렬로 처리**할 수 있어 빠른 처리 가능
        - 전체 메세지의 발행 순차처리를 보장하지 않지만, **같은 파티션의 메세지에 대해서는 순차처리**를 보장
        - 동시성 제어의 개념을 생각했을때, 동시에 처리되면 안되는 자원의 Id 등을 메세지의 키로 설정하면 순차처리가 보장되어야 하는 케이스는 보장도 되면서 병렬 처리로 높은 처리량을 보장한 하이브리드
    - `Producer` 에서 메세지를 발행할 때, 저장될 `Partition` 을 결정하기 위해 메세지의 **키 해시**를 활용하며, 키가 존재하지 않을 경우 균형 제어를 위해 Round-Robin 방식으로 메세지를 기록
        
        ```jsx
        key: "842"
        hash: 55478 // "842".hashCode()
        partitionCnt: 3
        
        targetPartition: 2 // 55478 % 3
        ```
        
        - **Partitioner**
            
            메세지를 발행할 때, 토픽의 어떤 파티션에 저장될 지 결정하며 Producer 측에서 결정. 특정  메세지에 키가 존재한다면 키의 해시 값에 매칭되는 파티션에 데이터를 전송함으로써 키가 같은 메시지를 다건 발행하더라도, 항상 같은 파티션에 메세지를 적재해 **처리순서를 보장**할 수 있음.
            
    - 한 `Partition` 은 하나의 컨슈머에서만 컨슘할 수 있음
        - 하나의 파티션에 여러개의 컨슈머가 메시지를 컨슘하면 메시지 처리의 순서를 보장할 수 없게 됨
5. **Consumer Group**
    
    ![슬라이드10.PNG](Step%2009%20Large-Scale%20Expansion%20Design/slide10.png)
    
    - 하나의 토픽에 발행된 메세지를 여러 서비스가 컨슘하기 위해 그룹을 설정
        - 하나의 주문완료 메세지를 결제서비스에서도, 상품서비스에서도 컨슘
    - 보통 소비 주체인 Application 단위로 Consumer Group 을 생성, 관리함
    - 같은 토픽에 대한 소비주체를 늘리고 싶다면, 별도의 컨슈머 그룹을 만들어 토픽을 구독
        
        ![Untitled](Step%2009%20Large-Scale%20Expansion%20Design/Untitled%202.png)
        
    
    > 파티션의 개수가 그룹 내 컨슈머 개수보다 많다면 잉여 파티션의 경우 메세지가 소비될 수 없음을 의미함
    > 
    - **( 참고 )** 토픽의 Partition 개수와 Consumer 개수에 따른 소비
        
        ![Untitled](Step%2009%20Large-Scale%20Expansion%20Design/Untitled%203.png)
        
        ![Untitled](Step%2009%20Large-Scale%20Expansion%20Design/Untitled%204.png)
        
        ![Untitled](Step%2009%20Large-Scale%20Expansion%20Design/Untitled%205.png)
        

## 챕터1-4 : Kafka 알아보기 (2)

1. **Rebalancing**
    
    ![슬라이드11.PNG](Step%2009%20Large-Scale%20Expansion%20Design/slide11.png)
    
    - Consmuer Group 의 **가용성과 확장성**을 확보해주는 개념
    - 특정 컨슈머로부터 다른 컨슈머로 파티션의 소유권을 이전시키는 행위
        
        e.g. `Consumer Group` 내에 Consumer 가 추가될 경우, 특정 파티션의 소유권을 이전시키거나 오류가 생긴 Consumer 로부터 소유권을 회수해 다른 Consumer 에 배정함
        
    
    <aside>
    🚫 **( 주의 )** 리밸런싱 중에는 컨슈머가 메세지를 읽을 수 없음.
    
    </aside>
    
    `Rebalancing Case`
    
    1. Consumer Group 내에 새로운 Consumer 추가
    2. Consumer Group 내의 특정 Consumer 장애로 소비 중단
    3. Topic 내에 새로운 Partition 추가
2. **Cluster**
    
    ![슬라이드12.PNG](Step%2009%20Large-Scale%20Expansion%20Design/slide12.png)
    
    - 고가용성 (HA) 를 위해 여러 서버를 묶어 특정 서버의 장애를 극복할 수 있도록 구성
    - Broker 가 증가할 수록 메시지 수신, 전달 처리량을 분산시킬 수 있으므로 확장에 유리
        
        > 동작중인 다른 Broker 에 영향 없이 확장이 가능하므로, 트래픽 양의 증가에 따른 브로커 증설이 손쉽게 가능
        > 
        
3. **Replication**
    
    ![슬라이드13.PNG](Step%2009%20Large-Scale%20Expansion%20Design/slide13.png)
    
    - Cluster 의 가용성을 보장하는 개념
    - 각 Partition 의 Replica 를 만들어 백업 및 장애 극복
        - Leader Replica
            
            각 파티션은 1개의 리더 Replica를 가진다. 모든 Producer, Consumer 요청은 리더를 통해 처리되게 하여 일관성을 보장함
            
        - Follower Replica
            
            각 파티션의 리더를 제외한 Replica 이며 단순히 리더의 메세지를 복제해 백업함. 만일, 파티션의 리더가 중단되는 경우 팔로워 중 하나를 새로운 리더로 선출함
            
            > Leader 의 메세지가 동기화되지 않은 Replica 는 Leader 로 선출될 수 없음.
            > 
            
4. [더 알아보기] Kafka 배포 모드: **ZooKeeper vs KRaft**
    
    ![슬라이드15.PNG](Step%2009%20Large-Scale%20Expansion%20Design/55bc0ae3-8694-4137-a711-16e99ef97156.png)
    
    - ZooKeeper 모드: 카프카 초기 버전부터 사용된 방식
        - 클러스터의 메타데이터 관리와 컨트롤러 선출 등에 ZooKeeper를 사용
        - 별도의 ZooKeeper 클러스터 구성 및 관리가 필요
        
        → 아직까지 대부분의 팀들이 사용하고 있는 방식
        
    - KRaft (Kafka Raft) 모드: 카프카 2.8 버전부터 도입된 새로운 메타데이터 관리 방식
        - ZooKeeper 없이 카프카 자체적으로 Raft 합의 알고리즘을 사용하여 메타데이터를 관리
        - 배포 및 운영의 복잡성을 줄이고 불필요한 성능 낭비를 줄임
        
        → 안정화 단계에 접어든지 얼마 안돼서 아직 도입한 조직이 많이 없음
        

1. [더 알아보기] Kafka 배포판: **Apache Kafka vs Confluent Kafka**
    
    ![슬라이드15.PNG](Step%2009%20Large-Scale%20Expansion%20Design/33673c8e-f4e4-4314-9426-8a2a9152285b.png)
    
    - **Apache Kafka**: Apache Software Foundation에서 오픈 소스로 제공하는 순수 카프카
        - 기본적인 메시징 기능만을 제공하며, 추가적인 관리 도구나 엔터프라이즈 기능 지원 X
    - **Confluent Kafka**: Apache Kafka를 기반으로 Confluent사에서 개발한 상업용 배포판
        - Apache Kafka에 비해 다양한 추가 기능(스키마 레지스트리, Kafka Connect, KSQL DB 등)과 관리 도구, 보안 기능, 기술 지원 등을 제공

## 챕터1-5 : 기존 코드 개선하기

## 비동기 메세지 통신을 통한 책임 분리

![슬라이드16.PNG](Step%2009%20Large-Scale%20Expansion%20Design/slide16.png)

지난주에 해치웠다고 생각했던 로직을 다시한번 불러와볼까요?

### 이벤트를 활용한 코드

```jsx
class OrderPaymentService {
	Transaction {
		fun 주문_결제() {
			유저_포인트_차감();
			결제_정보_저장();
			주문_상태_변경();
			
			결제_완료_이벤트_발행();			
		}
	}
}

class OrderPaymentEventListener {

	TransactionalEventListener(AFTER_COMMIT) {
		Async {
			fun 주문_정보_전달(결제_완료_이벤트)
		}
	}
	
	TransactionalEventListener(AFTER_COMMIT) {
		Async {
			fun 결제_완료_알림톡_발송(결제_완료_이벤트)
		}
	}
}
```

- 서비스 코드도 깔끔해지고, 외부 로직이 어떤것들이 있는지 관심사가 분리됨
- 트랜잭션 커밋 이후에 비동기적으로 수행되기 때문에 외부 api에 영향을 정확하게 제거함
- 와 해치웠나!!??
    - 그런데… 데이터 수집 플랫폼이 일시적인 장애가 발생해서 **api가 실패**했다면?
    - 데이터 수집 플랫폼으로 **데이터를 재 전달해줘야하는 책임**은 우리한테 있는 억울한 느낌..
    - **재전송 로직**은 또 어디다 만들지…

데이터 수집 플랫폼의 메세지의 수신불가 상황의 책임을 우리가 갖지 않으려면?

데이터 수집 플랫폼이 일시적인 장애상황일 때에도 카프카에 주문정보를 정상적으로 적재(저장)해둔다면, 장애상황 해제 이후 적재되었던 주문정보를 “알아서” 컨슘해가면 되지 않을까?

### Kafka를 활용하여 더 개선한 코드

```jsx
class OrderPaymentService {
	Transaction {
		fun 주문_결제() {
			유저_포인트_차감();
			결제_정보_저장();
			주문_상태_변경();
			
			결제_완료_이벤트_발행();			
		}
	}
}

class OrderPaymentEventListener {

	TransactionalEventListener(AFTER_COMMIT) {
		**kafkaProducer.publish(**결제_완료_이벤트**);**
	}
}
```

- 주문정보를 카프카로 전달한다면 데이터 수집 플랫폼, 알림 서비스에서 메세지를 잘 받았던 못 받던 내 책임은 끝!!
- 두 서비스는 각각 다른 `Consumer Group` 으로 발행되는 메시지를 알아서 각각 처리할 수 있음
- **주문 서비스에서 이후에 일어나는 부가 로직에 대한 관심사가 아예 제거됨**
    - 데이터 수접 플랫폼, 알림 서비스에서 주문의 결제 완료 카프카 이벤트를 받아 처리
- 발행하는 행위를 비동기 처리하지 않는 이유가 있나요?
    - 웹서비스의 API는 서버의 자체 로직과 데이터를 적재하는 비용이 함께 포함되어 latency가 안정적이지 않을 수 있으나, 일반적으로 **카프카에 메세지를 발행할때는 별도 로직 없이 발행된 메세지를 저장만 하기에 발행에 대한 비용이 매우 적음**
    - 따라서 비동기로 처리하기 위해 별도의 스레드에서 컨텍스트 스위칭하는 비용이 오히려 더 클 수도 있음

## 챕터1-6 : 시나리오별 Kafka 활용

## 대용량 트래픽 프로세스 개선

이번에는 카프카를 활용해 대용량 트래픽을 안정적으로 처리하는 방법을 학습해봅시다.

우리는 이미 대용량 트래픽을 처리하기 위해 레디스로 아래 두 프로세스를 개선해봤어요. 레디스의 장점인 원자성 보장과 빠른 처리속도를 활용해 이미 뛰어난 성능을 제공하도록 개선했지만, 카프카를 활용해서 프로세스를 개선해보면서 카프카는 어떤 강력한 장점을 갖는지 이해해보도록 합시다.

### 선착순 쿠폰 발급 (이커머스)

![슬라이드18.PNG](Step%2009%20Large-Scale%20Expansion%20Design/slide18.png)

- **필요한 카프카 관련 컴포넌트**
    - 프로듀서
        - 쿠폰 발급 API 서버
    - 카프카 토픽
        - 쿠폰 발급 요청 (`coupon-publish-request` )
    - 컨슈머
        - 쿠폰 발급 처리 컨슈머
- **토픽의 파티션을 활용한 병렬 처리 및 순서 보장 처리**
    
    ![image.png](Step%2009%20Large-Scale%20Expansion%20Design/image.png)
    
    - 메시지의 키를 쿠폰번호로 설정하면 같은 쿠폰번호의 모든 요청이 하나의 파티션에 메시지가 발행(저장)되기 때문에, 순서가 보장되면서 쿠폰이 발급되어 별도의 lock 없이 구현해도 동시성 이슈 및 초과발급 이슈가 발생하지 않음.
    - 메시지의 키가 다르다면 다른 파티션에 메시지가 발행되기 때문에 동시에 처리할 수 있어 처리량을 향상할 수 있음.
    - 동시성 제어를 보장하면서도 처리량도 향상시킬 수 있는 카프카의 위력!
    
    Q. 이 상황에서 처리량을 높이기 위해서 가장 효과적인 방법을 **한개**만 고르시오.
    
    1. Producer 수를 늘린다.
    2. Consumer 수를 늘린다.
    3. Partition 수를 늘린다.

### 대기열 (콘서트 예약)

![슬라이드19.PNG](Step%2009%20Large-Scale%20Expansion%20Design/slide19.png)

- **필요한 카프카 관련 컴포넌트**
    - 프로듀서
        - 대기열 진입 API 서버
    - 카프카 토픽
        - 대기열 토큰 (`waiting-token` )
    - 컨슈머
        - 대기열 활성화 처리 컨슈머
- N초당 M개의 메시지를 읽도록 컨슈머를 설정하여 토큰 활성화
- 대기열의 특성상 처리량보다는 순차보장에 집중한 설계
    - 파티션의 수는 1개로 고정하여 **전체 콘서트 서비스에 대한 대기열**의 순차처리 가능
        - 전체 서비스에서 하나의 대기열을 활용하여 토큰 활성화
    - 파티션 수를 N개로 확장하여 **콘서트 별 대기열** 토큰 활성화 가능
        - 콘서트의 수 만큼 파티션을 구성하여 콘서트별 대기열 설정 가능
            - 동시에 예약이 진행되는 콘서트의 수 만큼 파티션을 구성하고, 콘서트와 파티션을 동적으로 매핑
        - 예약 가능한 콘서트만큼 파티션의 수가 존재해야 하기 때문에, 카프카의 자체 요소와 비즈니스 로직간의 강결합이 있는 단점이 있음

## 챕터1-7 : Kafka, 더 깊게 알아보기

## 조금 더 나아가기

<aside>
📖

지금까지 카프카의 핵심 개념들을 살펴보았습니다.
이제 실제 운영 환경에서 알아두면 좋은 몇 가지 심화된 주제들을 더 자세히 알아봅시다!

</aside>

![슬라이드21.PNG](Step%2009%20Large-Scale%20Expansion%20Design/slide21.png)

### **Commit에 대한 개념 자세히 살펴보기**

> 컨슈머가 메시지를 어디까지 처리했는지 위치를 기록하는 행위를 Commit이라고 합니다.
이 커밋된 오프셋을 기반으로 다음에 읽을 메시지의 시작점을 결정하게 됩니다.
> 
- Auto-commit
    - 카프카 컨슈머는 기본적으로 특정 주기(기본 5초)마다 현재까지 처리한 메시지의 오프셋을 자동으로 커밋. 우리가 해야할게 적어지지만 이걸 사용할때는 반드시 주의해야할 점이 있음.
    - **데이터 유실 가능성**: 컨슈머가 메시지를 아직 완전히 처리하지 못했는데, 자동 커밋 주기마다 오프셋이 커밋되면 장애 발생 시 이미 커밋된 오프셋 이후부터 다시 처리하게 되어 처리 중이던 메시지가 누락될 수 있음. (메시지 처리가 매우 오래 걸리는 상황에서)
        - 메시지 읽기 → 자동 커밋 → 메시지 처리 중 장애 → 재시작 시 커밋된 다음 오프셋부터 시작
    - **데이터 중복 처리 가능성**: 반대로 메시지 처리가 완료된 후 컨슈머가 예상치 못하게 종료되면, 다음 재시작 시 마지막으로 커밋된 오프셋부터 시작하여 일부 메시지가 중복 처리될 수 있음.
- Manual-commit
    - 개발자가 코드 내에서 직접 오프셋을 커밋하는 방식. 실전에서는 Auto-commit은 웬만해서 사용되지 않음.
    - 메시지 처리가 완료된 시점에 정확히 오프셋을 커밋하는 로직을 직접 만들자.
        - 예를 들어, DB 트랜잭션이 성공적으로 완료된 후 오프셋을 커밋함으로써 "최소 한 번(At-least-once)" 또는 "정확히 한 번(Exactly-once)" 처리를 보장!
    - `commitSync()`: 동기 방식 커밋. 커밋 요청이 완료될 때까지 블로킹.
        - 안정적이지만 처리량이 중요할 때는 성능 저하가 발생할 수 있음.
    - `commitAsync()`: 비동기 방식 커밋. 커밋 요청을 보내고 바로 다음 작업을 수행.
        - 처리량이 중요할 때 유리하지만, 커밋 실패에 대한 추가적인 오류 처리가 필요할 수 있음

### DLQ (Dead Letter Queue)

- 간혹 카프카 토픽에 적재된 메시지 중에는 컨슈머가 **아무리 재시도해도 처리할 수 없는** "문제 있는 메시지"가 있을 수 있음.
- DLQ(Dead Letter Queue)는 이렇게 더 이상 처리할 수 없는 메시지들을 별도로 모아두는 **전용 토픽**
- 활용
    - **모니터링**: DLQ에 쌓이는 메시지들을 모니터링하여 어떤 종류의 오류 메시지가 발생하는지 파악하고, 근본 원인을 분석
    - **재처리**: 오류의 원인이 해결된 후, DLQ에 쌓인 메시지들을 수동으로 또는 별도의 프로세스를 통해 다시 정상 토픽으로 보내 재처리
    - **격리**: 문제 있는 메시지가 다른 정상적인 메시지의 처리를 방해하지 않도록 격리
- 실전에서 DLQ는 카프카 기반 시스템의 안정성과 견고성을 높이는 데 사실상 필수적인 패턴!

### 리텐션(Retention) 정책

카프카 브로커는 메시지를 한 번 저장하면, 컨슈머가 메시지를 소비했는지 여부와 관계없이 일정 기간 동안 메시지를 보관합니다.

- 이 보관 정책을 **리텐션(Retention) 정책**이라고 합니다.
- 목적
    - 컨슈머 장애나 버그 발생 시, 과거의 메시지를 다시 읽어 재처리할 수 있도록 하기 위함
    - **새로운 컨슈머 그룹이 구독을 시작할 때**, 과거 데이터부터 읽을 수 있음
- 설정 기준
    - 시간 기반 (Log Retention Time): 메시지가 발행된 시점으로부터 특정 시간(예: 7일)이 지나면 삭제
        - 이것이 일반적으로 사용하는 정책
    - 크기 기반 (Log Retention Size): 토픽의 파티션 크기가 특정 크기(예: 1GB)를 초과하면 오래된 메시지부터 삭제

### 멱등성(Idempotency)

- 멱등성이란 동일한 연산을 여러 번 수행하더라도 결과가 항상 동일하게 유지되는 특성을 의미
- 왜 중요한가?
    - 카프카는 메시지 전달을 "최소 한 번(At-Least-Once)" 보장
    - 즉, **같은 메시지가 여러 번 전달될 수 있다**는 의미 → 만약 멱등성이 보장되지 않는 작업을 여러 번 수행하면 시스템에 예상치 못한 부작용이 발생할 수 있음
- 예시
    - 비멱등적(Non-Idempotent) 연산: "계좌에서 100원 차감"이라는 메시지를 여러 번 받으면 계좌 잔액이 계속 줄어들 수 있음
    - 멱등적(Idempotent) 연산: "주문 상태를 '결제 완료'로 변경" 메시지를 여러 번 받아도 주문 상태는 최종적으로 '결제 완료'로 동일하게 유지되는 경우
- 멱등성을 확보하는 방법
    1. Unique ID 활용: 메시지마다 유니크한 ID(예: UUID)를 부여하고, 컨슈머는 이 ID를 기준으로 이미 처리된 메시지인지 확인하여 중복 처리를 방지
        - 데이터베이스에 `processed_message_id` 테이블을 만들고, 메시지 ID를 저장 후 조회
    2. 상태 기반 처리: 특정 상태로의 전환만 허용하거나, 최종 상태를 덮어쓰는 방식으로 처리합
        - '결제 대기'에서 '결제 완료'로만 변경 가능하게 하거나, 주문 상태를 단순히 '결제 완료'로 업데이트

### Zero-Payload vs Full-Payload

> 카프카 메시지에 얼만큼의 데이터를 담을 것인가
> 
- Full-Payload (풀 페이로드)
    - 메시지에 필요한 모든 데이터를 직접 담아서 발행하는 방식
    - 예시: 주문 완료 이벤트 메시지에 주문 번호, 상품 목록, 결제 금액, 배송지 주소 등 모든 주문 정보를 담는 경우
- Zero-Payload (제로 페이로드)
    - 메시지에는 최소한의 식별 정보(ID)만 담고, 필요한 실제 데이터는 별도의 데이터 스토어(DB, Redis 등)에서 조회하도록 하는 방식
    - 예시: 주문 완료 이벤트 메시지에 주문 번호(`orderId`)만 담고, 컨슈머는 `orderId`를 이용해 DB에서 주문 상세 정보를 조회하는 경우
- 실무적으로 Zero-Payload가 더 많다.
    - 데이터 무결성 및 최신성: 메시지가 발행된 시점의 데이터가 아니라, 컨슈머가 메시지를 처리하는 시점의 가장 최신 데이터를 조회해야 하는 경우가 많음
        - Full-Payload 방식은 메시지 발행 시점의 스냅샷 데이터이므로, 이후 데이터가 변경되면 메시지의 데이터는 과거의 정보를 담고 있는 문제 발생
    - 메시지 크기 최적화: 대용량 트래픽 환경에서는 메시지 하나의 크기가 전체 시스템 성능에 미치는 영향이 클 수 있음
        - Kafka도 무적의 시스템은 아니기 때문에 최소한의 부하를 줘야 클러스터가 안정적으로 운영됨
    - 데이터 변경의 유연성: 실제 상세 데이터의 스키마가 변경되어도 메시지의 스키마는 거의 변하지 않으므로, 시스템 변경에 대한 유연성이 높음
    - 역할 분리: 카프카는 이벤트 전달 플랫폼으로서의 역할에 집중하고, 실제 데이터 저장 및 관리는 전용 데이터 스토어가 담당하게 하여 시스템의 책임과 역할을 명확히 분리할 수 있음

## 챕터1-8 : 과제

<aside>
🚩 **이번 주차 과제**

</aside>

![슬라이드22.PNG](Step%2009%20Large-Scale%20Expansion%20Design/slide22.png)

### **`필수과제 - 카프카 기초 학습 및 활용`**

![9__________________________.png](Step%2009%20Large-Scale%20Expansion%20Design/9__________________________.png)

- 카프카에 대한 기초 개념을 학습하고 문서로 작성합니다.
    - 이벤트 아이디어를 시스템 전체 관점으로 확장 할 수 있다는 점을 상기해봅시다.
    - Kafka를 사용하면 얻을 수 있는 장단점을 정리해봅시다.
    - Kafka의 특징과 주요 요소, 핵심적인 기능은 무엇인지 생각해봅시다.
    
    → 이를 Kafka를 잘 모르는 동료들에게 공유한다고 생각하고 문서를 한번 만들어봅시다.
    
- 로컬에서 카프카를 설치하고 기본적인 기능을 수행해봅니다.
    - 제시된 Docker Compose 파일을 활용하여 카프카 클러스터를 로컬에서 실행시켜봅시다.
        - docker-compose.kafka.yaml
            
            ```yaml
            version: '3.8'
            
            services:
              zookeeper:
                image: confluentinc/cp-zookeeper:7.6.0
                hostname: zookeeper
                container_name: zookeeper
                ports:
                  - "2181:2181"
                environment:
                  ZOOKEEPER_CLIENT_PORT: 2181
                  ZOOKEEPER_TICK_TIME: 2000
            
              broker1:
                image: confluentinc/cp-kafka:7.6.0
                hostname: broker1
                container_name: broker1
                ports:
                  - "9092:9092"
                depends_on:
                  - zookeeper
                environment:
                  KAFKA_BROKER_ID: 1
                  KAFKA_ZOOKEEPER_CONNECT: 'zookeeper:2181'
                  KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
                  KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://broker1:29092,PLAINTEXT_HOST://localhost:9092
                  KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
                  KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
                  KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
                  KAFKA_LOG_RETENTION_MS: 604800000
                  KAFKA_LOG_RETENTION_BYTES: 1073741824
            
              broker2:
                image: confluentinc/cp-kafka:7.6.0
                hostname: broker2
                container_name: broker2
                ports:
                  - "9093:9093"
                depends_on:
                  - zookeeper
                environment:
                  KAFKA_BROKER_ID: 2
                  KAFKA_ZOOKEEPER_CONNECT: 'zookeeper:2181'
                  KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
                  KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://broker2:29093,PLAINTEXT_HOST://localhost:9093
                  KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
                  KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
                  KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
                  KAFKA_LOG_RETENTION_MS: 604800000
                  KAFKA_LOG_RETENTION_BYTES: 1073741824
            
              broker3:
                image: confluentinc/cp-kafka:7.6.0
                hostname: broker3
                container_name: broker3
                ports:
                  - "9094:9094"
                depends_on:
                  - zookeeper
                environment:
                  KAFKA_BROKER_ID: 3
                  KAFKA_ZOOKEEPER_CONNECT: 'zookeeper:2181'
                  KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
                  KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://broker3:29094,PLAINTEXT_HOST://localhost:9094
                  KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
                  KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
                  KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
                  KAFKA_LOG_RETENTION_MS: 604800000
                  KAFKA_LOG_RETENTION_BYTES: 1073741824
            
            ```
            
        - 직접 VM을 여러대 띄워서 카프카 클러스터를 구성하거나, 클라우드 서비스의 Managed 서비스(예: AWS MSK)를 이용해서 카프카 클러스터를 구성하면 BEST!
    - 어플리케이션에서 카프카를 연결하여 Producer & Consumer를 동작시켜봅니다.
- 실시간 주문정보(이커머스) & 예약정보(콘서트) & 맛집(검색) 를 카프카 메시지로 발행하도록 변경합니다.
    - 기존에 구현했던 이벤트를 통한 데이터 플랫폼으로의 정보 전달 과정을 Kafka를 통하여 전달하도록 수정해봅시다.

<aside>
<img src="https://www.notion.so/icons/light-bulb_red.svg" alt="https://www.notion.so/icons/light-bulb_red.svg" width="40px" />

**참고 PR 링크**

| 스택 | STEP9 |
| --- | --- |
| Java | [https://github.com/BEpaul/hhplus-e-commerce/pull/42](https://github.com/BEpaul/hhplus-e-commerce/pull/42) |
| Java | [https://github.com/juny0955/hhplus-concert/pull/38](https://github.com/juny0955/hhplus-concert/pull/38) |
| Kotlin | [https://github.com/chapakook/kotlin-ecommerce/pull/51](https://github.com/chapakook/kotlin-ecommerce/pull/51) |
| Kotlin | [https://github.com/psh10066/hhplus-server-concert/pull/27](https://github.com/psh10066/hhplus-server-concert/pull/27) |
| TS | [https://github.com/psyoongsc/hhplus-ts-server/pull/53](https://github.com/psyoongsc/hhplus-ts-server/pull/53) |
| TS | [https://github.com/suji6707/nestjs-case-02-ticketing/pull/9](https://github.com/suji6707/nestjs-case-02-ticketing/pull/9) |
</aside>

### **`선택과제- 카프카를 활용하여 비즈니스 프로세스 개선`**

![10.png](Step%2009%20Large-Scale%20Expansion%20Design/10.png)

- 각 프로젝트의 대용량 트래픽이 발생할 수 있는 지점을 파악해보고, 이를 Kafka를 활용하여 어떻게 개선하면 좋을지 고민해봅시다.
- 개선할 내용에 대한 설계 문서를 작성합니다.
    - 들어가면 좋을 법한 내용: 해당 기능에 카프카를 사용하면 좋은 이유, 비즈니스 시퀀스 다이어그램, 카프카 구성
- 설계한 내용을 바탕으로 실제 Kafka를 활용하여 대응하도록 변경해봅시다.
