# Step 06. 분산락과 캐싱으로 트래픽 대응

## 챕터1-1 : 이번 챕터 목표

<aside>
👦 **튜터 소개**

</aside>

- **스파르타코딩클럽 쿠버네티스 강사**
- **현직 NOL UNIVERSE 백엔드 엔지니어 (야놀자 그룹)**
- **Ad-Tech 도메인에서 오랜 기간 백엔드와 인프라를 담당**

<aside>
⚓ **이번 챕터에서 4주간 배울 것**

</aside>

- **대규모 트래픽 환경에서 발생하는 기술적 문제를 이해하고, 이를 해결하기 위한 시스템 설계 방법과 핵심 서비스를 학습한다.**
- **Redis와 Message Queue(Kafka)의 역할과 특징을 이해하여, 실시간 데이터 처리, 캐싱, 분산락, 이벤트 기반 아키텍처 등의 요구사항에 적절히 적용할 수 있는 역량을 기른다.**
- **고가용성, 확장성, 일관성 사이의 트레이드오프를 고려한 설계 및 운영 전략을 습득한다.**
- **실습과 사례 기반 학습을 통해, 실제 서비스에서 Redis와 Kafka를 조합하여 사용하는 방식을 경험하고, 이를 기반으로 복잡한 분산 시스템을 설계·운영할 수 있는 실전 감각을 키운다.**

## 챕터1-2 : 이번 강의에서 배울 것

<aside>
⛵ **이번 주차 목표**

</aside>

- **DB 트랜잭션 이상의 범위, 분산 환경에서 Lock 을 적용할 수 있는 방법에 대해 고민해 봅니다.**
- **다량의 트래픽을 처리하기 위해 적은 DB 부하로 올바르게 기능을 제공할 방법을 고민해 봅니다.**
- **캐시 레이어의 적용을 통해 DB I/O 를 줄일 방법을 고민해 봅니다.**

**왜 이번 주차의 목표가 중요할까요?**

<aside>
👉

이번 주차 목표를 충분히 이해하고 체화하는 것은, 단순한 기능 구현을 넘어서 **고성능, 고가용성 시스템을 설계하는 실무 역량을 갖추는 첫걸음**이 됩니다.

- 분산 환경에서의 데이터 일관성을 유지하기 위한 방법을 고민하며, 단일 DB 트랜잭션의 한계를 넘는 분산 락과 보상 트랜잭션 등의 개념을 이해하는 데 중점을 둡니다.
- 대규모 트래픽 상황에서도 DB에 과도한 부하 없이 안정적으로 기능을 제공하기 위한 구조 설계 역량을 기릅니다. 이를 통해 효율적인 쿼리 처리와 비동기 시스템 설계에 대한 이해를 높입니다.
- 자주 요청되는 데이터를 빠르게 제공하고 DB I/O를 줄이기 위해 캐시 레이어를 효과적으로 적용하는 방법을 학습하며, 고성능 시스템 구축에 필요한 핵심 전략을 다룹니다.
</aside>

![슬라이드1.PNG](Step%2006%20%EB%B6%84%EC%82%B0%EB%9D%BD%EA%B3%BC%20%EC%BA%90%EC%8B%B1%EC%9C%BC%EB%A1%9C%20%ED%8A%B8%EB%9E%98%ED%94%BD%20%EB%8C%80%EC%9D%91/%EC%8A%AC%EB%9D%BC%EC%9D%B4%EB%93%9C1.png)

<aside>
💡

**What to do: 이번 챕터에 해야 할 것. 이것만 집중하세요!**

</aside>

![슬라이드4.PNG](Step%2006%20%EB%B6%84%EC%82%B0%EB%9D%BD%EA%B3%BC%20%EC%BA%90%EC%8B%B1%EC%9C%BC%EB%A1%9C%20%ED%8A%B8%EB%9E%98%ED%94%BD%20%EB%8C%80%EC%9D%91/%EC%8A%AC%EB%9D%BC%EC%9D%B4%EB%93%9C4.png)

![슬라이드7.PNG](Step%2006%20%EB%B6%84%EC%82%B0%EB%9D%BD%EA%B3%BC%20%EC%BA%90%EC%8B%B1%EC%9C%BC%EB%A1%9C%20%ED%8A%B8%EB%9E%98%ED%94%BD%20%EB%8C%80%EC%9D%91/%EC%8A%AC%EB%9D%BC%EC%9D%B4%EB%93%9C7.png)

> **점점 늘어나는 고객과 많은 트래픽은 점점 시스템의 높은 Throughput 을 요구하게 됩니다.**
> 
> - **RDBMS 만으로는 다양한 비즈니스 가치를 달성하기 어렵습니다.**
> - **우리는 다양한 문제를 해결하기 위해 REDIS와 MQ라는 추가 선택지를 찾게 됩니다.**

## 챕터1-3 : 분산락 파헤치기

### 1. Distributed Lock 기반의 동시성 제어

### **Distributed Lock ( 분산락 )**

- **분산 시스템에서 서로 다른 서버 인스턴스에 대한 일관된 락을 제공하기 위한 장치 feat. Redis**
- **분산락의 핵심은 `분산된 서버/클러스터` 간에도 Lock 을 보장하는 것**
- **key-value 기반의 원자성을 이용한 Redis 를 통해 DB 부하를 최소화하는 Lock 을 설계**

![Untitled](Step%2006%20%EB%B6%84%EC%82%B0%EB%9D%BD%EA%B3%BC%20%EC%BA%90%EC%8B%B1%EC%9C%BC%EB%A1%9C%20%ED%8A%B8%EB%9E%98%ED%94%BD%20%EB%8C%80%EC%9D%91/Untitled.png)

                                     <유저의 잔액 충전과 사용의 동시 요청을 레디스를 활용하여 제어>

- **레디스를 활용한 분산락의 대표적인 세가지 방식**
    - `Simple Lock` - key 선점에 의한 lock 획득 실패 시, 비즈니스 로직을 수행하지 않음
        
        ```kotlin
        락 획득 여부 = redis 에서 key 로 확인
        if (락 획득) {
        	try {
        	  로직 실행
        	} finally {
        	  lock 제거
        	}
        } else {
          throw Lock 획득 실패 예외
        }
        ```
        
        - Lock 획득 실패 시 요청에 대한 비즈니스 로직을 수행하지 않음
        - 실패 시 재시도 로직에 대해 고려해야하며 요청의 case 에 따라 실패 빈도가 높음
    - `Spin Lock` - lock 획득 실패 시, 일정 시간/횟수 동안 Lock 획득을 재시도
        
        ```kotlin
        재시도 횟수 = 0
        while (true) {
          락 획득 여부 = redis 에서 key 로 확인 // SETNX key "1"
          if (락 획득) {
        		try {
        		  로직 실행
        		} finally {
        		  lock 제거
        		}
        		break;
          } else {
        	  재시도 횟수 ++
        	  if (재시도 횟수 == 최대 횟수) throw Lock 획득 실패 예외
        	  시간 지연 ( 대기 )
          }
        }
        ```
        
        - Lock 획득 실패 시, 지속적인 재시도로 인한 네트워크 비용 발생
        - 재시도에 지속적으로 실패할 시, 스레드 점유 등 문제 발생
    - `Pub/Sub` - redis pub/sub 구독 기능을 이용해 lock 을 제어
        
        ```kotlin
        락 획득 여부 = redis 에서 lock 데이터 에 대한 subscribe 요청 및 획득 시 값 반환
        // 특정 시간 동안 Lock 에 대해 구독
        if (락 획득) {
        	로직 실행
        } else {
        	// 정해진 시간 내에 Lock 을 획득하지 못한 경우
        	throw Lock 획득 실패 예외
        }
        ```
        
        - 레디스 Pub/Sub 기능을 활용해 락 획득을 실패 했을 시에, “구독” 하고 차례가 될 때까지 이벤트를 기다리는 방식을 이용해 효율적인 Lock 관리가 가능
        - “구독” 한 subscriber 들 중 먼저 선점한 작업만 Lock 해제가 가능하므로 안정적으로 원자적 처리가 가능
        - 직접 구현, 혹은 라이브러리를 이용할 때 해당 방식의 구현이 달라질 수 있으므로 주의해서 사용해야 함

**“레디스”를 활용한 락에서 락 획득과 트랜잭션의 순서의 중요성**

락과 트랜잭션은 데이터의 무결성을 보장하기 위해 아래 순서에 맞게 수행됨을 보장해야 합니다.

![Untitled](Step%2006%20%EB%B6%84%EC%82%B0%EB%9D%BD%EA%B3%BC%20%EC%BA%90%EC%8B%B1%EC%9C%BC%EB%A1%9C%20%ED%8A%B8%EB%9E%98%ED%94%BD%20%EB%8C%80%EC%9D%91/Untitled%201.png)

왜 그래야할까요?

**정상적인 케이스**

![Untitled](Step%2006%20%EB%B6%84%EC%82%B0%EB%9D%BD%EA%B3%BC%20%EC%BA%90%EC%8B%B1%EC%9C%BC%EB%A1%9C%20%ED%8A%B8%EB%9E%98%ED%94%BD%20%EB%8C%80%EC%9D%91/Untitled%202.png)

동시에 두 요청이 인입되더라도, 락의 범위 내에서 트랜잭션이 일어나면, 상품의 재고차감에 대해 동시성이슈 발생하지 않습니다.

**트랜잭션이 먼저 시작된 뒤 락을 획득할 때 발생할 수 있는 문제**

![Untitled](Step%2006%20%EB%B6%84%EC%82%B0%EB%9D%BD%EA%B3%BC%20%EC%BA%90%EC%8B%B1%EC%9C%BC%EB%A1%9C%20%ED%8A%B8%EB%9E%98%ED%94%BD%20%EB%8C%80%EC%9D%91/Untitled%203.png)

트랜잭션이 시작되어 데이터를 조회한 이후에 락을 획득하면, 동시에 처리되고 있는 앞단 트랜잭션의 커밋결과를 확인하지 못한 채 재고를 조회해와 차감하는 로직을 수행하게 되므로, 정상적으로 재고를 차감할 수 없습니다. 

또, 트랜잭션이 시작된 이후에 락 획득에 실패하면 의미없는 트랜잭션을 발생시킵니다. 또 락 획득을 위한 대기시간 동안 데이터베이스의 커넥션을 유지하여야 합니다. 즉, 데이터베이스에 부하를 유발할 수 있습니다.

→ DB Connection Pool로 커넥션이 유지된 자원은 서버단에서 가지고 있으나, 원활한 자원 반납이 늦어짐에 따라 처리 성능감소

**락이 먼저 해제된 뒤 트랜잭션이 커밋될 때 발생할 수 있는 문제**

![Untitled](Step%2006%20%EB%B6%84%EC%82%B0%EB%9D%BD%EA%B3%BC%20%EC%BA%90%EC%8B%B1%EC%9C%BC%EB%A1%9C%20%ED%8A%B8%EB%9E%98%ED%94%BD%20%EB%8C%80%EC%9D%91/Untitled%204.png)

락이 해제된 이후에 트랜잭션이 커밋된다면, 대기하던 요청이 락 해제 이후 트랜잭션의 커밋 반영 이전의 재고를 조회해와 차감하는 로직을 수행하게 되므로, 마찬가지로 정상적으로 재고를 차감할 수 없습니다.

**Kafka Messaging** 

- 메세지 큐와 같이 순서 보장이 가능한 장치를 이용해 동시성 이슈를 해결
- Queue 의 성질을 이용, 처리 순서를 보장해 특정 데이터에 대한 동시 접근 문제를 해결
- `Kafka` 의 발행 메세지는 기본적으로 각 파티션에 분산되지만, “동일한 key 로 메세지를 발행 시” 항상 동일한 파티션에 메세지가 발행되는 걸 보장해 컨슈머가 순서대로 처리하도록 할 수 있음
    
    ![Untitled](Step%2006%20%EB%B6%84%EC%82%B0%EB%9D%BD%EA%B3%BC%20%EC%BA%90%EC%8B%B1%EC%9C%BC%EB%A1%9C%20%ED%8A%B8%EB%9E%98%ED%94%BD%20%EB%8C%80%EC%9D%91/Untitled%205.png)
    
    - 트랜잭션의 범위를 좁히고, 순차 처리를 보장할 수 있으므로 성능적 우위 가능
    - 비동기 처리를 위한 비즈니스 로직의 분리 및 구조 설계가 중요
    - 카프카 HA (고가용성) , 컨슈머 Scale-out 등 구조를 고려할 수 있어야 함
    - 비동기 처리가 되므로 처리 결과를 바로 확인할 수 없음
    

분산락으로는 동시성 문제가 모두 해소 가능한지?

```
Redis 등을 활용한 외부 Resource 를 통해 불필요한 DB Connection 까지 차단 가능하다.
하지만 관리주체가 DB + Redis 와 같이 늘어남에 따라 다양한 문제 파생으로 이어진다.

**Lock 의 관리주체가 다운되면 서비스 전체의 Down 으로 이어질 수 있는 문제**가 있다.

하지만 Redis 의 높은 원자성을 활용해 프로세스 처리단위에 대한 동일한 Lock 을
여러 인스턴스에 대해 적용할 수 있으므로 매우 효과적일 수 있고, DB 의 Conection 이나
오래 걸리는 I/O 에 대한 접근 자체를 차단할 수 있으므로 **DB 에 가해지는 직접적인 부하를
원천 차단할 수 있으므로 효과적**이다.
```

- DB Transaction 과 Lock 의 범위에 따른 처리 고려
    
    ![Untitled](Step%2006%20%EB%B6%84%EC%82%B0%EB%9D%BD%EA%B3%BC%20%EC%BA%90%EC%8B%B1%EC%9C%BC%EB%A1%9C%20%ED%8A%B8%EB%9E%98%ED%94%BD%20%EB%8C%80%EC%9D%91/Untitled%201.png)
    

**[!] 여기서 잠깐! 분산락을 구현할때 아래와 같이 하면..?**

```java
@Transactional
public void charge(Long userId, BigDecimal point) {
	RLock lock = redissonClient.getLock("userChargeLock");

	try {
		if(lock.tryLock() == true) {
			User user = userRepository.findById(userId)
			user.charge(point)
		} else {
			throw new LockAccruedFailedException();
		}
	} finally {
		lock.unlock();
	}
}

@Transactional
public void pay(Long userId, BigDecimal point) {
	RLock lock = redissonClient.getLock("userPayLock");

	try {
		if(lock.tryLock() == true) {
			User user = userRepository.findById(userId)
			user.pay(point)
		} else {
			throw new LockAccruedFailedException();
		}
	} finally {
		lock.unlock();
	}
}
```

- 발생 가능 현상 ( 우리의 의도대로 구현이 되었는가? )
    1. 트랜잭션과 락의 순차보장이 실패해요.
    2. 충전과 결제가 동시에 수행 가능해요.
    3. 충전과 결제 기능 자체에 걸리는 락.
    

  4. Test Code 의 병렬 부하 만으로 부하발생 환경에서 동시성 테스트가 가능한가?

      : JMeter, nGrinder, k6를 사용하여 별도의 부하테스트 필요

https://notspoon.tistory.com/48

## 챕터1-4 : 캐싱이란?

### 2. Caching

**Cache**

- 데이터를 임시로 복사해두는 Storage 계층
- 적은 부하로 API 응답을 빠르게 처리하기 위해 캐싱을 사용

<aside>
💡 우리 주변에서 볼 수 있는 **Cache 사례**
* DNS : 웹사이트 IP 를 기록해두어 웹사이트 접근을 위한 DNS 조회 수를 줄인다.
* CPU : 자주 접근하는 데이터의 경우, 메모리에 저장해두고 빠르게 접근할 수 있도록 한다.
* CDN : 이미지나 영상과 같은 컨텐츠를 CDN 서버에 저장해두고 애플리케이션에 접근하지 않고 컨텐츠를 가져오도록 해 부하를 줄인다.

</aside>

**Server Caching 전략**

- `application level` **메모리 캐시**
    - 애플리케이션의 메모리에 데이터를 저장해두고 같은 요청에 대해 데이터를 빠르게 접근해 반환함으로서 API 성능 향상 달성
        
        e.g. **Spring** ( ehcache, caffeine, .. ) , **nest-js** ( @nestjs/cache-manager, .. )
        
        ### Spring Cacheable Example
        
        ```java
        @Cacheable("POPULAR_ITEM")
        @Transactional(readOnly = true)
        public List<PopularItem> getPopularItems() {
        	return statisticsService.findPopularItems();
        }
        
        @Scheduled(cron = "0 0 0 * * *")
        @CacheEvict("POPULAR_ITEM")
        public void evictPopularItemsCache() { }
        ```
        
        ### Nest.js CacheInterceptor Example
        
        ```tsx
        // NestJS Caching
        @Injectable()
        export class HttpCacheInterceptor extends CacheInterceptor {
          trackBy(context: ExecutionContext): string | undefined {
            const cacheKey = this.reflector.get(
              CACHE_KEY_METADATA,
              context.getHandler(),
            );
         
            if (cacheKey) {
              const request = context.switchToHttp().getRequest();
              return `${cacheKey}-${request._parsedUrl.query}`;
            }
         
            return super.trackBy(context);
          }
        }
        ```
        
    - 메모리 캐시 원리
        
        <요청 1>
        
        1. 유저의 API 요청이 들어왔을 때 `Memory` 에 Cache Hit 확인
        2. Cache Miss 시에 비즈니스 로직 수행 ( DB, 외부API 등 통신 ) 후 `Memory` 에 데이터 저장
        3. 이후 응답
        
        ---
        
        <요청 2>
        
        1. 유저의 API 요청이 들어왔을 때 `Memory` 상에 상응하는 응답이 있는지 확인
        2. 있으면 해당 값을 이용해 그대로 응답
        
        ![image.png](Step%2006%20%EB%B6%84%EC%82%B0%EB%9D%BD%EA%B3%BC%20%EC%BA%90%EC%8B%B1%EC%9C%BC%EB%A1%9C%20%ED%8A%B8%EB%9E%98%ED%94%BD%20%EB%8C%80%EC%9D%91/image.png)
        
    - 메모리 캐시 특징
        - `신속성` - 인스턴스의 메모리에 캐시 데이터를 저장하므로 속도가 가장 빠름
        - `저비용` - 인스턴스의 메모리에 캐시 데이터를 저장하므로 별도의 네트워크 비용이 발생하지 않음
        - `휘발성` - 애플리케이션이 종료될 때, 캐시 데이터는 삭제됨
        - `메모리 부족` - 활성화된 애플리케이션 인스턴스에 데이터를 올려 캐싱하는 방법이므로 메모리 부족으로 인해 비정상 종료로 이어질 수 있음
        - `분산 환경 문제` - 분산 환경에서 서로 다른 서버 인스턴스 간에 데이터 불일치 문제가 발생할 수 있음
        
        <aside>
        💡 분산 서비스 환경에서의 애플리케이션 캐시
        
        각 서버의 수용 가능 상태에 따라 서로 다른 인스턴스에 요청이 분배될 수 있으며, 아래와 같은 문제가 발생할 수 있습니다.
        
        **유저1 이 동일한 조회 API 를 3번 요청**
        
        - Server2 에 도달한 경우, 이전의 캐시가 존재해 `르탄1,르탄2`라는 응답을 받음
        - 그 후 `르탄3,르탄4` 라는 데이터가 추가됨
        - 다음 요청은 캐시가 만료된 Server1 에 도달해 최신의 정보를 모두 가져옴
        - 다음 요청은 캐시가 존재하는 Server2 에 도달해 이전의 정보인 `르탄1,르탄2` 만을 가져옴
        
        ![image.png](Step%2006%20%EB%B6%84%EC%82%B0%EB%9D%BD%EA%B3%BC%20%EC%BA%90%EC%8B%B1%EC%9C%BC%EB%A1%9C%20%ED%8A%B8%EB%9E%98%ED%94%BD%20%EB%8C%80%EC%9D%91/image%201.png)
        
        위와 같이 애플리케이션의 메모리에 의존적인 캐시가 제공될 경우, 유저는 요청마다 어떤 인스턴스에 도달하느냐에 따라 같은 시간대에 다른 응답을 받게 된다.
        
        이런 상황이 유저의 구매나 사용성에 영향을 미치는 기능에서 발생할 경우, 사용자 경험에 지대한 악영향을 끼칠 수 있습니다.
        
        **어떻게 해결할 수 있을까?**
        
        - 캐시 데이터가 저장 / 삭제 될 때마다 다른 인스턴스에도 통지하여 동기화 시키는 방법 ( 추가 네트워크 비용 발생 )
        - 별도의 캐시 스토리지를 두고 모든 인스턴스가 하나의 관리주체를 통해 제공받을 수 있도록 구성 ( 하기 설명 )
        </aside>
        
- `external Level` **별도의 캐시 서비스**
    - 별도의 캐시 Storage 혹은 이를 담당하는 API 서버를 통해 캐싱 환경 제공
        
        e.g. **Redis**, Nginx 캐시, CDN, ..
        
    - 캐시 서비스 원리
        
        < 요청 1 >
        
        1. 유저의 API 요청이 들어왔을 때 `Cache Service` 에 Cache Hit 확인
        2. Cache Miss 일 경우, 비즈니스 로직을 수행 후 결과를 `Cache Service` 에 저장
        3. 이후 응답 반환
        
        ---
        
        < 요청 2 >
        
        1. 유저의 API 요청이 들어왔을 때 `Cache Service` 에 Cache Hit 확인
        2. Cache Hit 일 경우, 해당 데이터를 그대로 활용해 응답 반환
        
        ![Untitled](Step%2006%20%EB%B6%84%EC%82%B0%EB%9D%BD%EA%B3%BC%20%EC%BA%90%EC%8B%B1%EC%9C%BC%EB%A1%9C%20%ED%8A%B8%EB%9E%98%ED%94%BD%20%EB%8C%80%EC%9D%91/Untitled%206.png)
        
    - 캐시 서비스 특징
        - `일관성` - 별도의 담당 서비스를 둠으로서 분산 환경 ( Multi - Instance ) 에서도 동일한 캐시 기능을 제공할 수 있음
        - `안정성` - 외부 캐시 서비스의 Disk 에 스냅샷을 저장하여 장애 발생 시 복구가 용이함
        - `고가용성` - 각 인스턴스에 의존하지 않으므로 분산 환경을 위한 HA 구성이 용이함
        - `고비용` - 네트워크 통신을 통해 외부의 캐시 서비스와 소통해야 하므로 네트워크 비용 또한 고려해야 함

## 챕터1-5 : 캐싱 전략

### 3. Caching Strategy

<aside>
ℹ️ DB 의 Connection 과 I/O 는 매우 높은 비용을 요구하며, 이는 트래픽이 많아질 수록 기하급수적으로 그 부하가 증가하는 특성을 가지고 있다. 데이터 정합성을 유지하기 위해 각 트랜잭션은 원자적으로 수행되어야 하며 이는 요청이 증가할 수록 더 많은 딜레이가 생긴다는 것을 의미하기 때문이다.

</aside>

### Cache 의 Termination Type

- **Expiration**
    - 캐시 데이터의 유통기한을 두는 방법
    - Lifetime 이 지난 캐시 데이터의 경우, 삭제시키고 새로운 데이터를 사용가능하게 함
- **Eviction**
    - 캐시 메모리 확보를 위해 캐시 데이터를 삭제
    - 명시적으로 캐시를 삭제시키는 기능
    - 특정 데이터가 Stale 해진 경우 ( 상한 경우 ) 기존 캐시를 삭제할 때도 사용
- **LRU (Least Recently Used)**
    - 가장 오랫동안 사용되지 않은 데이터를 우선적으로 제거하는 전략
    - 캐시 공간이 부족할 경우 최근에 접근되지 않은 데이터부터 제거하여, 자주 사용되는 데이터의 유지율을 높임

**[ 조회 ]**

- `Cache` 에서 데이터를 먼저 확인하고, 없다면 DB 를 확인
    
    ![Untitled](Step%2006%20%EB%B6%84%EC%82%B0%EB%9D%BD%EA%B3%BC%20%EC%BA%90%EC%8B%B1%EC%9C%BC%EB%A1%9C%20%ED%8A%B8%EB%9E%98%ED%94%BD%20%EB%8C%80%EC%9D%91/Untitled%207.png)
    
- 일반적으로 잦은 조회가 일어나거나 DB I/O 가 많은 조회에 대해 부하를 줄이기 위한 전략
    
    **방식**
    
    1. Cache 에 해당하는 데이터가 있는지 확인 ( Cache Hit )
    2. Cache 에 없을 경우 ( Cache Miss ) DB 에서 데이터 조회
    3. DB 에서 조회한 데이터를 Cache 에 저장
    
    **특징**
    
    - 조회 부하를 낮추는 데에 적합한 방식
    - 빈번하게 변경이 일어나는 데이터에 대해서 정합성 보장을 위해 Eviction 전략을 잘 세워야 함
        - 인스타그램 피드의 좋아요
        - 이커머스의 인기상품 조회 (실시간(5분 / 10분), 일간, 주간)

## (추가 학습자료)

### **🎁**Redis 학습 자료

### **핵심 요약**

1. **캐싱의 개념 및 중요성**
    - 캐시란 데이터를 임시로 저장하여 빠르게 응답하고 DB 부하를 줄이는 기술.
    - DNS, CPU 캐시, CDN 등 다양한 사례를 설명.
    - 서버 내 메모리 캐시와 외부 캐시 서비스(예: Redis, CDN) 비교.
2. **서버 캐싱 전략**
    - **메모리 캐시 (Application Level)**
        - Spring 및 Nest.js에서의 캐싱 코드 예시 제공.
        - 장점: 속도 빠름, 네트워크 비용 없음.
        - 단점: 분산 환경에서 데이터 불일치 가능성, 애플리케이션 종료 시 캐시 삭제.
    - **외부 캐시 서비스 (External Level)**
        - Redis 같은 외부 스토리지 사용으로 일관성과 안정성 강화.
        - 장점: 분산 환경에서도 안정적, 장애 복구 가능.
        - 단점: 네트워크 통신으로 비용 증가.
3. **캐싱 전략 및 종료 타입**
    - 데이터 유효기간 관리 (`Expiration`)과 메모리 확보를 위한 삭제(`Eviction`) 방법 설명.
    - 조회 부하를 줄이는 캐시 활용 시나리오 (e.g., 인기 상품 조회, 게임 리더보드).
4. **Redis 데이터 구조**
    - **Strings:** 단순 문자열 및 증감 연산.
    - **Sets:** 중복 없는 데이터 저장, 집합 연산 지원.
    - **Sorted Sets:** 점수(score) 기반 정렬된 데이터 저장.
    - Redis를 활용한 다양한 실전 사례 소개.

### **Milestones**

| 단계 | 목표 |
| --- | --- |
| Phase 1 | **Redis 이해** |
| Phase 2 | **캐싱의 개념과 필요성** |
| Phase 3 | **Redis 데이터 구조 심화** |
| Phase 4 | **Redis 사용하기** |
| Phase 5 | **캐싱 전략 이해** |
| Phase 6 | **Redis 캐싱 최적화** |

### 세부 학습 로드맵

**Phase 1: Redis 소개 및 설치**

- **목표**: Redis의 기본 개념과 특징을 이해하고, 개발 환경에 Redis를 설치합니다.
- **키워드**: Redis 개요, 설치, 데이터 구조
- **학습 자료**:
    - [Redis 공식 문서](https://redis.io/docs/latest/develop/)
    - [설치 가이드](https://redis.io/docs/latest/operate/oss_and_stack/install/install-stack/)
    - [Redis 공식 튜토리얼](https://redis.io/learn)

**Phase 2: 캐싱의 개념과 필요성**

- **목표**: 캐싱의 기본 개념과 필요성을 이해합니다.
- **키워드**: 캐싱 개념, 필요성, 장단점
- **학습 자료**:
    - [캐싱이란 무엇인가?](https://aws.amazon.com/ko/caching/)

**Phase 3: Redis 데이터 구조 심화**

- **목표**: Redis에서 제공하는 다양한 데이터 구조를 학습합니다.
- **키워드**: Strings, Hashes, Lists, Sets, Sorted Sets
- **학습 자료**:
    - [Redis 데이터 타입](https://redis.io/docs/data-types/)

**Phase 4: Redis 사용하기**

- **목표**: Java, Kotlin, TypeScript 애플리케이션에서 Redis를 연동하는 방법을 학습합니다.
- **키워드**: Jedis, Spring Data Redis, Kotlin, Spring Data Redis, Nest.JS, TypeScript, ioredis
- **학습 자료**:
    - [Spring Boot에서 Redis 캐시 사용하기](https://www.baeldung.com/spring-boot-redis-cache)
    - [Jedis를 사용한 Java에서의 Redis 캐시](https://www.geeksforgeeks.org/redis-cache-in-java-using-jedis/)
    - [Spring Boot와 Kotlin으로 Redis 캐시 구현하기](https://medium.com/%40tharindudulshanfdo/optimizing-spring-boot-applications-with-redis-caching-35eabadae012)
    - [NestJS/Typescript : 캐시 메모리와 레디스(Redis)](https://blex.me/%40laetipark/nestjstypescript-%EC%BA%90%EC%8B%9C-%EB%A9%94%EB%AA%A8%EB%A6%AC%EC%99%80-%EB%A0%88%EB%94%94%EC%8A%A4redis?utm_source=chatgpt.com)

**Phase 5: 캐싱 전략 이해**

- **목표**: 다양한 캐싱 전략과 패턴을 학습합니다.
- **키워드**: Cache-Aside, Read-Through, Write-Through, Write-Behind
- **학습 자료**:
    
    [인메모리 저장소 및 캐싱 전략](Step%2006%20%EB%B6%84%EC%82%B0%EB%9D%BD%EA%B3%BC%20%EC%BA%90%EC%8B%B1%EC%9C%BC%EB%A1%9C%20%ED%8A%B8%EB%9E%98%ED%94%BD%20%EB%8C%80%EC%9D%91/%EC%9D%B8%EB%A9%94%EB%AA%A8%EB%A6%AC%20%EC%A0%80%EC%9E%A5%EC%86%8C%20%EB%B0%8F%20%EC%BA%90%EC%8B%B1%20%EC%A0%84%EB%9E%B5%203168b9f9063f80298c36de76ebcdcbcf.md)
    
    - [Redis를 사용한 쿼리 캐싱](https://redis.io/learn/howtos/solutions/microservices/caching)

**Phase 6: Redis 캐싱 최적화**

- **목표**: Java, Kotlin, TypeScript ****애플리케이션에서 Redis 캐싱을 최적화하는 방법을 학습합니다.
- **키워드**: Spring Cache, 캐시 설정, 성능 최적화
- **학습 자료**:
    - [Spring Boot에서 Redis 캐시 최적화](https://medium.com/%40tharindudulshanfdo/optimizing-spring-boot-applications-with-redis-caching-35eabadae012)
    - [Spring Boot와 Kotlin으로 Redis 캐시 최적화](https://medium.com/%40tharindudulshanfdo/optimizing-spring-boot-applications-with-redis-caching-35eabadae012?utm_source=chatgpt.com)
    - [NestJS에서 Redis로 캐싱하기 1(feat get, set)](https://joojae.com/nestjs-redis-caching-advance/?utm_source=chatgpt.com)
    - [NestJS에서 Redis로 캐싱하기 2 (feat mget, mset, sadd, smembers)](https://joojae.com/nestjs-redis-caching-mget-mset-sadd-smembers/)

Redis 기초 학습 자료입니다. 공식링크도 참고하여 학습을 진행해 주세요 😀

## 챕터1-6 : 과제

<aside>
🚩 **이번 주차 과제**

</aside>

### **`[필수] Distributed Lock`**

- 과제 내용
    - Redis 기반의 분산락을 직접 구현해보고 동작에 대한 통합테스트 작성
    - 주문/예약/결제 기능 등에 **(1)** 적절한 키 **(2)** 적절한 범위를 선정해 분산락을 적용
- 주요 평가 기준
    - 분산락에 대한 이해와 DB Tx 과 혼용할 때 주의할 점을 이해하였는지
    - 적절하게 분산락이 적용되는 범위에 대해 구현을 진행하였는지

### **`[선택] Cache`**

- 과제 내용
    - 조회가 오래 걸리거나, 자주 변하지 않는 데이터 등 애플리케이션의 요청 처리 성능을 높이기 위해 캐시 전략을 취할 수 있는 구간을 점검하고, 적절한 캐시 전략을 선정
    - 위 구간에 대해 Redis 기반의 캐싱 전략을 시나리오에 적용하고 성능 개선 등을 포함한 보고서 작성 및 제출
- 주요 평가 기준
    - 각 시나리오에서 발생하는 Query 에 대한 충분한 이해가 있는지
    - 각 시나리오에서 캐시 가능한 구간을 분석하였는지
    - 대량의 트래픽 발생시 지연이 발생할 수 있는 조회쿼리에 대해 분석하고, 이에 대한 결과를 작성하였는지

<aside>
<img src="https://www.notion.so/icons/light-bulb_red.svg" alt="https://www.notion.so/icons/light-bulb_red.svg" width="40px" />

**참고 PR 링크**

| 스택 | STEP6 |
| --- | --- |
| Java | [https://github.com/BEpaul/hhplus-e-commerce/pull/33](https://github.com/BEpaul/hhplus-e-commerce/pull/33) |
| Java | [https://github.com/juny0955/hhplus-concert/pull/29](https://github.com/juny0955/hhplus-concert/pull/29) |
| Kotlin | [https://github.com/chapakook/kotlin-ecommerce/pull/39](https://github.com/chapakook/kotlin-ecommerce/pull/39) |
| Kotlin | [https://github.com/psh10066/hhplus-server-concert/pull/22](https://github.com/psh10066/hhplus-server-concert/pull/22) |
| TS | [https://github.com/psyoongsc/hhplus-ts-server/pull/47](https://github.com/psyoongsc/hhplus-ts-server/pull/47) |
| TS | [https://github.com/suji6707/nestjs-case-02-ticketing/pull/6](https://github.com/suji6707/nestjs-case-02-ticketing/pull/6) |
</aside>