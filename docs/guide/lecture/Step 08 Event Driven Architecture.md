# Step 08. Event Driven Architecture

## 챕터1-1 : 들어가기에 앞서

## **지난 주차 돌아보기**

## Redis를 활용한 모든 것

![슬라이드2.PNG](Step%2008%20Event%20Driven%20Architecture/%EC%8A%AC%EB%9D%BC%EC%9D%B4%EB%93%9C2.png)

### 캐싱

캐시는 조회 비용이 크거나 자주 변하지 않는 데이터를 빠르게 응답하기 위해 사용하는 전략입니다. 우리는 Redis를 활용해 DB I/O를 줄이고 어플리케이션의 응답속도를 개선해 봤습니다. 

캐시는 대용량 트래픽 상황에서 도입하면 매우 효과적이며, 반드시 비즈니스 로직에 맞는 적절한 캐싱 전략을 적용해야 합니다. 적절하지 않은 캐싱 전략을 사용할 경우 캐싱을 관리하는 비용이 더 많이 발생할 수도 있고, 오염된 데이터를 반환할 수도 있습니다.

### 분산락

분산락은 다건의 요청이 동시에 특정 데이터를 접근하고 수정할 때 발생할 수 있는 “동시성 이슈”를 해결하기 위한 기술입니다. 우리는 Redis를 활용해 분산락을 적용하여 동시성 이슈를 해결해 봤습니다. 비관적락과 낙관적락은 DB Connection을 필요로 하기 때문에 대용량 트래픽을 그대로 데이터베이스까지 전달하지 않는 좋은 완충장치로도 사용할 수 있고, 특정 데이터(row)에 국한되지 않는 동시성 이슈에도 적용할 수 있었습니다.

### Redis 자료구조

String, List, Set, SortedSet, Hash등 다양한 자료구조를 활용해 데이터를 관리하고 다양한 명령어를 학습해봤습니다. 앞으로 Redis를 활용해 어떤 자료구조와 어떤 명령어를 사용하여 데이터를 관리하는게 좋은지 판단하고 설계하고 구현할 수 있어야 합니다.

### Redis를 활용하여 다양한 서비스 구현하기

콘서트 서비스에서는 서비스에 접근하려는 수많은 사용자들을 그대로 허용하지 않고, 일정한 트래픽만을 허용하도록 대기열을 Redis로 구현해 데이터베이스의 트래픽을 최소화 해보았습니다.

이커머스 서비스에서는 동일한 쿠폰을 동시에 발급받기를 원하는 수많은 요청을 데이터베이스의 부하 없이 Redis를 활용해 발급이 가능한 요청만을 발급 시도하여 데이터베이스의 부하를 최소화 해보았습니다.

<aside>
💡 대용량 트래픽에 관련된 면접에서 가장 많이 질문하는 서비스가 바로 이 Redis입니다. 
앞으로는 Redis를 단순하게 캐시 정도로만 활용하지 말고, 대용량 트래픽을 처리하는 다양한 방법으로 고도화해서 알차게 활용합시다. 
난이도가 높은 대용량 트래픽 관련 면접 질문에서도 기똥차게 답변해보아요.

</aside>

## 챕터1-2 : 이번 강의에서 배울 것

## 이번 주차에서 배울 것

![슬라이드3.PNG](Step%2008%20Event%20Driven%20Architecture/%EC%8A%AC%EB%9D%BC%EC%9D%B4%EB%93%9C3.png)

## 이벤트를 활용한 관심사 및 트랜잭션 분리

- 현재 여러분들이 구현한 비즈니스 로직 별 트랜잭션의 범위를 파악하고 사이드 이펙트에 대해 고려해 봅니다.
- 비즈니스를 적절하게 핸들링할 수 있도록 선후관계를 파악하고, 애플리케이션 이벤트를 활용해 관심사를 분리하도록 개선해 봅니다.
- 도메인간 트랜잭션이 분리된다면 발생할 수 있는 문제와 해결하는 방법을 학습해봅시다.

## 챕터1-3 : 트랜잭션 범위

### 비즈니스 로직과 트랜잭션의 범위

![슬라이드5.PNG](Step%2008%20Event%20Driven%20Architecture/%EC%8A%AC%EB%9D%BC%EC%9D%B4%EB%93%9C5.png)

<aside>
💡

앞서 우리는 동시성 제어를 학습하면서 시스템의 부하를 최소화하기 위해 락의 범위를 `무결성이 보장되는 수준에서 가장 최소화`해야한다고 배웠습니다. 이는 트랜잭션을 최소화하기 위함인데요, 락의 범위 뿐만 아니라, 무분별한 비즈니스 로직과 트랜잭션 규모 또한 우리가 예측하지 못한 문제를 발생시킬 수 있습니다.

마주할 수 있는 문제 상황을 이해하고 해결 방법을 알아봅시다.

</aside>

<aside>
🔥 **문제 상황 1.** 하나의 트랜잭션이 너무 많은 작업 혹은 느린 조회 등을 처리하는 경우

</aside>

![슬라이드6.PNG](Step%2008%20Event%20Driven%20Architecture/%EC%8A%AC%EB%9D%BC%EC%9D%B4%EB%93%9C6.png)

![Untitled](Step%2008%20Event%20Driven%20Architecture/Untitled.png)

- **어떤 문제로 이어질 수 있을까?**
    - 다수의 `SlowRead` 작업으로 인해 요청 처리에 영향을 줄 수 있음
    - Transaction 범위 내에서 Lock 을 사용하고 있을 경우, 해당 자원에 접근하는 다른 요청의 대기 혹은 데드락 상황을 유발할 수 있음
    - 긴 생명 주기의 Transaction 의 경우, 오랜 시간은 소요되나 후속 작업에 의해 전체 트랜잭션이 실패할 수 있음

<aside>
🔥 **문제 상황 2.** 트랜잭션 범위 내에서 DB 와 무관한 작업을 수행하고 있는 경우

</aside>

![슬라이드7.PNG](Step%2008%20Event%20Driven%20Architecture/%EC%8A%AC%EB%9D%BC%EC%9D%B4%EB%93%9C7.png)

![Untitled](Step%2008%20Event%20Driven%20Architecture/Untitled%201.png)

- **어떤 문제로 이어질 수 있을까?**
    - API 요청과 같은 DB 외적인 작업이 오래 걸리게 되어 Transaction 이 길어지는 문제
    - DB 외적인 작업의 실패가 Transaction 의 범위로 전파되어 전체 비즈니스 로직이 `rollback` 되는 문제
        - 만약, external API가 실패하더라도 우리의 비즈니스는 정상적으로 성공시켜도 되는 요구사항이라면?
    - external API의 타임아웃으로 트랜잭션을 롤백시켰으나, external 서비스에서는 사실 정상적으로 처리되었을때 무결성을 잃게 되는 문제

### 실제 사례 살펴보기

![슬라이드8.PNG](Step%2008%20Event%20Driven%20Architecture/%EC%8A%AC%EB%9D%BC%EC%9D%B4%EB%93%9C8.png)

<aside>
❓ **실시간 주문정보 전달 (이커머스 시나리오)**
데이터 분석을 위해 결제 성공 시에 실시간으로 주문 정보를 데이터 플랫폼에 전송해야 합니다. 

콘서트 예약에서도 동일한 요구사항을 추가해봅시다. 
데이터 분석을 위해 결제 성공 시에 실시간으로 좌석예약 정보를 데이터 플랫폼에 전송해야 합니다.

</aside>

### 원래의 우리의 코드..

```jsx
class OrderPaymentService {
	Transaction {
		fun 주문_결제() {
			유저_포인트_차감();
			결제_정보_저장();
			주문_상태_변경();
			주문_정보_전파();
		}
	}
}
```

주문 결제 메서드에서 핵심 로직과 부가 로직을 나눠본다면 다음과 같습니다.

- 핵심 로직
    - 유저 포인트 차감
    - 결제 정보 저장
    - 주문 상태 변경
- 부가 로직
    - 주문 정보 전파

이 코드에서는 다음과 같은 상황이 발생할 수 있습니다.

- 주문 정보 전달에 **실패**하면 결제의 모든 프로세스가 실패합니다.
- 주문 정보 전달이 **오래걸리면** 결제의 모든 프로세스가 오래 걸립니다.

**부가 로직**은 **핵심 로직**에 영향을 끼치지 않도록 구현하는 것이 좋겠습니다. 

그럼 우리는 어떻게 변경해야 할까요?

### 개선된 우리의 코드 !!

```jsx
class OrderPaymentService {

	fun 주문_결제() {
		execute_main();
		Async {
			try {
				주문_정보_전파();
			} catch(Exception e) {
				log.warn("[!] 부가로직 수행 에러 발생")
			}
		}
	}

	Transaction {
		private fun execute_main() {
			유저_포인트_차감();
			결제_정보_저장();
			주문_상태_변경();
		}
	}
}
```

이제는 **부가 로직**이 **핵심 로직**에 영향을 끼치지 않도록 구현했습니다.

- 주문 정보 전달이 실패하더라도 `warn` 로그와 함께 정상적으로 주문이 완료됩니다.
- 비동기(`Async`)로 처리하여 주문 정보 전달이 오래걸리더라도 결제 프로세스는 먼저 완료됩니다.

자, 어떤가요? 괜찮은가요? 

우리의 코드는 원하는 대로 “정확하게” 동작하여 서비스는 정확하게 동작합니다.

하지만,  “**가독성**” 관점에서 좋은 코드라고 볼 수 없습니다. 왜냐하면, 부가 로직이 핵심 로직에 영향을 주지 않도록 작성한 코드가 더 중요하게 구현되어 핵심 로직을 이해하기 어렵기 때문입니다.

만약, 결제완료 알림톡 등의 부가 로직이 추가된다면..?

오늘 우리가 학습할 “이벤트”라는 친구가 필요한 이유가 여기에 있습니다.

## 챕터1-4 : 이벤트

### 애플리케이션 이벤트를 통한 관심사 분리

<aside>
💡

단순히 위 문제 뿐만 아니라, 우리의 비즈니스 로직은 과도한 책임이 주어져 있을 수 있습니다. 검증도 하고, 재고도 차감하고, 주문도 만들고, 결제도 하는 등 너무 많은 관심사를 하나의 작업으로 처리하고 있기 때문에 위에서 살펴본 문제들이 발생할 수 있는 것이죠.

</aside>

![슬라이드10.PNG](Step%2008%20Event%20Driven%20Architecture/%EC%8A%AC%EB%9D%BC%EC%9D%B4%EB%93%9C10.png)

![슬라이드11.PNG](Step%2008%20Event%20Driven%20Architecture/%EC%8A%AC%EB%9D%BC%EC%9D%B4%EB%93%9C11.png)

### Application Event

<aside>
📚 **Library & Keyword**

**`Spring` : ApplicationEventPublisher & EventListener / TransactionalEventListener
`nest.js` : event-emitter**

</aside>

![Untitled](Step%2008%20Event%20Driven%20Architecture/Untitled%202.png)

**Event 기반 흐름 제어**

- `Event` 를 발행 및 구독하는 모델링을 통해 코드의 강한 결합을 분리함
- `Event` 에 의해 본인의 관심사만 수행하도록 하여 비즈니스 로직간의 의존을 느슨하게 함

**활용 방안**

- 비대해진 트랜잭션 내의 각 작업을 작은 단위의 트랜잭션으로 분리할 수 있음
- 특정 작업이 완료되었을 때, 후속 작업이 이벤트에 의해 trigger 되도록 구성함으로써 과도하게 많은 비즈니스 로직을 알고 있을 필요 없음
- 트랜잭션 내에서 외부 API 호출 ( e.g. DB 작업 등 ) 의 실패나 수행이 주요 비즈니스 로직 ( 트랜잭션 ) 에 영향을 주지 않도록 구성할 수 있음

### Implementation

이벤트를 활용해서 단순히 **부가 로직**을 분리할 뿐만 아니라, 여러 도메인이 결합하여 구성된 **핵심 로직**을 각 도메인별로 관심사와 트랜잭션을 분리하도록 구성할 수도 있습니다.

![Untitled](Step%2008%20Event%20Driven%20Architecture/Untitled%203.png)

**동작 순서**

1. service 1 수행
2. service 1 완료 이벤트 발행
3. service 1 완료 이벤트에 대한 리스너가 본인의 비즈니스(service2_1 & service2_2) 수행

**주의할 점**

- 각 작업의 논리적 의존이나 관계를 잘 파악해야 함
- 만약 이벤트에 의해 파생된 작업이 실패하였을 때, 원본 작업 또한 실패 처리를 해야 한다면 이를 위한 처리가 필요함 ( **keyword : `보상 트랜잭션, SAGA 패턴`** )
- 이벤트에 의해 각 작업이 영향을 주고 있는지, 혹은 이벤트가 발생하면 안되는 상황에서 이벤트가 발행되고 있지는 않은지 등

### 진짜 개선된 우리의 코드

![슬라이드12.PNG](Step%2008%20Event%20Driven%20Architecture/%EC%8A%AC%EB%9D%BC%EC%9D%B4%EB%93%9C12.png)

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
		  // Notify Service
			fun 주문_정보_전파(결제_완료_이벤트)
		}
	}
	
	TransactionalEventListener(AFTER_COMMIT) {
		Async {
			fun 결제_완료_알림톡_발송(결제_완료_이벤트)
		}
	}
}
```

- OrderPaymentService에서 **핵심 로직**의 가독성이 훨씬 좋아졌습니다.
- 결제 완료에 대한 부가 로직에 대한 **관심사를 분리**했습니다.

이로써 우리는 “**이벤트**”를 통해 핵심 로직과 부가 로직을 나누어 트랜잭션을 분리할 뿐만 아니라, 관심사를 분리하여 코드의 가독성도 향상시켰습니다.

### Event - Sample Code

<aside>
⚠️ **주의**
하단에 제공되는 코드는 Event 기반 제어를 표현하는 데에 집중하였으므로 모든 완성 기능을 제공하는 것이 아닙니다. 이벤트 객체/발행/구독 에 관련된 예시 코드를 제공하는 데에 집중하고 있으므로 이를 유념해주시기 바랍니다.

</aside>

### Spring

### Java

```java
// 이벤트 객체
public class PaymentSuccessEvent {
    private final String orderKey;
    private final String paymentKey;

    public PaymentSuccessEvent(String orderKey, String paymentKey) {
        this.orderKey = orderKey;
        this.paymentKey = paymentKey;
    }

    public String getOrderKey() {
        return orderKey;
    }

    public String getPaymentKey() {
        return paymentKey;
    }
}
// 이벤트 발행서비스
@Component
public class PaymentEventPublisher {
    private final ApplicationEventPublisher applicationEventPublisher;

    public PaymentEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public void success(PaymentSuccessEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}
// 이벤트 구독서비스
@Component
public class PaymentEventListener {
    private final DataPlatformSendService sendService;

    public PaymentEventListener(DataPlatformSendService sendService) {
        this.sendService = sendService;
    }
		// 비동기로 이벤트 발행주체의 트랜잭션이 커밋된 후에 수행한다.
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void paymentSuccessHandler(PaymentSuccessEvent event) {
        // (4) 주문 정보 전달
        PaymentSuccessPayload payload = new PaymentSuccessPayload(event);
        this.sendService.send(payload);
    }
}
// 비즈니스 로직
@Service
public class PaymentService {
    private final RequestValidator requestValidator;
    private final UserService userService;
    private final OrderService orderService;
    private final PaymentRepository paymentRepository;
    private final PaymentEventPublisher eventPublisher;

    public PaymentService(RequestValidator requestValidator, UserService userService, OrderService orderService, PaymentRepository paymentRepository, PaymentEventPublisher eventPublisher) {
        this.requestValidator = requestValidator;
        this.userService = userService;
        this.orderService = orderService;
        this.paymentRepository = paymentRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void pay(PaymentRequest request) {
        // (1) 결제 요청 검증
        requestValidator.validate(request);
        // (2) 유저 포인트 차감
        User user = userService.getWithLock(request.getUserId());
        orderService.check(request.getOrderKey(), user.getId());
        user.usePoint(request.getAmount());
        // (3) 결제 정보 저장
        Payment payment = paymentRepository.save(new Payment(request));
        // 결제 성공 이벤트 발행
        eventPublisher.success(new PaymentSuccessEvent(payment.getOrderKey(), payment.getKey()));
    }
}
```

### Kotlin

```kotlin
// 이벤트 객체
data class PaymentSuccessEvent(val orderKey: String, val paymentKey: String)
// 이벤트 발행서비스
@Component
class PaymentEventPublisher(
	private val applicationEventPublisher: ApplicationEventPublisher,
) {
	fun success(event: PaymentSuccessEvent) {
		applicationEventPublisher.publishEvent(event)
	}
}
// 이벤트 구독서비스
@Component
class PaymentEventListener(
	private val sendService: DataPlatformSendService
) {
	@Async
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	fun paymentSuccessHandler(event: PaymentSuccessEvent) {
		// (4) 주문 정보 전달
		val payload = PaymentSuccessPayload(event)
		sendService.send(payload)
	}
}
// 비즈니스 로직
@Service
class PaymentService(
	..
	private val eventPublisher: PaymentEventPublisher,
) {
	@Transactional
	fun pay(request: PaymentRequest) {
		// (1) 결제 요청 검증
		requestValidator.validate(request)
		// (2) 유저 포인트 차감
		val user = userService.getWithLock(request.userId)
		orderService.check(request.orderKey, user.id)
		user.usePoint(request.amount)
		// (3) 결제 정보 저장
		val payment = paymentRepository.save(Payment(request))
		// 결제 성공 이벤트 발행
		eventPublisher.success(PaymentSuccessEvent(order.key, payment.key))
	}
}
```

### Nest.js

<aside>
⚠️ event-emitter 의 `@OnEvent` 의 경우, 핸들링하는 로직에서 Exception 발생 시 nest.js 의 Context 를 벗어나므로 Exception Handling 이 불가능합니다. 이에 아래 제공하는 `@OnEventSafe` 데코레이터 코드를 추가하여 이를 이용해 EventListener 를 구현해주세요.

</aside>

```tsx
// on-event-safe.decorator.ts
import { applyDecorators, Logger } from '@nestjs/common'
import { OnEvent, OnEventType } from '@nestjs/event-emitter'
import { OnEventOptions } from '@nestjs/event-emitter/dist/interfaces'

function _OnEventSafe() {
  return function (target: any, key: string, descriptor: PropertyDescriptor) {
    const originalMethod = descriptor.value

    const metaKeys = Reflect.getOwnMetadataKeys(descriptor.value)
    const metas = metaKeys.map((key) => [key, Reflect.getMetadata(key, descriptor.value)])

    descriptor.value = async function (...args: any[]) {
      try {
        await originalMethod.call(this, ...args)
      } catch (err) {
        Logger.error(err, err.stack, 'OnEventSafe')
      }
    }
    metas.forEach(([k, v]) => Reflect.defineMetadata(k, v, descriptor.value))
  }
}

export function OnEventSafe(event: OnEventType, options?: OnEventOptions | undefined) {
  return applyDecorators(OnEvent(event, options), _OnEventSafe())
}
```

### Typescript

```tsx

export class PaymentSuccessEvent {
  constructor(public orderKey: string, public paymentKey: string) {}
}

@Injectable()
export class PaymentEventPublisher {
	constructor(private eventEmitter: EventEmitter2) {}
	
	async success(event: PaymentSuccessEvent) {
		this.eventEmitter.emit('payment.success', event)
	}
}

@Injectable()
export class PaymentEventListener {
	constructor(private readonly sendService: DataPlatformSendService) {}
	
	@OnEventSafe('payment.success')
	async paymentSuccessHandler(event: PaymentSuccessEvent) {
		const payload = new PaymentSuccessPayload(event)
		sendService.send(payload)
	}
}

@Injectable()
export class PaymentService {
	constructor(
		..
		private dataSource: DataSource,
		private readonly paymentEventPublisher: PaymentEventPublisher,
	) {}
	
	async pay(request: PaymentRequest) {
		await this.dataSource.transaction(async transactionManager =>  {
			// (1) 결제 요청 검증
			requestValidator.validate(request)
			// (2) 유저 포인트 차감
			const user = await userService.getWithLock(transactionManager, request.userId)
			await orderService.check(transactionManager, request.orderKey, user.id)
			await user.usePoint(transactionManager, request.amount)
			// (3) 결제 정보 저장
			const payment = await paymentRepository.save(transactionManager, Payment(request))
			// 결제 성공 이벤트 발행
			eventPublisher.success(PaymentSuccessEvent(order.key, payment.key))
		})
	}
}
```

```
하나의 큰 트랜잭션에서 결제, 재고 차감을 한다면 복잡한 구현 없이 트랜잭션 처리로 롤백이 가능해요.

결제는 결제대로, 재고는 재고대로 로직을 수행해야 하죠.

결제가 성공하고 해당 이벤트(결제완료)를 발행하면
재고 도메인이 해당 이벤트를 수집해서 재고를 차감해요.

이때 재고가 없는 케이스에서는 주문을 실패처리해요. 

근데 이미 위에 트랜잭션은 
커밋이 완료된 상황에서는 '재고 차감에 실패했어요~' 라는 이벤트(재고차감 실패)를
다시 발행해서 주문 서비스에 해당 주문의 취소, 실패 등의 상태로 변경해야해요.
```

### 확장하여 생각해보기: 이벤트를 통해 구성하는 MSA 아키텍처

![슬라이드13.PNG](Step%2008%20Event%20Driven%20Architecture/%EC%8A%AC%EB%9D%BC%EC%9D%B4%EB%93%9C13.png)

> 이벤트는 단지 비동기 처리 도구가 아니라, **서비스 간 약한 결합을 통해 유연한 시스템을 설계하는 아키텍처 전략**
> 

**단일 서비스 내부에서의 이벤트**

- 우리는 지금까지 **하나의 서비스 내부**에서
    - 핵심 로직과 부가 로직을 분리하고
    - 트랜잭션 경계를 줄이기 위한 수단으로 이벤트를 사용했습니다.

**그러나, MSA 환경에서는?**

- MSA 아키텍처에서는 하나의 비즈니스 흐름이 **여러 서비스에 걸쳐 분산**되어 있습니다.
- 서비스 간 강한 결합 없이 협력하려면 어떻게 해야 할까요?

→ 바로 **이벤트 기반 통신(Event-driven architecture)**이 해답!

**하나의 서비스에서 이벤트를 발행하면, 관심 있는 다른 서비스가 이를 구독하여 상태를 반영**

- `OrderService`에서 "주문 완료" 이벤트 발행
- `InventoryService`는 해당 주문 정보를 기반으로 재고 차감
- `NotificationService`는 사용자에게 알림톡 발송

**⚠️ 주의할 점**

- 흐름이 명시적이지 않아서 **전체 프로세스를 한눈에 파악하기 어려움**
- 이벤트가 실패하거나 중복될 경우 대응이 필요 → **보상 트랜잭션 / SAGA 패턴** 고려
- 테스트와 디버깅이 어려워질 수 있음

## 챕터1-4 : 이벤트

## 이번 주차 과제

### **`필수과제 Application Event`**

- 실시간 주문정보(이커머스) & 예약정보(콘서트)를 데이터 플랫폼에 전송(mock API 호출)하는 요구사항을 이벤트를 활용하여 트랜잭션과 관심사를 분리하여 서비스를 개선합니다.
    
    ![슬라이드15.PNG](Step%2008%20Event%20Driven%20Architecture/%EC%8A%AC%EB%9D%BC%EC%9D%B4%EB%93%9C15.png)
    

### **`선택과제 Transaction Diagnosis`**

- 우리 서비스의 규모가 확장되어 MSA의 형태로 각 도메인별로 배포 단위를 분리해야한다면 각각 어떤 도메인으로 배포 단위를 설계할 것인지 결정하고, 그 분리에 따른 트랜잭션 처리의 한계와 해결방안에 대한 서비스 설계 문서 작성하여 제출합니다.
    
    ![슬라이드16.PNG](Step%2008%20Event%20Driven%20Architecture/%EC%8A%AC%EB%9D%BC%EC%9D%B4%EB%93%9C16.png)
    

### Try if you want

보상트랜잭션, Saga 패턴 등 활용하여 우리의 프로젝트를 고도화 해봅시다.

- Facade 활용한다면 트랜잭션을 도메인 단위로 분리하고 발생하는 분산 트랜잭션을 올바르게 구현하기
- Facade 없이 서비스간 의존하는 구조라면 어플리케이션 이벤트를 활용하여 각 서비스 의존을 없애기

<aside>
<img src="https://www.notion.so/icons/light-bulb_red.svg" alt="https://www.notion.so/icons/light-bulb_red.svg" width="40px" />

**참고 PR 링크**

| 스택 | STEP8 |
| --- | --- |
| Java | [https://github.com/BEpaul/hhplus-e-commerce/pull/41](https://github.com/BEpaul/hhplus-e-commerce/pull/41) |
| Java | [https://github.com/juny0955/hhplus-concert/pull/35](https://github.com/juny0955/hhplus-concert/pull/35) |
| Kotlin | [https://github.com/chapakook/kotlin-ecommerce/pull/49](https://github.com/chapakook/kotlin-ecommerce/pull/49) |
| Kotlin | [https://github.com/psh10066/hhplus-server-concert/pull/26](https://github.com/psh10066/hhplus-server-concert/pull/26) |
| TS | [https://github.com/psyoongsc/hhplus-ts-server/pull/51](https://github.com/psyoongsc/hhplus-ts-server/pull/51) |
| TS | [https://github.com/suji6707/nestjs-case-02-ticketing/pull/8](https://github.com/suji6707/nestjs-case-02-ticketing/pull/8) |
</aside>