# Step 03. Clean Architecture

## 들어가기에 앞서

### **지난 강의 리마인드**

<aside>
💡

**지난 강의 한눈에 리마인드**

</aside>

- **1) 서버 설계의 정의 & 필수 6대 요소**
    - **서버 설계란?**
        - 서비스가 실제로 **어디에서·어떻게** 구동되고, **무엇을 기준으로** 확장·운영될지  **그려 두는 설계도**
    - **필수 고려 6요소**
        - **성능** (Performance) – 요청-응답 지연 최소화
        - **확장성** (Scalability) – 사용자/트래픽 증가에 비례해 선형 확장
        - **가용성** (Availability) – 장애 상황에서도 서비스 지속
        - **유지보수성** (Maintainability) – 변경 비용 ↓, 코드/인프라 가독성 ↑
        - **보안성** (Security) – 전송·저장 데이터 보호, 접근 통제
        - **비용 효율성** (Cost Efficiency) – “필요할 땐 쓰고, 아닐 땐 닫는다” 전략
- **2) 사전 리서치 & 요구사항 정의**
    - **기능적 요구사항** – “무엇(What)을 제공해야 하나?”
        - 예: 상품 목록 조회, 주문 생성, 결제, 회원가입 …
    - **비기능적 요구사항** – “어떻게(How) 돌아가야 하나?”
        - 성능 SLA(예: 95% 요청 100 ms 이내), 인증/권한, 장애 MTTR, 로그 보존주기 …
    - **트래픽 예측** – DAU·동접·QPS 추산 → 서버 수/DB 스펙/캐시 전략 결정
        - **현실 제약 파악** – 팀 역량, 예산, 데드라인, 레거시 연동
- **3) 설계 산출물 & 문서화 체크리스트**
    
    
    | **산출물** | **목적** |
    | --- | --- |
    | **시퀀스 다이어그램** | 시스템/모듈 간 **동작 흐름** 시간순 정리 |
    | **ERD** | 테이블 관계 및 데이터 흐름 **시각화** |
    | **상태 다이어그램** | 중요한 **도메인 상태 전이** 명확화 |
    | **API 명세 (OAS/Swagger)** | 팀·외부 협업을 위한 **계약서 & Mock API** |
    | **인프라 구성도** | 로드밸런서·캐시·DB·큐 등 **전체 톱다운 뷰** |
    | **Milestone & Issue 템플릿** | 일정 관리·우선순위·PR 품질 관리 |
- **4) “놓치기 쉬운 3대 영역”**
    - **보안** – HTTPS·JWT·암호화, WAF·VPC Segmentation, 키·시크릿 관리
    - **로깅/모니터링** – 구조화 로그(JSON) + Prometheus/Grafana 알람 → **가시성 확보**
    - **장애 대응** – 이중화·백업 + Incident Process + Post-mortem 문화 **= 운영 근육**
- **5) 실무형 인터뷰 질문  (각자 답변을 생각해보고 학습해보세요~!)**
    - “TPS가 10배 튀면 어떤 레이어부터 병목을 의심하나요?”
    - “캐시 일관성과 최신성, 무엇을 우선할 건가요
    - “JWT 만료 후 재발급 전략을 어떻게 잡겠습니까?”
    - “Postmortem 문서에 반드시 들어가야 할 항목은?”

## 이번 강의에서 배울 것

### 혹시 이런 경험 있으신가요?

- 새로운 기능을 개발하는데 처음엔 잘 나가다가 갈수록 코드가 꼬여버리는 상황 말이에요. 예를 들어 볼까요?
    - 요구사항 파악: 만들 기능 목록은 정리했는데
    - 구현하다 막힘: “이번엔 우아한 코드 짜보자!” 의욕적으로 하루 종일 코드를 짭니다. 하지만 짜다 보니 코드가 점점 우아하지 않게 꼬입니다.
    - 시간 부족: 수정 또 수정… 그러다 보니 마감 시한이 코앞! 결국 야근하며 임시방편 코드를 덕지덕지 붙입니다.
    - 팀장님의 호출: …
- 위와 같은 개발 과정의 악순환을 겪은 적 있다면, 소프트웨어 아키텍처를 한 번 돌아볼 때
- 많은 프로젝트들이 초기엔 빨리 개발되지만, 시간이 지날수록 새로운 기능 추가가 점점 어려워지고 속도가 느려지는 현상을 겪음
- 원인은 코드 구조가 뒤엉키고 빚만 늘어나기 때문
- 좋은 아키텍처는 이러한 속도 저하를 줄여주거나, 오히려 시간이 지날수록 개발을 더 빠르게 해줄 수도 있음
- 잘 구조화된 컴포넌트는 레고 블럭처럼 새로운 기능을 빨리 조립할 수 있게 해줌

### **소프트웨어 아키텍처란 무엇일까요?**

- 간단히 말하면 **코드 구성에 대한 큰 그림 설계**
- 국제 표준 ANSI/IEEE 1471-2000에서는 소프트웨어 아키텍처를 “시스템을 구성하는 요소(클래스, 파일, 컴포넌트, 모듈 등)들과 그들 사이의 관계 및 상호작용을 설계하는 것”으로 정의
- 유명한 객체지향 대가 랄프 존슨은 “모든 팀원이 공유하는 시스템에 대한 이해이며, 쉽게 바꾸기 어려운 것들”이 아키텍처라고 이야기함

![image.png](Step%2003%20Clean%20Architecture/image.png)

- 두 관점을 종합하면, 아키텍처란 그 프로젝트에서 가장 중요해서 잘못 바꾸면 큰 문제가 되는 결정들이라고 할수 있음
- 어떤 것이 중요한지는 팀과 프로젝트에 따라 다르지만, **“코드 구조에 관한 팀의 공통 이해”**라고 정리할 수 있음

### **왜 아키텍처가 중요할까요?**

- 눈에 보이는 기능 구현도 바쁜데 구조까지 신경써야 할까 생각도 들지만, 앞서 언급했듯이, 아키텍처는 **소프트웨어의 속도와 품질에 장기적인 영향**을 미침
- 초기에는 대충 짜도 돌아가지만, **구조가 부실한 채로 기능만 추가하다 보면** 시간이 지날 수록 퍼포먼스가 급격하게 떨어짐
- **탄탄한 설계**를 해두면 나중에 기능을 추가할 때도 수월하고, 버그 수정이나 변경에도 여유가 생김
- 정리하면
    - 많은 프로젝트는 처음엔 빨리 개발되나, **시간이 지날수록 변경이 어려워져 개발 속도가 감소**
    - **좋은 아키텍처**는 이러한 속도 저하를 최소화하고, **변경이 쉬운 구조** 덕분에 오히려 개발이 가속화될 수도 있음 (기존 컴포넌트를 재사용/조립하여 빠르게 새 기능을 만들 수 있기 때문)
- 하지만 좋은 아키텍처나 설계는 **겉으로 바로 드러나지 않기 때문에,** 개발자들은 눈앞의 기능 구현에 치중하고, 구조에는 소홀해지기 쉬움
- 이번 강의에서는 **거시적인 관점**에서 코드 구조를 돌아보고, 왜 그런 **설계 원칙**들이 필요한지 공감할 수 있도록 알아봅시다.

💬 **궁금증:** “클린 아키텍처, 헥사고날 아키텍처… 이런 패턴들을 전부 다 알아야 할까요?”

🙂 **답:** 모든 패턴의 세부 구현을 당장 숙달할 필요는 없지만, **다양한 아키텍처 패턴들이 제안하는 원칙**을 이해하는 건 중요합니다. 우리가 만드는 서비스는 한번 만들고 끝이 아니라 **지속적으로 성장하고 변화**해야 합니다. 자율적으로 개발하고 유지보수하기 위해, 그리고 **코드의 유연성과 가독성**을 높이기 위해 여러 가지 **설계 규칙**을 도입하게 됩니다. 레이어드, 헥사고날, 클린 같은 패턴들은 그러한 규칙들의 **모음집**이라고 보면 됩니다. 결국 목표는 **“우리 팀만의 좋은 아키텍처 원칙”**을 세우는 것이고, 여러 패턴의 철학을 알아두면 상황에 맞는 규칙을 선택하고 응용할 수 있습니다

![image.png](Step%2003%20Clean%20Architecture/image%201.png)

<aside>
⛵

**이번 주차 목표**

</aside>

- 이번 강의의 목표는 **아키텍처 설계의 기본 원칙**부터 살펴보고, 이후에 주요 아키텍처 패턴과 **클린 아키텍처의 실제 적용**까지 차근차근 알아보는 것입니다.
- 이번 강의의 주요 내용
    - **소프트웨어 아키텍처의 중요성과 역할을 이해**
    - **좋은 설계의 기본 원칙을 습득**
    - **TDD가 아키텍처와 설계에 미치는 영향을 경험**
    - **주요 아키텍처 패턴을 비교 분석**
    - **계층형 구조에 클린 아키텍처 원칙을 적용하는 실무 사례를 학습**

## **아키텍처 설계 원칙과 기본기 (SOLID, TDD의 영향)**

### **좋은 설계의 기본: SOLID 원칙**

- 소프트웨어 아키텍처의 토대에는 **객체지향 설계 5원칙, SOLID**가 자리잡고 있음
- **SOLID 원칙**을 잘 지키는 것이 결국 **유지보수하기 쉬운 구조**의 시작이기 때문
- SOLID는 다음 다섯 가지의 약어
    - **SRP (단일 책임 원칙)** – 하나의 클래스,  모듈은 **한 가지 책임**만 가져야 합니다. 한 클래스가 여러 역할을 하면 변경에 취약해지고 테스트도 어려워집니다(응집도를 높이고 결합도를 낮추는 원칙과 일맥상통합니다.)
    
    **예시 – Spring Service를 책임별로 분리**
        
        ```java
        // 주문 비즈니스 로직 전담
        @Service
        public class OrderService {
        
            private final OrderRepository orderRepository;
        
            public OrderService(OrderRepository orderRepository) {
                this.orderRepository = orderRepository;
            }
        
            public Order placeOrder(CreateOrderCommand cmd) {
                // 주문 생성과 검증만 담당
                return orderRepository.save(cmd.toEntity());
            }
        }
        
        // 결제 비즈니스 로직 전담
        @Service
        public class PaymentService {
        
            private final PaymentGateway paymentGateway;
        
            public PaymentService(PaymentGateway paymentGateway) {
                this.paymentGateway = paymentGateway;
            }
        
            public PaymentReceipt pay(Order order) {
                // 결제 처리만 담당
                return paymentGateway.charge(order.totalPrice());
            }
        }
        ```
        
        - NestJS
            
            ```java
            // src/order/order.service.ts
            import { Injectable } from '@nestjs/common';
            import { PrismaService } from '../prisma/prisma.service';
            import { CreateOrderDto } from './dto/create-order.dto';
            import { Order } from '@prisma/client';
            
            @Injectable()
            export class OrderService {
              constructor(private readonly prisma: PrismaService) {}
            
              async placeOrder(dto: CreateOrderDto): Promise<Order> {
                if (dto.quantity <= 0) {
                  throw new Error('수량은 0보다 커야 합니다.');
                }
            
                return this.prisma.order.create({
                  data: {
                    productName: dto.productName,
                    quantity: dto.quantity,
                    unitPrice: dto.unitPrice,
                    orderedAt: new Date(),
                  },
                });
              }
            }
            
            // src/payment/payment.service.ts
            import { Injectable } from '@nestjs/common';
            import { PaymentGateway } from './gateway/payment-gateway.interface';
            import { PaymentReceipt } from './dto/payment-receipt.dto';
            import { Order } from '@prisma/client';
            
            @Injectable()
            export class PaymentService {
              constructor(private readonly paymentGateway: PaymentGateway) {}
            
              async pay(order: Order): Promise<PaymentReceipt> {
                return this.paymentGateway.charge(order.totalPrice);
              }
            }
            
            ```
            
    - **OCP (개방-폐쇄 원칙)** – **확장에는 열려 있고 변경에는 닫혀 있어야** 합니다. 새로운 기능을 추가할 때 기존 코드를 수정하지 않고도 확장이 가능해야 한다는 뜻입니다. 예를 들어 if/else로 타입별 분기를 추가하기보단 다형성을 활용해 쉽게 새 케이스를 추가하도록 설계합니다.
    
    **예시 – 알림 전략을 빈 주입으로 확장**
        
        ```java
        // 확장을 위한 추상화
        public interface Notifier {
            void notify(User target, String message);
        }
        
        // 이메일 알림
        @Component
        public class EmailNotifier implements Notifier {
            @Override
            public void notify(User target, String message) {
                // 이메일 전송 로직
            }
        }
        
        // 슬랙 알림
        @Component
        public class SlackNotifier implements Notifier {
            @Override
            public void notify(User target, String message) {
                // 슬랙 전송 로직
            }
        }
        
        // 새로운 알림 수단을 추가하려면 알림 구현체만 하나 더 만들면 된다.
        // 기존 OrderService는 전혀 수정하지 않는다.
        @Service
        public class OrderNotificationService {
        
            private final List<Notifier> notifiers;   // 모든 알림 빈이 자동 주입
        
            public OrderNotificationService(List<Notifier> notifiers) {
                this.notifiers = notifiers;
            }
        
            public void announceOrderCreated(Order order) {
                User buyer = order.buyer();
                for (Notifier notifier : notifiers) {
                    notifier.notify(buyer, "주문이 완료되었습니다!");
                }
            }
        }
        ```
        
        - NestJS
            
            ```tsx
            // src/notification/email-notifier.service.ts
            import { Injectable } from '@nestjs/common';
            
            @Injectable()
            export class EmailNotifier {
              async notify(target: string, message: string): Promise<void> {
                // 실제 이메일 전송 로직이 들어갈 부분
                console.log(`[EMAIL] To: ${target} / Message: ${message}`);
              }
            }
            
            // src/notification/slack-notifier.service.ts
            import { Injectable } from '@nestjs/common';
            
            @Injectable()
            export class SlackNotifier {
              async notify(target: string, message: string): Promise<void> {
                // 실제 슬랙 메시지 전송 로직이 들어갈 부분
                console.log(`[SLACK] To: ${target} / Message: ${message}`);
              }
            }
            
            // src/notification/order-notification.service.ts
            import { Injectable } from '@nestjs/common';
            import { EmailNotifier } from './email-notifier.service';
            import { SlackNotifier } from './slack-notifier.service';
            
            @Injectable()
            export class OrderNotificationService {
              constructor(
                private readonly emailNotifier: EmailNotifier,
                private readonly slackNotifier: SlackNotifier,
              ) {}
            
              async announceOrderCreated(userEmail: string): Promise<void> {
                // 각 알림 채널에 동일한 메시지를 전송
                await this.emailNotifier.notify(userEmail, '주문이 완료되었습니다!');
                await this.slackNotifier.notify(userEmail, '주문이 완료되었습니다!');
              }
            }
            
            // src/notification/notification.module.ts
            import { Module } from '@nestjs/common';
            import { EmailNotifier } from './email-notifier.service';
            import { SlackNotifier } from './slack-notifier.service';
            import { OrderNotificationService } from './order-notification.service';
            
            @Module({
              providers: [
                EmailNotifier,
                SlackNotifier,
                OrderNotificationService,
              ],
              exports: [OrderNotificationService],
            })
            export class NotificationModule {}
            
            ```
            
    - **LSP (리스코프 치환 원칙)** – **하위 타입은 언제나 상위 타입을 대체할 수 있어야** 합니다. 자식 클래스가 부모 클래스의 규약을 지켜서, 프로그램의 기능적 의미가 유지되어야 한다는 원칙입니다. 이를 어기면 다형성이 무너지고, 코드 예측이 어려워집니다.
    
    **예시 – Spring Repository 구현에서 LSP 보장**
        
        ```java
        // 상위 타입(계약)
        public interface DiscountPolicy {
            Money calculateDiscount(Money original);
        }
        
        // 고정 금액 할인
        @Component
        public class FixedDiscountPolicy implements DiscountPolicy {
            @Override
            public Money calculateDiscount(Money original) {
                return original.minus(Money.won(1000));
            }
        }
        
        // 퍼센트 할인
        @Component
        public class RateDiscountPolicy implements DiscountPolicy {
            @Override
            public Money calculateDiscount(Money original) {
                return original.times(0.9);
            }
        }
        
        // OrderService는 DiscountPolicy 타입에만 의존
        @Service
        public class OrderPriceCalculator {
        
            private final DiscountPolicy discountPolicy;
        
            public OrderPriceCalculator(DiscountPolicy discountPolicy) {
                this.discountPolicy = discountPolicy;
            }
        
            public Money finalPrice(Money original) {
                // FixedDiscountPolicy든 RateDiscountPolicy든
                // 계약을 만족하기 때문에 자유롭게 교체 가능 (LSP 성립)
                return discountPolicy.calculateDiscount(original);
            }
        }
        ```
        
        - NestJS
            
            ```tsx
            // src/discount/discount-policy.interface.ts
            export interface DiscountPolicy {
              calculateDiscount(originalPrice: number): number;
            }
            
            // src/discount/fixed-discount.policy.ts
            import { Injectable } from '@nestjs/common';
            import { DiscountPolicy } from './discount-policy.interface';
            
            @Injectable()
            export class FixedDiscountPolicy implements DiscountPolicy {
              calculateDiscount(originalPrice: number): number {
                return originalPrice - 1000;
              }
            }
            
            // src/discount/rate-discount.policy.ts
            import { Injectable } from '@nestjs/common';
            import { DiscountPolicy } from './discount-policy.interface';
            
            @Injectable()
            export class RateDiscountPolicy implements DiscountPolicy {
              calculateDiscount(originalPrice: number): number {
                return originalPrice * 0.9;
              }
            }
            
            // src/order/order-price-calculator.service.ts
            import { Injectable } from '@nestjs/common';
            import { DiscountPolicy } from '../discount/discount-policy.interface';
            
            @Injectable()
            export class OrderPriceCalculator {
              constructor(private readonly discountPolicy: DiscountPolicy) {}
            
              finalPrice(originalPrice: number): number {
                // 어떤 정책이 들어오든, 인터페이스 계약만 지키면 문제 없음 (LSP 충족)
                return this.discountPolicy.calculateDiscount(originalPrice);
              }
            }
            
            // src/app.module.ts
            import { Module } from '@nestjs/common';
            import { OrderPriceCalculator } from './order/order-price-calculator.service';
            import { FixedDiscountPolicy } from './discount/fixed-discount.policy';
            // import { RateDiscountPolicy } from './discount/rate-discount.policy';
            
            @Module({
              providers: [
                OrderPriceCalculator,
                {
                  provide: 'DiscountPolicy', // 인터페이스 토큰
                  useClass: FixedDiscountPolicy, // or RateDiscountPolicy
                },
              ],
            })
            export class AppModule {}
            
            // OrderPriceCalculator에 @Inject('DiscountPolicy')로 명시 주입:
            // constructor(
            //  @Inject('DiscountPolicy') private readonly discountPolicy: DiscountPolicy
            // ) {}
            
            ```
            
    - **ISP (인터페이스 분리 원칙)** – **인터페이스를 용도별로 작게 분리**하라는 뜻입니다. 한 클래스가 자신과 무관한 메서드들까지 가진 커다란 인터페이스를 구현하지 않도록 합니다. 예를 들어 DAO 인터페이스를 읽기용, 쓰기용으로 쪼개는 식으로 **클라이언트에 딱 필요한 인터페이스만 제공**하는 게 좋습니다.
    
    **예시 – 읽기/쓰기 레포지토리 분리**
        
        ```java
        // 읽기 전용
        public interface ProductReader {
            Optional<Product> findById(Long id);
            List<Product> findAllByCategory(Category category);
        }
        
        // 쓰기 전용
        public interface ProductWriter {
            Product save(Product product);
            void deleteById(Long id);
        }
        
        // 구현체는 둘 다 구현하더라도
        // 클라이언트(서비스)는 필요한 인터페이스만 의존
        @Repository
        public class JpaProductRepository
                implements ProductReader, ProductWriter {
        
            @PersistenceContext
            private EntityManager em;
        
            @Override
            public Optional<Product> findById(Long id) {
                return Optional.ofNullable(em.find(Product.class, id));
            }
        
            @Override
            public List<Product> findAllByCategory(Category category) {
                // JPQL…
            }
        
            @Override
            public Product save(Product product) {
                em.persist(product);
                return product;
            }
        
            @Override
            public void deleteById(Long id) {
                Product p = em.find(Product.class, id);
                if (p != null) em.remove(p);
            }
        }
        ```
        
        ```java
        @RequiredArgsConstructor
        public class ProductQueryService {
        
            private final ProductReader productReader;
        
            public Product findProduct(Long id) {
                return productReader.findById(id)
                        .orElseThrow(() -> new NotFoundException());
            }
        }
        
        @RequiredArgsConstructor
        public class ProductCommandService {
        
            private final ProductWriter productWriter;
        
            public void register(Product product) {
                productWriter.save(product);
            }
        
            public void remove(Long id) {
                productWriter.deleteById(id);
            }
        }
        
        ```
        
        - NestJS
            
            ```tsx
            //도메인 모델 정의 (단순화)
            // src/product/model/product.model.ts
            export class Product {
              constructor(
                public readonly id: number,
                public readonly name: string,
                public readonly category: string,
              ) {}
            }
            
            //인터페이스 분리: 읽기 / 쓰기
            // src/product/port/product-reader.ts
            import { Product } from '../model/product.model';
            
            export interface ProductReader {
              findById(id: number): Promise<Product | null>;
              findAllByCategory(category: string): Promise<Product[]>;
            }
            
            // src/product/port/product-writer.ts
            import { Product } from '../model/product.model';
            
            export interface ProductWriter {
              save(product: Product): Promise<void>;
              deleteById(id: number): Promise<void>;
            }
            
            //Repository 구현: 하나의 클래스가 두 역할 모두 수행
            // src/product/repository/in-memory-product.repository.ts
            import { Injectable } from '@nestjs/common';
            import { Product } from '../model/product.model';
            import { ProductReader } from '../port/product-reader';
            import { ProductWriter } from '../port/product-writer';
            
            @Injectable()
            export class InMemoryProductRepository
              implements ProductReader, ProductWriter
            {
              private readonly db: Product[] = [];
            
              async findById(id: number): Promise<Product | null> {
                return this.db.find((p) => p.id === id) ?? null;
              }
            
              async findAllByCategory(category: string): Promise<Product[]> {
                return this.db.filter((p) => p.category === category);
              }
            
              async save(product: Product): Promise<void> {
                this.db.push(product);
              }
            
              async deleteById(id: number): Promise<void> {
                const index = this.db.findIndex((p) => p.id === id);
                if (index !== -1) this.db.splice(index, 1);
              }
            }
            
            //서비스 분리: 읽기용 / 쓰기용 의존성 분리
            // src/product/service/product-query.service.ts
            import { Injectable } from '@nestjs/common';
            import { ProductReader } from '../port/product-reader';
            import { Product } from '../model/product.model';
            
            @Injectable()
            export class ProductQueryService {
              constructor(private readonly reader: ProductReader) {}
            
              async getProduct(id: number): Promise<Product> {
                const product = await this.reader.findById(id);
                if (!product) throw new Error('Not found');
                return product;
              }
            }
            
            // src/product/service/product-command.service.ts
            import { Injectable } from '@nestjs/common';
            import { ProductWriter } from '../port/product-writer';
            import { Product } from '../model/product.model';
            
            @Injectable()
            export class ProductCommandService {
              constructor(private readonly writer: ProductWriter) {}
            
              async register(product: Product) {
                await this.writer.save(product);
              }
            
              async remove(id: number) {
                await this.writer.deleteById(id);
              }
            }
            
            ```
            
    - **DIP (의존성 역전 원칙)** – **상위 레벨 모듈은 하위 레벨 모듈에 의존하면 안 된다**, **둘 다 추상화에 의존**해야 한다는 원칙입니다. 쉽게 말해 **구체(구현)보다는 인터페이스(추상)에 의존**하도록 코드를 짜라는 것입니다. 이 원칙을 지키면 높은 수준의 비즈니스 로직이 하위 세부사항(DB, UI 등)에 영향받지 않고 독립적일 수 있습니다. DIP는 뒤에 배울 **클린 아키텍처의 핵심 원칙**이기도 합니다.
    
    **예시 – 결제 게이트웨이를 인터페이스로 뒤집기**
        
        ```java
        // 상위 타입(계약)
        public interface DiscountPolicy {
            Money calculateDiscount(Money original);
        }
        
        // 고정 금액 할인
        @Component
        public class FixedDiscountPolicy implements DiscountPolicy {
            @Override
            public Money calculateDiscount(Money original) {
                return original.minus(Money.won(1000));
            }
        }
        
        // 퍼센트 할인
        @Component
        public class RateDiscountPolicy implements DiscountPolicy {
            @Override
            public Money calculateDiscount(Money original) {
                return original.times(0.9);
            }
        }
        
        // OrderService는 DiscountPolicy 타입에만 의존
        @Service
        public class OrderPriceCalculator {
        
            private final DiscountPolicy discountPolicy;
        
            public OrderPriceCalculator(DiscountPolicy discountPolicy) {
                this.discountPolicy = discountPolicy;
            }
        
            public Money finalPrice(Money original) {
                // FixedDiscountPolicy든 RateDiscountPolicy든
                // 계약을 만족하기 때문에 자유롭게 교체 가능 (LSP 성립)
                return discountPolicy.calculateDiscount(original);
            }
        }
        
        ```
        
        - NestJS
            
            ```tsx
            //할인 정책 인터페이스 정의
            // src/discount/discount-policy.interface.ts
            export interface DiscountPolicy {
              calculateDiscount(originalPrice: number): number;
            }
            
            //구체 구현 – 고정 금액 할인
            // src/discount/fixed-discount.policy.ts
            import { Injectable } from '@nestjs/common';
            import { DiscountPolicy } from './discount-policy.interface';
            
            @Injectable()
            export class FixedDiscountPolicy implements DiscountPolicy {
              calculateDiscount(originalPrice: number): number {
                return originalPrice - 1000;
              }
            }
            
            //구체 구현 – 비율 할인
            // src/discount/rate-discount.policy.ts
            import { Injectable } from '@nestjs/common';
            import { DiscountPolicy } from './discount-policy.interface';
            
            @Injectable()
            export class RateDiscountPolicy implements DiscountPolicy {
              calculateDiscount(originalPrice: number): number {
                return originalPrice * 0.9;
              }
            }
            
            //비즈니스 로직 – 할인 계산기 (상위 모듈)
            // src/order/order-price-calculator.service.ts
            import { Inject, Injectable } from '@nestjs/common';
            import { DiscountPolicy } from '../discount/discount-policy.interface';
            
            @Injectable()
            export class OrderPriceCalculator {
              constructor(
                @Inject('DiscountPolicy') // 인터페이스 토큰으로 주입
                private readonly discountPolicy: DiscountPolicy,
              ) {}
            
              finalPrice(originalPrice: number): number {
                return this.discountPolicy.calculateDiscount(originalPrice);
              }
            }
            
            //Module 설정 – 의존성 역전 구성
            // src/app.module.ts
            import { Module } from '@nestjs/common';
            import { OrderPriceCalculator } from './order/order-price-calculator.service';
            import { FixedDiscountPolicy } from './discount/fixed-discount.policy';
            // import { RateDiscountPolicy } from './discount/rate-discount.policy';
            
            @Module({
              providers: [
                OrderPriceCalculator,
                FixedDiscountPolicy,
                {
                  provide: 'DiscountPolicy',     // 추상 토큰
                  useClass: FixedDiscountPolicy, // 실제 구현체 선택
                },
              ],
            })
            export class AppModule {}
            
            ```
            
- 위 원칙들을 따르면 **코드 변경에 강한 구조**를 얻을 수 있음
- 자연히 **유지보수성이 높아지고**, 여러 개발자가 동시에 작업해도 충돌이 줄어듬
- 또한 **테스트 작성이 쉬워지고**(특히 DIP는 테스트에 용이한 구조를 만듭니다), 결과적으로 **아키텍처가 장기적으로 건강**해짐
- SOLID 자체는 원론적인 원칙들이지만, 앞으로 살펴볼 다양한 아키텍처 패턴들이 결국 SOLID 원칙을 구현하는 구체적 방식이라 보시면 됩니다.
    
    

### **TDD가 설계에 미치는 영향**

- TDD는 “테스트를 먼저 작성하고 그걸 통과시키는 구현을 하는” 개발 방식인데, 잘 해보면 **자연스레 좋은 설계 습관**을 익히게됨
- TDD를 진행하면, 테스트 코드가 잘 짜기 어려울 때는 본 코드의 구조를 개선해야 하는 신호가됨
- 실제로 TDD를 하다 보면 아래와 같은 깨달음이 오곤 합니다:
    - **TDD를 해보고 느낀 점:**
    - 한 클래스에 너무 많은 로직이 몰려 있으면 **테스트하기 어렵다** → 자연스럽게 코드를 **여러 역할로 분리**하게 된다.
    - 기능을 테스트하려면 **어떤 클래스들이 필요하고 역할이 뭐여야 할지** 미리 생각해야 한다 → 구현 전에 **클래스 설계**부터 고민하게 된다.
    - **의존성이 높으면** (예: DB와 꽉 결합) 테스트가 힘들다 → **인터페이스를 두고 의존성 주입** 등을 활용해서 **구조를 유연**하게 만든다.
- 예시 : **“Red → Green → Refactor”** 흐름을 한 번만 따라가도 **인터페이스 분리·DI·SRP** 같은 설계 원칙이 자연히 스며드는 과정을 확인할 수 있음
    - **Red – 먼저 실패하는 테스트를 쓴다**
        
        ```java
        // OrderPriceCalculatorTest.java
        class OrderPriceCalculatorTest {
        
            @Test
            void totalPrice_포함된할인_정상계산() {
                // 가짜 할인 정책(10%↓)을 주입해 테스트 격리
                DiscountPolicy fakePolicy = original -> original.times(0.9);
        
                OrderPriceCalculator calc = new OrderPriceCalculator(fakePolicy);
        
                Money price = calc.finalPrice(Money.won(10_000));
        
                assertEquals(Money.won(9_000), price);
            }
        }
        ```
        
        - NestJS
            
            ```tsx
            // test/order-price-calculator.spec.ts
            import { OrderPriceCalculator } from '../src/order/order-price-calculator.service';
            import { DiscountPolicy } from '../src/discount/discount-policy.interface';
            
            describe('OrderPriceCalculator', () => {
              it('should calculate 10% discounted price', () => {
                // 가짜 할인 정책: 10% 할인
                const fakePolicy: DiscountPolicy = {
                  calculateDiscount: (original: number) => original * 0.9,
                };
            
                const calculator = new OrderPriceCalculator(fakePolicy);
            
                const price = calculator.finalPrice(10000);
            
                expect(price).toBe(9000); // 실패 (처음엔 구현 안 되어 있음)
              });
            });
            
            ```
            
        
        **포인트**
        
        - **할인 정책(DiscountPolicy)** 을 인터페이스로 미리 작성
        - 실제 DB나 외부 서비스 없이 **람다(fake 객체)** 로 주입해 빠르고 안정적인 테스트를 작성
    - **Green – 테스트를 통과시키는 최소 구현**
        
        ```java
        // DiscountPolicy.java ― 추상화
        public interface DiscountPolicy {
            Money calculateDiscount(Money original);
        }
        
        // OrderPriceCalculator.java ― 단일 책임
        public class OrderPriceCalculator {
        
            private final DiscountPolicy discountPolicy;
        
            public OrderPriceCalculator(DiscountPolicy discountPolicy) {
                this.discountPolicy = discountPolicy;
            }
        
            public Money finalPrice(Money original) {
                return discountPolicy.calculateDiscount(original);
            }
        }
        ```
        
        - NestJS
            
            ```tsx
            // src/discount/discount-policy.interface.ts
            export interface DiscountPolicy {
              calculateDiscount(original: number): number;
            }
            
            // src/order/order-price-calculator.service.ts
            import { DiscountPolicy } from '../discount/discount-policy.interface';
            
            export class OrderPriceCalculator {
              constructor(private readonly discountPolicy: DiscountPolicy) {}
            
              finalPrice(original: number): number {
                return this.discountPolicy.calculateDiscount(original);
              }
            }
            
            ```
            
        
        **포인트**
        
        - 구현은 단순하지만 **DI(의존성 주입)·SRP(단일 책임)** 가 자동으로 적용
        - 덕분에 테스트에서는 원하는 어떤 정책이든 **가짜 객체**로 손쉽게 교체할 수 있음
    
    - **Refactor – 설계를 확장·다듬는다**
        
        ```java
        // 실제 정책 구현체들 ― OCP & LSP 충족
        @Component
        public class FixedDiscountPolicy implements DiscountPolicy {
            public Money calculateDiscount(Money original) {
                return original.minus(Money.won(1_000));
            }
        }
        
        @Component
        public class RateDiscountPolicy implements DiscountPolicy {
            public Money calculateDiscount(Money original) {
                return original.times(0.9);
            }
        }
        
        ```
        
        - NestJS
            
            ```tsx
            // src/discount/fixed-discount.policy.ts
            import { Injectable } from '@nestjs/common';
            import { DiscountPolicy } from './discount-policy.interface';
            
            @Injectable()
            export class FixedDiscountPolicy implements DiscountPolicy {
              calculateDiscount(original: number): number {
                return original - 1000;
              }
            }
            
            // src/discount/rate-discount.policy.ts
            import { Injectable } from '@nestjs/common';
            import { DiscountPolicy } from './discount-policy.interface';
            
            @Injectable()
            export class RateDiscountPolicy implements DiscountPolicy {
              calculateDiscount(original: number): number {
                return original * 0.9;
              }
            }
            
            // src/order/order-price-calculator.service.ts
            import { Inject, Injectable } from '@nestjs/common';
            import { DiscountPolicy } from '../discount/discount-policy.interface';
            
            @Injectable()
            export class OrderPriceCalculator {
              constructor(
                @Inject('DiscountPolicy') // 인터페이스 토큰
                private readonly discountPolicy: DiscountPolicy,
              ) {}
            
              finalPrice(original: number): number {
                return this.discountPolicy.calculateDiscount(original);
              }
            }
            
            //Nest Module 설정 예
            // src/app.module.ts
            import { Module } from '@nestjs/common';
            import { OrderPriceCalculator } from './order/order-price-calculator.service';
            import { RateDiscountPolicy } from './discount/rate-discount.policy';
            // or use FixedDiscountPolicy
            
            @Module({
              providers: [
                RateDiscountPolicy,
                OrderPriceCalculator,
                {
                  provide: 'DiscountPolicy',
                  useClass: RateDiscountPolicy,
                },
              ],
            })
            export class AppModule {}
            
            ```
            
        - **OCP(개방-폐쇄)** : 새 정책 클래스를 추가해도 OrderPriceCalculator는 그대로.
        - **LSP(리스코프 치환)** : 두 구현체가 계약을 지켜서 아무거나 끼워 넣어도 동작
        - **ISP** : 할인과 무관한 메서드를 강요하지 않는 **작은 인터페이스**.
- **TDD 한 사이클이 가져온 설계 변화 요약**
    
    
    | **단계** | **설계 신호** | **결과적으로 도입된 원칙** |
    | --- | --- | --- |
    | **Red** | “테스트 격리가 필요하다” | **DIP(의존성 역전)**·인터페이스 추출 |
    | **Green** | “가짜 객체를 넣자” | **DI**(의존성 주입)·**SRP(단일 책임)** |
    | **Refactor** | “확장할 수 있게 하자” | **OCP(개방폐쇄)**, **LSP(라스코프 치환)**, **ISP** |
- TDD는 테스트 작성 기술로만 끝나지 않고, “테스트하기 쉬운 구조 = 유지보수하기 쉬운 구조”를 만드는 가장 간단한 설계 훈련법임.
- 작은 예시를 반복하다보면 어느새 프로젝트 전체가 SOLID 원칙을 따는 방향으로 진화해 있을것

## **아키텍처 패턴 비교: Layered vs Hexagonal vs Clean**

<aside>
💡

이번 파트에서는 실무에서 많이 거론되는 세 가지 아키텍처 패턴, **레이어드 아키텍처 (계층형)**, **헥사고날 아키텍처 (Ports & Adapters)**, **클린 아키텍처**를 핵심 개념 위주로 비교해보겠습니다. 각각 **비즈니스 로직을 어떤 식으로 배치**하는지가 다르고, **의존성 제어 방식**에 차이가 있습니다.

먼저 크게 개념을 잡고, 후반부에 실무 응용으로 넘어갈 예정이니 너무 깊이 파고들기보다는 **“아 이런 차이가 있구나”** 감을 잡으면 됩니다.

</aside>

먼저 크게 개념을 잡고, 후반부에 실무 응용으로 넘어갈 예정이니 너무 깊이 파고들기보다는 **“아 이런 차이가 있구나”** 감을 잡으면 됩니다.

### 도메인?

<aside>
💡 Domain ? Entity ? 이게 뭐요..

</aside>

용어에서부터 벽이 느껴질 때가 있는데, 바로 위와 같이 여기저기서 쓰이는 용어들입니다. 심지어는 상황에 따라 달라지는 이 용어들로 소통하다보면, 서로 다른 이야기를 하는 경우도 종종 있는 것 같아요. 용어의 다른 뜻 때문에 헷갈리는 분들이 많았을 거라고 생각해요. 그래서 좀 보편적으로 이해해보면 좋을 것 같습니다.

- **도메인** ( Domain )
    - **도메인(Domain)** 은 “**어떤 문제를 해결하고 싶은 대상 영역**“
        - 예를 들어, **“주문 시스템”**을 만들고 있다고 해볼게요.
            - 이 시스템이 다루는 핵심 개념은? → *상품*, *고객*, *주문*, *결제* 등등
            - 이 각각의 개념(상품, 주문 등)이 바로 **도메인**이에요.
        - 즉, **도메인은 “우리가 만들고자 하는 서비스에서 중요한 개념들”**입니다.
    - 도메인 객체는 **도메인을 코드로 표현한 것**.
        - 현실 세계의 개념을 **코드로 옮긴 것**
            
            ```java
            public class Order {
                private String productName;
                private int quantity;
                private int unitPrice;
            
                public int totalPrice() {
                    return quantity * unitPrice;
                }
            }
            ```
            
        
- **엔티티** ( Entity )
    - **도메인에서의 엔티티**
        - 도메인 객체 중에서 **고유 식별자(ID)가 있는 것**
    - **데이터베이스에서의 엔티티**
        - **DB 테이블에 매핑되는 클래스**예요. (JPA에서 @Entity 붙이는 거)
        - “DB에서 데이터를 읽고 쓰기 위한 객체”
- 정리
    - 도메인 객체는 *비즈니스 로직에 집중*
    - 엔티티는 *데이터 저장·조회에 집중*
    - 간단한 프로젝트에서는 그냥 JPA @Entity를 도메인 객체로도 씀
    → Order 클래스가 동시에 **도메인 객체 + DB 엔티티** 역할
    - 복잡한 도메인에서는 **도메인 객체와 엔티티를 분리
    → 도메인 객체는 오직 로직에만 집중하게 하고, DB는 몰라도 되게 하려고**

### **2-1. 레이어드 아키텍처 – 전통적인 계층형 패턴**

![Untitled.png](Step%2003%20Clean%20Architecture/Untitled.png)

- **레이어드 아키텍처**는 가장 익숙한 **계층형 구조**
- 보통 표현 계층(UI) → 비즈니스 계층 → 데이터 계층 순으로 **한 방향으로 계층 간 호출**이 이루어짐
- 예를 들어 **Controller → Service → Repository** 형태로, 상위 계층이 하위 계층을 호출하고 하위 계층은 결과를 반환하는 구조

🔔 Rules

- **단방향 의존:** 상위 계층은 자신보다 아래 계층의 기능을 호출합니다. 역방향 호출은 하지 않음
(예: Controller는 Service를 부르지만, Service가 Controller를 호출하지는 않음)
- **계층 책임:** 상위 계층은 필요한 작업을 **하위 계층의 구현에 위임**
(예: Service 로직에서 DB 저장은 Repository에 위임)
- **전이적 영향:** 하위 계층이 변경되면 상위 계층이 영향을 받을 수 있습니다. (예: DB 스키마 변경 -> Service 로직 수정 필요할 수 있음)
- **비즈니스 로직 노출:** 비즈니스 로직이 완전히 보호되지 못함 (아래 계층까지 흩어질 수 있음)
- **원칙 만족도:** DIP 🆗 (부분적으로 가능), OCP ❌ (새 계층 추가는 어렵고, 하위 변경에 상위가 닫혀있지 않음)
- 예시코드
    - 전형적인 **Controller → Service → Repository** 흐름
    - 레이어드 아키텍처가 어떻게 동작하는지 전체 그림이 보이도록 
    컨트롤러 ←→ DTO ←→ 서비스 ←→ 엔티티 ←→ 레포지토리까지 모두 포함
    - **도메인(Entity) · Value Object**
        
        ```java
        @Entity
        @Table(name = "orders")
        public class Order {
        
            @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
            private Long id;
        
            private String productName;
            private int quantity;
            private int unitPrice;
            private LocalDateTime orderedAt;
        
            /** JPA용 protected 기본 생성자 */
            protected Order() {}   
        
            private Order(String productName, int quantity, int unitPrice) {
                this.productName = productName;
                this.quantity = quantity;
                this.unitPrice = unitPrice;
                this.orderedAt = LocalDateTime.now();
            }
        
            public static Order create(String productName, int qty, int unitPrice) {
                return new Order(productName, qty, unitPrice);
            }
        
            // 비즈니스 규칙 메서드 (총액 계산 등)
            public int totalPrice() {
                return quantity * unitPrice;
            }
        
            // Getter만 노출 (Setter X — 불변성 유지)
            public Long getId()             { return id; }
            public String getProductName()  { return productName; }
            public int getQuantity()        { return quantity; }
            public int getUnitPrice()       { return unitPrice; }
            public LocalDateTime getOrderedAt() { return orderedAt; }
          }
        ```
        
        - 도메인 로직(총액 계산)과 상태(필드)를 한곳에 모아 응집도를 높인 도메인 모델
        - 도메인 엔티티는 비즈니스 상태와 규칙을 표현하는 도메인 계층의 핵심 구성요소
        - 단순히 DB 테이블에 매핑되는 구조가 아니라, **비즈니스 의미를 담고 있는 중심 모델**
            
            
    - **Repository 계층**
        
        ```java
        public interface OrderRepository extends JpaRepository<Order, Long> {
            // 기본 CRUD 외에 필요 시 도메인 특화 쿼리 추가
        }
        ```
        
        - JpaRepository를 확장하여 DB 액세스를 캡슐화
        - **상위 레이어**는 인터페이스만 의존 → **DIP** 만족
    - **비즈니스 계층 – Service**
        
        ```java
        @Service
        public class OrderService {
        
            private final OrderRepository orderRepository;
        
            public OrderService(OrderRepository orderRepository) {
                this.orderRepository = orderRepository;
            }
        
            /**
             * 한 레이어(서비스)는 자신의 **비즈니스 규칙**에만 집중하고,
             * 실제 저장은 하위 레이어(Repository)에 **위임**한다.
             */
            @Transactional
            public Order placeOrder(String productName, int qty, int unitPrice) {
                Order order = Order.create(productName, qty, unitPrice);
        
                // DB 접근은 Repository가 담당 (SRP)
                return orderRepository.save(order);
            }
        }
        ```
        
        - **서비스 레이어 핵심 원칙**
            - **도메인 규칙** 오케스트레이션 담당: *“무엇을 해야 하는가”*
            - **영속성 세부사항**은 **Repository**로 위임: *“어떻게 저장하는가”* 몰라도 됨
            - **단방향**: Service → Repository (거꾸로 호출 없음)
    - **표현(UI) 계층 – Controller**
        
        ```java
        @RestController
        @RequestMapping("/api/v1/orders")
        public class OrderController {
        
            private final OrderService orderService;
        
            public OrderController(OrderService orderService) {
                this.orderService = orderService;
            }
        
            /** ---- DTO (Request) -------------------------------------------------- */
            public record CreateOrderRequest(
                    @NotBlank String productName,
                    @Min(1) int quantity,
                    @Min(100) int unitPrice) {}
        
            /** ---- DTO (Response) ------------------------------------------------- */
            public record OrderResponse(
                    Long id, String productName,
                    int quantity, int unitPrice, int totalPrice) {
                public static OrderResponse from(Order o) {
                    return new OrderResponse(
                            o.getId(), o.getProductName(),
                            o.getQuantity(), o.getUnitPrice(),
                            o.totalPrice());
                }
            }
        
            /** ---- API 엔드포인트 -------------------------------------------------- */
            @PostMapping
            public ResponseEntity<OrderResponse> create(
                    @RequestBody @Valid CreateOrderRequest req) {
        
                Order saved = orderService.placeOrder(
                        req.productName(), req.quantity(), req.unitPrice());
        
                return ResponseEntity.ok(OrderResponse.from(saved));
            }
        }
        ```
        
        - 컨트롤러는 **HTTP → 도메인 호출** 변환 역할만 담당
        - **Validate → DTO 변환** 후 Service에 **위임** → **단방향 의존 유지**
        - Service가 Controller를 호출하지 않으므로 **UI 교체**(REST, GraphQL, gRPC 등) 시에도 **비즈니스 로직 건드릴 필요 없음**
    - **SOLID 원칙 충족도**
        
        
        | **레이어** | **책임** | **상위 ↔ 하위 의존성** | **SOLID 관점** |
        | --- | --- | --- | --- |
        | **UI (Controller)** | HTTP 매핑, DTO 변환, 검증 | UI → Service | SRP, DIP(서비스 인터페이스만 의존) |
        | **비즈니스 (Service)** | 도메인 규칙 조합·오케스트레이션 | Service → Repository | SRP·DIP (레포 인터페이스 의존) |
        | **데이터 (Repository)** | 영속성 세부 구현(JPA) | 없음 (최하위) | SRP (DB 처리 전담) |
        | **도메인 (Entity)** | 비즈니스 상태·규칙 응집 | 없음 (순수 객체) | LSP·OCP (도메인 확장 쉬움) |
    - **DIP**: **상위 모듈(서비스 계층)**이 **하위 모듈의 구체 구현이 아닌 인터페이스에만 의존**
    - **OCP**: 새 저장소(MyBatis, Mongo) 추가 = OrderRepository 구현체만 늘리면 OK
    - **SRP**: 각 레이어(및 클래스)가 *하나의 책임*만 갖도록 분리
    - 레이어드 아키텍처 특성상 **OCP 완전 달성은 어려움**(예: DB 스키마 변경 시 Service 영향), 그러나 대부분의 CRUD 서비스에서는 빠른 생산성 측면에서 충분히 실용적입니다.
    - NestJS
        - 전체 구조 요약
            
            ```tsx
            src/
            ├── order/
            │   ├── controller/order.controller.ts
            │   ├── dto/create-order.dto.ts
            │   ├── dto/order-response.dto.ts
            │   ├── entity/order.entity.ts
            │   ├── repository/order.repository.ts
            │   └── service/order.service.ts
            
            ```
            
        - Entity
            
            ```tsx
            // order/entity/order.entity.ts
            export class Order {
              constructor(
                private readonly productName: string,
                private readonly quantity: number,
                private readonly unitPrice: number,
                private readonly orderedAt: Date = new Date(),
                private id?: number,
              ) {}
            
              static create(productName: string, quantity: number, unitPrice: number): Order {
                return new Order(productName, quantity, unitPrice);
              }
            
              totalPrice(): number {
                return this.quantity * this.unitPrice;
              }
            
              getId(): number | undefined {
                return this.id;
              }
            
              getProductName(): string {
                return this.productName;
              }
            
              getQuantity(): number {
                return this.quantity;
              }
            
              getUnitPrice(): number {
                return this.unitPrice;
              }
            
              getOrderedAt(): Date {
                return this.orderedAt;
              }
            }
            
            ```
            
        - DTO
            
            ```tsx
            // order/dto/create-order.dto.ts
            import { IsString, Min } from 'class-validator';
            
            export class CreateOrderDto {
              @IsString()
              productName: string;
            
              @Min(1)
              quantity: number;
            
              @Min(100)
              unitPrice: number;
            }
            
            // order/dto/order-response.dto.ts
            import { Order } from '../entity/order.entity';
            
            export class OrderResponseDto {
              constructor(
                public readonly id: number | undefined,
                public readonly productName: string,
                public readonly quantity: number,
                public readonly unitPrice: number,
                public readonly totalPrice: number,
              ) {}
            
              static from(order: Order): OrderResponseDto {
                return new OrderResponseDto(
                  order.getId(),
                  order.getProductName(),
                  order.getQuantity(),
                  order.getUnitPrice(),
                  order.totalPrice(),
                );
              }
            }
            
            ```
            
        - Repository (In-memory 예시)
            
            ```tsx
            // order/repository/order.repository.ts
            import { Injectable } from '@nestjs/common';
            import { Order } from '../entity/order.entity';
            
            @Injectable()
            export class OrderRepository {
              private db: Order[] = [];
              private nextId = 1;
            
              save(order: Order): Order {
                // ID 할당 (간단한 시뮬레이션)
                const saved = new Order(
                  order.getProductName(),
                  order.getQuantity(),
                  order.getUnitPrice(),
                  order.getOrderedAt(),
                  this.nextId++,
                );
                this.db.push(saved);
                return saved;
              }
            }
            
            ```
            
        - Service
            
            ```tsx
            // order/service/order.service.ts
            import { Injectable } from '@nestjs/common';
            import { OrderRepository } from '../repository/order.repository';
            import { Order } from '../entity/order.entity';
            
            @Injectable()
            export class OrderService {
              constructor(private readonly orderRepository: OrderRepository) {}
            
              placeOrder(productName: string, quantity: number, unitPrice: number): Order {
                const order = Order.create(productName, quantity, unitPrice);
                return this.orderRepository.save(order);
              }
            }
            
            ```
            
        - Controller
            
            ```tsx
            // order/controller/order.controller.ts
            import {
              Body,
              Controller,
              Post,
            } from '@nestjs/common';
            import { OrderService } from '../service/order.service';
            import { CreateOrderDto } from '../dto/create-order.dto';
            import { OrderResponseDto } from '../dto/order-response.dto';
            
            @Controller('/orders')
            export class OrderController {
              constructor(private readonly orderService: OrderService) {}
            
              @Post()
              create(@Body() dto: CreateOrderDto): OrderResponseDto {
                const order = this.orderService.placeOrder(dto.productName, dto.quantity, dto.unitPrice);
                return OrderResponseDto.from(order);
              }
            }
            
            ```
            
- 정리
    - 레이어드 패턴은 구현이 쉽고 **관심사 분리가 명확**해 초보팀도 적용하기 좋음
    - 하지만 **의존성 방향이 한쪽으로만 고정**되어 있고, 비즈니스 로직이 종종 DB 등 하위 기술에 종속되는 단점이 있음
    - 예를 들어 Service에서 JPA 엔티티를 직접 다루는 식으로 결합되기 쉬움
    - **확장성** 측면에서는 새로운 타입의 UI나 외부 인터페이스를 추가할 때 기존 계층에 변경이 불가피하여 OCP 원칙을 지키기 어려움
    - **도메인 로직이 매우 복잡하거나, 다채로운 입출력 포트를 가져야 하는 서비스**라면 이후 단계에서 **클린/헥사고날 아키텍처**가 필요해집니다. 하지만 **대부분의 CRUD 중심 백엔드**를 빠르게 구축할 때는 여전히 레이어드 패턴이 **생산성과 가독성** 면에서 훌륭한 출발점이 됩니다.

### **2-2. 헥사고날 아키텍처 – 포트와 어댑터로 분리**

![Untitled.png](Step%2003%20Clean%20Architecture/Untitled%201.png)

- **비즈니스 로직과 외부 요소(입출력)를 철저히 분리**하는 것을 목표
- 어플리케이션의 중심에 핵심 비즈니스 로직(도메인)을 두고, 이를 둘러싸듯이 UI, DB, 메시지큐 같은 외부 시스템과의 인터페이스가 **포트(Ports)**와 **어댑터(Adapters)**를 통해 연결
- 다각형(육각형)으로 표현하는 이유는 여러 방향에서 다양한 입출력이 붙을 수 있다는 의미

🔔 Rules

- **비즈니스 로직이 핵심:** 시스템의 중앙에 **비즈니스 도메인 로직**이 위치하고, 가장 중요하게 다뤄짐
- **외부는 내부에 의존:** 데이터베이스나 UI 같은 외부 영역이 **비즈니스 로직에 의존** (내부 도메인을 호출하기 위해 **포트 인터페이스**를 구현하는 **어댑터**를 제공)
- **다양한 입출력 지원:** 하나의 비즈니스 로직을 두고, 여러 종류의 외부 인터페이스를 쉽게 붙이거나 뗄 수 있음 (예: 동일한 도메인 로직을 REST API, 메시지 큐, CLI 등으로 호출 가능)
- **원칙 만족도:** DIP 🆗, OCP 🆗 (도메인은 구현 세부와 분리되어 있고, 새 어댑터 추가로 기능 확장 가능)
- 예시코드
    
    <aside>
    💡
    
    육각형 중심에 순수 **도메인(비즈니스 로직)** 을 두고, 모든 입·출력은 **Port** 인터페이스를 거쳐 들어오거나 나갑니다. 실제 기술 세부 사항은 **Adapter**가 담당하므로, 도메인은 어떤 프레임워크·DB·프로토콜에도 의존하지 않습니다.
    
    </aside>
    
    - **프로젝트 구조**
        
        ```basic
        📂 src
        └─ main/java
           ├─ domain          : 핵심 도메인 + Port Interface
           └─ adapter         : 실제 IO 구현체(웹, DB 등)
        ```
        
    
    - Domain Model
        
        ```java
        // domain/model/Order.java
        package com.example.domain.model;
        
        import java.time.LocalDateTime;
        
        public class Order {
        
            private Long id;                       // 식별자 (JPA Entity 아님! 순수 객체)
            private final String productName;
            private final int quantity;
            private final int unitPrice;
            private final LocalDateTime orderedAt;
        
            private Order(String productName, int quantity, int unitPrice) {
                this.productName = productName;
                this.quantity    = quantity;
                this.unitPrice   = unitPrice;
                this.orderedAt   = LocalDateTime.now();
            }
        
            public static Order create(String product, int qty, int price) {
                return new Order(product, qty, price);
            }
        
            /** 비즈니스 규칙 */
            public int totalPrice() {
                return quantity * unitPrice;
            }
        
            /* 게터(Setter X) */
            public Long getId()             { return id; }
            public String getProductName()  { return productName; }
            public int getQuantity()        { return quantity; }
            public int getUnitPrice()       { return unitPrice; }
            public LocalDateTime getOrderedAt() { return orderedAt; }
        
            /* 식별자 세터는 패키지-프라이빗 → 영속성 어댑터만 호출 가능 */
            void assignId(Long id) { this.id = id; }
        }
        ```
        
        - JPA 어노테이션·Spring 의존성 **0**. “순수 자바 객체”로 비즈니스 규칙만 담음.
        - assignId() 는 같은 패키지에 있는 퍼시스턴스 어댑터만 호출 가능하도록 제한.
    - **Inbound Port — 주문 생성 유스케이스**
        
        ```java
        // domain/port/in/PlaceOrderUseCase.java
        package com.example.domain.port.in;
        
        import com.example.domain.model.Order;
        
        public interface PlaceOrderUseCase {
            Order placeOrder(String productName, int qty, int unitPrice);
        }
        ```
        
        - “도메인이 **외부**로부터 호출받고 싶은 작업”을 선언하는 **계약**.
        - 구현체는 도메인 내부(OrderService), 호출자는 다양한 Adapter(REST, Batch 등).
    - **Outbound Port — 주문 저장**
        
        ```java
        // domain/port/out/SaveOrderPort.java
        package com.example.domain.port.out;
        
        import com.example.domain.model.Order;
        
        public interface SaveOrderPort {
            Order save(Order order);
        }
        ```
        
        - 도메인이 **외부 시스템**을 호출해야 할 때 의존하는 추상 계약.
        - 실제 구현은 RDB(JPA), NoSQL, 메시지 큐 등 어댑터 층이 담당.
    - **Domain Service – OrderService**
        
        ```java
        // domain/service/OrderService.java
        package com.example.domain.service;
        
        import com.example.domain.model.Order;
        import com.example.domain.port.in.PlaceOrderUseCase;
        import com.example.domain.port.out.SaveOrderPort;
        import org.springframework.transaction.annotation.Transactional;
        
        public class OrderService implements PlaceOrderUseCase {
        
            private final SaveOrderPort saveOrderPort;
        
            public OrderService(SaveOrderPort saveOrderPort) {
                this.saveOrderPort = saveOrderPort;
            }
        
            @Override
            @Transactional
            public Order placeOrder(String productName, int qty, int unitPrice) {
                Order order = Order.create(productName, qty, unitPrice);
                return saveOrderPort.save(order);          // 외부 의존 → Port 인터페이스
            }
        }
        ```
        
        - 비즈니스 로직 오케스트레이션만 수행.
        - SaveOrderPort mock 만 주입하면 **순수 단위 테스트**가 가능.
    - **Outbound Adapter – JPA Persistence**
        
        ```java
        // adapter/out/persistence/OrderJpaEntity.java
        package com.example.adapter.out.persistence;
        
        import jakarta.persistence.*;
        import java.time.LocalDateTime;
        
        @Entity
        @Table(name = "orders")
        class OrderJpaEntity {
            @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
            Long id;
        
            String productName;
            int quantity;
            int unitPrice;
            LocalDateTime orderedAt;
        }
        
        /* JPA 리포지토리 */
        interface SpringDataOrderRepository
                extends org.springframework.data.jpa.repository.JpaRepository<OrderJpaEntity, Long> {}
        
        /* Adapter → Port 구현 */
        package com.example.adapter.out.persistence;
        
        import com.example.domain.model.Order;
        import com.example.domain.port.out.SaveOrderPort;
        import org.springframework.stereotype.Component;
        
        @Component
        class OrderPersistenceAdapter implements SaveOrderPort {
        
            private final SpringDataOrderRepository repo;
        
            OrderPersistenceAdapter(SpringDataOrderRepository repo) {
                this.repo = repo;
            }
        
            @Override
            public Order save(Order order) {
                OrderJpaEntity entity = toEntity(order);
                OrderJpaEntity saved  = repo.save(entity);
        
                order.assignId(saved.id);   // 식별자 역주입
                return order;
            }
        
            private OrderJpaEntity toEntity(Order o) {
                OrderJpaEntity e = new OrderJpaEntity();
                e.productName = o.getProductName();
                e.quantity    = o.getQuantity();
                e.unitPrice   = o.getUnitPrice();
                e.orderedAt   = o.getOrderedAt();
                return e;
            }
        }
        ```
        
        - SaveOrderPort를 **구현**해 실제 DB(JPA) 작업 수행.
        - 매핑 로직을 어댑터 내부에 숨겨 도메인에 JPA 엔티티가 **누수되지 않음**.
    - **Inbound Adapter – REST Controller**
        
        ```java
        // adapter/in/web/OrderController.java
        package com.example.adapter.in.web;
        import com.example.domain.model.Order;
        import com.example.domain.port.in.PlaceOrderUseCase;
        import jakarta.validation.constraints.Min;
        import jakarta.validation.constraints.NotBlank;
        import org.springframework.http.ResponseEntity;
        import org.springframework.validation.annotation.Validated;
        import org.springframework.web.bind.annotation.*;
        
        @RestController
        @RequestMapping("/api/v1/orders")
        public class OrderController {
        
            private final PlaceOrderUseCase placeOrderUseCase;
        
            public OrderController(PlaceOrderUseCase placeOrderUseCase) {
                this.placeOrderUseCase = placeOrderUseCase;
            }
        
            /* DTO */
            public record CreateOrderRequest(
                    @NotBlank String productName,
                    @Min(1) int quantity,
                    @Min(100) int unitPrice) {}
        
            public record OrderResponse(
                    Long id, String productName,
                    int quantity, int unitPrice, int totalPrice) {
                static OrderResponse from(Order o) {
                    return new OrderResponse(
                            o.getId(), o.getProductName(),
                            o.getQuantity(), o.getUnitPrice(),
                            o.totalPrice());
                }
            }
        
            /* REST Endpoint */
            @PostMapping
            public ResponseEntity<OrderResponse> create(
                    @RequestBody @Validated CreateOrderRequest req) {
        
                Order saved = placeOrderUseCase.placeOrder(
                        req.productName(), req.quantity(), req.unitPrice());
        
                return ResponseEntity.ok(OrderResponse.from(saved));
            }
        }
        ```
        
        - HTTP 요청을 DTO → 도메인 입력으로 변환하고 결과를 응답 DTO로 재구성.
        - 다른 프로토콜(gRPC, GraphQL 등) 지원 시 **컨트롤러만 추가**하면 동일 도메인 재사용.
    - NestJS
        - 전체 구조 요약
            
            ```tsx
            src/
            ├── domain/
            │   ├── model/
            │   │   └── order.ts
            │   ├── port/
            │   │   ├── in/place-order.usecase.ts
            │   │   └── out/save-order.port.ts
            │   └── service/
            │       └── order.service.ts
            ├── adapter/
            │   ├── in/web/order.controller.ts
            │   └── out/persistence/order.persistence.ts
            └── app.module.ts
            
            ```
            
        - 순수 도메인 객체
            
            ```tsx
            export class Order {
              private id?: number;
            
              constructor(
                private readonly productName: string,
                private readonly quantity: number,
                private readonly unitPrice: number,
                private readonly orderedAt: Date = new Date(),
              ) {}
            
              static create(productName: string, quantity: number, unitPrice: number): Order {
                return new Order(productName, quantity, unitPrice);
              }
            
              assignId(id: number) {
                this.id = id;
              }
            
              totalPrice(): number {
                return this.quantity * this.unitPrice;
              }
            
              getId() {
                return this.id;
              }
            
              getProductName() {
                return this.productName;
              }
            
              getQuantity() {
                return this.quantity;
              }
            
              getUnitPrice() {
                return this.unitPrice;
              }
            
              getOrderedAt() {
                return this.orderedAt;
              }
            }
            
            ```
            
        - nbound Port (유즈케이스 정의)
            
            ```tsx
            import { Order } from '../../model/order';
            
            export interface PlaceOrderUseCase {
              placeOrder(productName: string, quantity: number, unitPrice: number): Order;
            }
            
            ```
            
        - Outbound Port (저장 인터페이스)
            
            ```tsx
            import { Order } from '../../model/order';
            
            export interface SaveOrderPort {
              save(order: Order): Order;
            }
            
            ```
            
        - 도메인 서비스 (UseCase 구현)
            
            ```tsx
            import { Injectable } from '@nestjs/common';
            import { PlaceOrderUseCase } from '../port/in/place-order.usecase';
            import { SaveOrderPort } from '../port/out/save-order.port';
            import { Order } from '../model/order';
            
            @Injectable()
            export class OrderService implements PlaceOrderUseCase {
              constructor(private readonly saveOrderPort: SaveOrderPort) {}
            
              placeOrder(productName: string, quantity: number, unitPrice: number): Order {
                const order = Order.create(productName, quantity, unitPrice);
                return this.saveOrderPort.save(order);
              }
            }
            
            ```
            
        - Persistence Adapter (in-memory 구현)
            
            ```tsx
            import { Injectable } from '@nestjs/common';
            import { SaveOrderPort } from '../../domain/port/out/save-order.port';
            import { Order } from '../../domain/model/order';
            
            @Injectable()
            export class OrderPersistenceAdapter implements SaveOrderPort {
              private db: Order[] = [];
              private nextId = 1;
            
              save(order: Order): Order {
                order.assignId(this.nextId++);
                this.db.push(order);
                return order;
              }
            }
            
            ```
            
        - REST 어댑터 (Inbound Adapter)
            
            ```tsx
            import {
              Body,
              Controller,
              Post,
            } from '@nestjs/common';
            import { PlaceOrderUseCase } from '../../domain/port/in/place-order.usecase';
            import { Order } from '../../domain/model/order';
            
            class CreateOrderRequest {
              productName: string;
              quantity: number;
              unitPrice: number;
            }
            
            class OrderResponse {
              constructor(
                public readonly id: number,
                public readonly productName: string,
                public readonly quantity: number,
                public readonly unitPrice: number,
                public readonly totalPrice: number,
              ) {}
            
              static from(order: Order): OrderResponse {
                return new OrderResponse(
                  order.getId()!,
                  order.getProductName(),
                  order.getQuantity(),
                  order.getUnitPrice(),
                  order.totalPrice(),
                );
              }
            }
            
            @Controller('orders')
            export class OrderController {
              constructor(private readonly placeOrderUseCase: PlaceOrderUseCase) {}
            
              @Post()
              create(@Body() req: CreateOrderRequest): OrderResponse {
                const saved = this.placeOrderUseCase.placeOrder(req.productName, req.quantity, req.unitPrice);
                return OrderResponse.from(saved);
              }
            }
            
            ```
            
        - 의존성 주입 설정
            
            ```tsx
            import { Module } from '@nestjs/common';
            import { OrderService } from './domain/service/order.service';
            import { OrderPersistenceAdapter } from './adapter/out/persistence/order.persistence';
            import { OrderController } from './adapter/in/web/order.controller';
            
            @Module({
              controllers: [OrderController],
              providers: [
                OrderService,
                OrderPersistenceAdapter,
                {
                  provide: 'SaveOrderPort',
                  useExisting: OrderPersistenceAdapter,
                },
                {
                  provide: 'PlaceOrderUseCase',
                  useExisting: OrderService,
                },
              ],
            })
            export class AppModule {}
            
            ```
            
    - **SOLID 원칙 충족도 비교**
        
        
        | **구조** | **SRP (단일 책임)** | **OCP (확장 가능)** | **LSP** | **ISP** | **DIP** |
        | --- | --- | --- | --- | --- | --- |
        | **Layered** | 레이어별 책임 OK | UI 추가·DB 교체 시 영향 有 | 대부분 만족 | Controller·Service 연계 | Repository 인터페이스만 추상 |
        | **Hexagonal** | 도메인·Adapter 명확 분리 | 새 Adapter 추가 **완벽** (Port 재사용) | 동일 | Port가 작아 인터페이스 경량화 | **모든 의존성이 추상** |
- 정리
    - **비즈니스 핵심을 완벽히 격리**해, 프레임워크·DB·프로토콜이 바뀌어도 도메인 로직이 **1줄도 수정되지 않음**
    - **의존성 방향이 내부 → 외부**로 고정돼 **DIP / OCP**를 거의 완전 달성
    - **다양한 입·출력 채널(REST, gRPC, CLI, Batch, MQ …)** 을 ­**Adapter만 추가**해 손쉽게 확장
    → 한 도메인을 여러 채널에서 재사용해야 할 때 비용 대비 효과가 압도적
    - **테스트 용이성**이 압권
        - Port를 **Mock/Fake**로 주입하면 도메인 단위 테스트가 ‘순수 자바’ 수준으로 간단
        - 외부 시스템 없이도 규칙 검증이 가능해 TDD·CI 파이프라인이 가벼워짐
    - **유지보수성**
        - “변화가 잦은 부분(IO·프레임워크 코드)”을 주변으로 밀어내어 **변화 범위를 국소화**
        - 새 DB·메시지 브로커·UI 프레임워크 도입 시 도메인 수정이 필요 없어 리스크↓
    - **학습 곡선**
        - Port·Adapter 구분, 폴더 구조, DI 설정 등 **초기 설정이 Layered보다 복잡**
        - “추상화가 한 겹 더” 추가된 만큼 **가시성이 낮아질 수 있음**
    - **적합한 상황**
        - **채널 다변화** : REST뿐 아니라 메시지 큐·스케줄러·CLI 등 복수 채널 요구
        - **도메인 규칙이 빈번하게 진화**하거나 **여러 팀이 같은 코어를 공유**할 때
        - **교차 DB(관계형 + 캐시 + NoSQL)** 를 조합하거나, 장기적으로 기술 교체 가능성이 큰 프로젝트
    - **과(過) 엔지니어링 위험**
        - 단일 웹-MVC + 단순 CRUD 서비스라면 오히려 **Layered가 더 단순하고 생산성 높음**
        - “적용 근거(채널 다양성·복잡 규칙)가 명확하지 않으면” 불필요한 추상화가 될 수 있음
    - **결론**
        - **“변화 지점”을 기술 어댑터로 고립**시키고 **도메인을 영구 불변**으로 유지하려면 최적의 선택
        - 하지만 **요구사항이 단순·채널이 적다**면, 먼저 Layered로 빠르게 출시 → 복잡도 상승 시 **핵심 모듈부터 헥사고날로 점진적 리팩토링** 하는 접근이 현실적

### **2-3. 클린 아키텍처 – 의존성 규칙을 통한 계층 분리**

![Untitled.png](Step%2003%20Clean%20Architecture/Untitled%202.png)

- **클린 아키텍처**는 2010년대에 로버트 마틴이 제창한 개념으로, 앞선 헥사고날과 철학을 같이함
- 깨끗한(**Clean**)이라는 이름처럼 **의존성 규칙** 하나로 모든 복잡도를 제어
- **“안쪽은 밖을 모르고, 밖은 안쪽에 의존한다”** – 이것이 바로 클린 아키텍처의 핵심 **의존성 규칙(Dependency Rule)**
- 계층을 원으로 그려서
    - **안쪽 원 = Entities(엔티티, 핵심 업무 규칙)**,
    - 그 바깥 원 = Use Cases(유스케이스, 응용 서비스),
    - 더 바깥 = Interface Adapters(인터페이스 어댑터, UI, DB 인터페이스 등),
    - 가장 바깥 = Frameworks & Drivers(외부 프레임워크) 로 표현
    ****
- 🔔 Rules
    - **비즈니스 로직이 최중앙:** 애플리케이션의 가장 중심에 **엔티티(Entity)** 또는 핵심 도메인 모델이 위치합니다. 그리고 그보다 살짝 바깥에 **유스케이스(Use Case)**나 **도메인 서비스** 계층이 위치하여 구체적인 시나리오별 비즈니스 로직을 담당
    - **외부 요소가 내부에 의존:** DB, UI 같은 외부 계층이 **내부 비즈니스 계층의 인터페이스에 의존**합니다. (헥사고날의 Port와 개념상 동일하나, 여기서는 UseCase/InputBoundary, Presenter/OutputBoundary 같은 용어를 쓰기도 합니다.)
    - **관심사의 고수준 분리:** UI나 DB와 같은 **저수준 구현(details)**은 가장 바깥으로 밀어내고, **고수준 정책(policy)**인 도메인 규칙만 안쪽에 모읍니다.
    - **원칙 만족도:** DIP 🆗, OCP 🆗 (내부 모듈은 추상 인터페이스만 노출하고, 외부 구현은 추가/교체해도 내부코드 변화 없음)
- 예시코드
    
    <aside>
    💡
    
    동심원 중심에 Entity(핵심 규칙), 그다음 Use-Case(시나리오), 바깥은 Interface Adapter(UI·DB), 마지막으로 Framework & Drivers.
    의존성 규칙: 바깥 계층은 안쪽 계층의 인터페이스에만 의존하고, 안쪽은 바깥을 몰라야 한다.
    
    </aside>
    
    - **프로젝트 구조**
        
        ```basic
        📂 src
        └─ main/java
           ├─ entity         : 순수 도메인 모델
           ├─ usecase        : 입력·출력 포트 + 인터랙터
           ├─ interface
           │   ├─ web        : Controller + Presenter
           │   └─ gateway    : DB·외부 API 구현
           └─ framework      : Spring Boot, JPA 등 외부 프레임워크
        ```
        
    
    - **Entity — 순수 비즈니스 규칙**
        
        ```java
        // entity/Order.java
        public class Order {
            private Long id;
            private final String productName;
            private final int qty;
            private final int unitPrice;
            
            public void assignId(Long id) { this.id = id; }
        		public Long getId() { return id; }
        		public String getProductName() { return productName; }
        		public int getQty() { return qty; }
        		public int getUnitPrice() { return unitPrice; }
        
            private Order(String productName, int qty, int unitPrice) {
                this.productName = productName;
                this.qty         = qty;
                this.unitPrice   = unitPrice;
            }
            public static Order create(String p, int q, int price) {
                return new Order(p, q, price);
            }
            public int totalPrice() { return qty * unitPrice; }
            /* getters … */
        }
        ```
        
        - *순수 POJO — 프레임워크·DB·어노테이션 **0개***
    - **Use-Case Port — 입·출력 인터페이스**
        
        ```java
        // usecase/PlaceOrderInput.java
        public interface PlaceOrderInput {
            void place(PlaceOrderCommand command);
        }
        public record PlaceOrderCommand(String product, int qty, int price) {}
        ```
        
        ```java
        // usecase/PlaceOrderOutput.java
        public interface PlaceOrderOutput {
            void ok(PlaceOrderResult result);
        }
        public record PlaceOrderResult(Long id, int totalPrice) {}
        ```
        
        - *안쪽 원(Use-Case)은 **입력·출력 경계 객체**만 정의*
    - **Interactor — 유스케이스 구현**
        
        ```java
        // usecase/PlaceOrderInteractor.java
        public class PlaceOrderInteractor implements PlaceOrderInput {
        
            private final OrderRepository repo;       // ← 내부 포트
            private final PlaceOrderOutput presenter; // ← 외부로 결과 전달
        
            public PlaceOrderInteractor(OrderRepository repo,
                                        PlaceOrderOutput presenter) {
                this.repo = repo;
                this.presenter = presenter;
            }
        
            @Override
            public void place(PlaceOrderCommand c) {
                Order order = Order.create(c.product(), c.qty(), c.price());
                Order saved = repo.save(order);           // DB 호출은 추상 포트
                presenter.ok(new PlaceOrderResult(
                        saved.getId(), saved.totalPrice()));
            }
        }
        ```
        
        - *Interactor 는 **비즈니스 시나리오**만 담당, DB·웹을 모름*
    - **Presenter**
        
        ```java
        // interface/web/PlaceOrderPresenter.java
        @Component
        @Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
        class PlaceOrderPresenter implements PlaceOrderOutput {
        
            private PlaceOrderResult last;
        
            @Override
            public void ok(PlaceOrderResult result) {
                this.last = result;
            }
        
            PlaceOrderResult getLast() {
                return last;
            }
        }
        ```
        
    - **Interface Adapter — 웹 & DB 구현**
        
        ```java
        // interface/web/OrderController.java
        @RestController
        @RequestMapping("/api/v1/orders")
        class OrderController {
        
            private final PlaceOrderInput usecase;
            private final PlaceOrderPresenter presenter;
            
            OrderController(PlaceOrderInput usecase, PlaceOrderPresenter presenter) {
                this.usecase = usecase; 
                this.presenter = presenter;
            }
        
            @PostMapping
            public ResponseEntity<OrderResponse> create(@RequestBody Req r) {
                usecase.place(new PlaceOrderCommand(r.product, r.qty, r.price));
                PlaceOrderResult res = presenter.getLast();
                
                return ResponseEntity
        					    .created(URI.create("/api/v1/orders/" + res.id()))
        					    .body(new OrderResponse(res.id(), res.totalPrice())); // 이름 정합
            }
        
            /* ---- DTO ---- */
            static record Req(String product, int qty, int price) {}
            static record OrderResponse(Long id, int totalPrice) {}
        
        }
        ```
        
        ```java
        // interface/gateway/OrderJpaGateway.java
        @Component
        class OrderJpaGateway implements OrderRepository {
        
            private final SpringDataOrderRepo jpa;
        
            OrderJpaGateway(SpringDataOrderRepo jpa) { this.jpa = jpa; }
            
            private OrderEntity toEntity(Order o) {
        		    return new OrderEntity(o.getId(), o.getProductName(), o.getQty(), o.getUnitPrice());
        		}
        
            @Override
            public Order save(Order o) {
                OrderEntity e = jpa.save(toEntity(o));
                o.assignId(e.getId());
                return o;
            }
        }
        ```
        
        - *Controller 는 **입력 바인딩 + Presenter**, Gateway 는 **DB 세부 구현**만*
    - NestJS
        - 전체 구조 요약
            
            ```tsx
            src/
            ├── entity/                # 순수 도메인 모델
            │   └── order.ts
            ├── usecase/               # 유스케이스 + 입출력 포트
            │   ├── place-order.input.ts
            │   ├── place-order.output.ts
            │   └── place-order.interactor.ts
            ├── interface/
            │   ├── web/               # REST 컨트롤러 + Presenter
            │   │   ├── place-order.presenter.ts
            │   │   └── order.controller.ts
            │   └── gateway/           # DB 어댑터
            │       ├── order.entity.ts
            │       └── order.gateway.ts
            └── app.module.ts          # DI 설정
            
            ```
            
        - Entity (Domain)
            
            ```tsx
            // entity/order.ts
            export class Order {
              private id?: number;
            
              private constructor(
                private readonly productName: string,
                private readonly qty: number,
                private readonly unitPrice: number,
              ) {}
            
              static create(product: string, qty: number, price: number): Order {
                return new Order(product, qty, price);
              }
            
              totalPrice(): number {
                return this.qty * this.unitPrice;
              }
            
              assignId(id: number): void {
                this.id = id;
              }
            
              getId() { return this.id; }
              getProductName() { return this.productName; }
              getQty() { return this.qty; }
              getUnitPrice() { return this.unitPrice; }
            }
            
            ```
            
        - UseCase Port – Input / Output
            
            ```tsx
            // usecase/place-order.input.ts
            import { PlaceOrderCommand } from './place-order.command';
            
            export interface PlaceOrderInput {
              place(command: PlaceOrderCommand): Promise<void>;
            }
            
            export class PlaceOrderCommand {
              constructor(
                public readonly product: string,
                public readonly qty: number,
                public readonly price: number,
              ) {}
            }
            
            // usecase/place-order.output.ts
            export interface PlaceOrderOutput {
              ok(result: PlaceOrderResult): void;
            }
            
            export class PlaceOrderResult {
              constructor(
                public readonly id: number,
                public readonly totalPrice: number,
              ) {}
            }
            ```
            
        - UseCase Interactor
            
            ```tsx
            // usecase/place-order.interactor.ts
            import { Injectable } from '@nestjs/common';
            import { PlaceOrderInput, PlaceOrderCommand } from './place-order.input';
            import { PlaceOrderOutput, PlaceOrderResult } from './place-order.output';
            import { Order } from '../entity/order';
            import { OrderRepository } from '../interface/gateway/order.repository';
            
            @Injectable()
            export class PlaceOrderInteractor implements PlaceOrderInput {
              constructor(
                private readonly repo: OrderRepository,
                private readonly presenter: PlaceOrderOutput,
              ) {}
            
              async place(command: PlaceOrderCommand): Promise<void> {
                const order = Order.create(command.product, command.qty, command.price);
                const saved = await this.repo.save(order);
                this.presenter.ok(new PlaceOrderResult(saved.getId()!, saved.totalPrice()));
              }
            }
            
            ```
            
        - Interface Adapter – Web (Controller + Presenter)
            
            ```tsx
            // interface/web/place-order.presenter.ts
            import { Injectable, Scope } from '@nestjs/common';
            import { PlaceOrderOutput, PlaceOrderResult } from '../../usecase/place-order.output';
            
            @Injectable({ scope: Scope.REQUEST }) // Request 스코프
            export class PlaceOrderPresenter implements PlaceOrderOutput {
              private result: PlaceOrderResult;
            
              ok(result: PlaceOrderResult): void {
                this.result = result;
              }
            
              getLast(): PlaceOrderResult {
                return this.result;
              }
            }
            
            // interface/web/order.controller.ts
            import {
              Controller, Post, Body, HttpCode, HttpStatus, Inject
            } from '@nestjs/common';
            import { PlaceOrderInput, PlaceOrderCommand } from '../../usecase/place-order.input';
            import { PlaceOrderOutput, PlaceOrderResult } from '../../usecase/place-order.output';
            import { PlaceOrderPresenter } from './place-order.presenter';
            
            class CreateOrderRequest {
              product: string;
              qty: number;
              price: number;
            }
            
            class OrderResponse {
              constructor(
                public readonly id: number,
                public readonly totalPrice: number,
              ) {}
            }
            
            @Controller('orders')
            export class OrderController {
            
              constructor(
                @Inject('PlaceOrderInput') private readonly usecase: PlaceOrderInput,
                private readonly presenter: PlaceOrderPresenter, // Presenter 주입
              ) {}
            
              @Post()
              async create(@Body() body: CreateOrderRequest): Promise<OrderResponse> {
                const command = new PlaceOrderCommand(body.product, body.qty, body.price);
                await this.usecase.place(command);
                
                const result = this.presenter.getLast(); // Presenter에서 결과 가져오기
                return new OrderResponse(result.id, result.totalPrice);
              }
            }
            
            ```
            
        - Interface Adapter – Gateway (DB Adapter)
            
            ```tsx
            // interface/gateway/order.entity.ts
            import { Entity, PrimaryGeneratedColumn, Column } from 'typeorm';
            
            @Entity('orders')
            export class OrderEntity {
              @PrimaryGeneratedColumn()
              id: number;
            
              @Column()
              productName: string;
            
              @Column()
              qty: number;
            
              @Column()
              unitPrice: number;
            }
            
            // interface/gateway/order.gateway.ts
            import { Injectable } from '@nestjs/common';
            import { InjectRepository } from '@nestjs/typeorm';
            import { Repository } from 'typeorm';
            import { OrderEntity } from './order.entity';
            import { OrderRepository } from './order.repository';
            import { Order } from '../../entity/order';
            
            @Injectable()
            export class OrderJpaGateway implements OrderRepository {
              constructor(
                @InjectRepository(OrderEntity)
                private readonly repo: Repository<OrderEntity>,
              ) {}
            
              async save(order: Order): Promise<Order> {
                const entity = this.toEntity(order);
                const saved = await this.repo.save(entity);
                order.assignId(saved.id);
                return order;
              }
            
              private toEntity(order: Order): OrderEntity {
                return {
                  id: order.getId(),
                  productName: order.getProductName(),
                  qty: order.getQty(),
                  unitPrice: order.getUnitPrice(),
                };
              }
            }
            
            // interface/gateway/order.repository.ts
            import { Order } from '../../entity/order';
            
            export interface OrderRepository {
              save(order: Order): Promise<Order>;
            }
            
            ```
            
        - AppModule (DI 등록)
            
            ```tsx
            // app.module.ts
            import { Module } from '@nestjs/common';
            import { TypeOrmModule } from '@nestjs/typeorm';
            import { OrderEntity } from './interface/gateway/order.entity';
            import { OrderJpaGateway } from './interface/gateway/order.gateway';
            import { OrderController } from './interface/web/order.controller';
            import { PlaceOrderInteractor } from './usecase/place-order.interactor';
            import { PlaceOrderPresenter } from './interface/web/place-order.presenter';
            
            @Module({
              imports: [TypeOrmModule.forFeature([OrderEntity])],
              controllers: [OrderController],
              providers: [
                OrderJpaGateway,
                PlaceOrderInteractor,
                PlaceOrderPresenter,
                {
                  provide: 'OrderRepository',
                  useExisting: OrderJpaGateway,
                },
                {
                  provide: 'PlaceOrderInput',
                  useClass: PlaceOrderInteractor,
                },
                {
                  provide: 'PlaceOrderOutput',
                  useExisting: PlaceOrderPresenter,
                },
              ],
            })
            export class AppModule {}
            
            ```
            
    
- **정리**
    - **안쪽은 비즈니스, 바깥은 세부사항** → 변경(프레임워크·DB·UI) 시 안쪽 코드 **무변경**
    - **입·출력 경계(Port)** 와 **Interactor** 로 유스케이스를 분리해 **시나리오 단위 테스트**가 간단
    - **모듈 격리·DI 설정**이 Layered보다 복잡하지만, **규칙 변화·채널 다변화** 프로젝트에 탁월
    - 작은 CRUD 앱이면 레이어드로 출발 → 복잡도 상승 시 **클린** 또는 **헥사고날**로 단계적 리팩토링 권장

### **2-4.  패턴 별 장단점 한눈에 보기**

| **구분** | **Layered (계층형)** | **Hexagonal (헥사고날)** | **Clean (클린)** |
| --- | --- | --- | --- |
| **핵심 철학** | 레이어 간 **한 방향** 의존 | **도메인**을 중심으로 포트/어댑터 분리 | **의존성 규칙**으로 안-밖 분리 |
| **비즈니스 로직** | Service 계층 등 중간 계층에 존재. 하지만 하위(DB) 영향 받을 수 있음. | **가장 안쪽**에 위치. 외부와 독립적. | **가장 안쪽**에 위치. 완전히 독립적. |
| **외부 의존성** | 상위 → 하위 호출. UI → Service → DB. | 외부가 내부에 **의존**. DB/UI가 도메인 인터페이스 구현. | 외부가 내부에 **의존**. 프레임워크도 도메인에 영향 없음. |
| **장점** | 단순, 이해 쉬움. 전통적 방식으로 진입장벽 낮음. | 도메인 순수성 유지. 다양한 인터페이스 추가 용이 (테스트 용이) | 도메인 순수성 최고 수준. 변경에 초강력 (유연성 최고) |
| **단점** | 도메인이 DB 등에 종속 위험. 새 I/F 추가 시 기존 코드 수정 큼. | 구조가 복잡, 추상계층 많음. 소규모 프로젝트엔 과할 수 있음. | 개념 학습이 어려움. 초기 설계 많이 필요. |
| **적용 예시** | 전통적인 모놀리식 웹앱 다수. (Spring MVC + 3-layer 등) | DDD 구현, 멀티 UI/인프라 지원 서비스. | DDD 응용, 멀티모듈 대규모 서비스. |
| **원칙 만족도** | DIP 일부 가능, OCP 미흡. | DIP 만족, OCP 만족. | DIP 만족, OCP 만족. |
- 위 표에서 보다시피, **헥사고날**과 **클린**은 방향만 다를 뿐 **원리는 거의 동일**
- 둘을 합쳐 **“클린/헥사고날 아키텍처”**라고 부르기도 함
- 두 패턴 모두 **SOLID의 DIP(의존성 역전)** 원칙을 잘 구현했고, 새로운 요구사항에도 기존 코드 변경이 최소화되도록 **OCP(개방-폐쇄)** 원칙도 충족
- 반면 **전통 레이어드 아키텍처**는 DIP를 적용할 여지는 있지만(예: 인터페이스 활용 가능), 구조적으로는 하위 모듈 변경이 상위에 영향을 주기 쉬워 OCP 달성이 어려움
- **아키텍처 패턴 선택**은 프로젝트와 팀 역량에 맞춰 결정하면 됨
- 만약 소규모 팀이라면 레이어드로 빠르게 개발하고, 이후에 필요에 따라 점진적으로 구조 개선(예: DIP 적용)하는 방법도 있음. 반대로 대규모 서비스나 장기간 유지보수 프로젝트라면 처음부터 공들여 클린 아키텍처 원칙을 적용해 보는 게 좋겠죠.

## **클린 아키텍처 실무 적용 (계층형 구조에 클린 원칙 접목)**

<aside>
💡

이제 **클린 아키텍처**의 의존성 역전 원칙을 실무 계층 구조에 녹여낸 예시를 살펴보겠습니다.

</aside>

![Untitled.png](Step%2003%20Clean%20Architecture/Untitled%203.png)

- 회사에서 많이 사용하는 **레이어드 아키텍처** 위에 **클린의 장점(의존성 규칙)**을 더한다고 생각하면 됩니다.
- 완전히 새로운 구조를 만드는 게 아니라, 기존 Controller-Service-Repository 틀은 유지하되 **도메인 중심 규칙**을 적용하는 것
- 렇게 하면 비교적 학습 비용은 낮추면서도 클린 아키텍처의 이점을 얻을 수 있어 **“Clean + Layered Architecture”**라고 부르기도 함
- 핵심 아이디어
    - **Presentation 레이어**: Controller 등 **외부 입출력** 담당. 여기서는 비즈니스 로직을 직접 하지 않고 **도메인 계층의 기능을 호출**하는 역할만
    - **Application 레이어**: Facade(퍼사드)나 UseCase 클래스로 구성되는 **유스케이스/서비스 조합 계층**. 여러 도메인 서비스들을 orchestrate(조합)하여 하나의 유스케이스 시나리오를 완성 (필요시 트랜잭션 관리 등 부가 로직도 여기서)
    - **Domain 레이어**: **핵심 비즈니스 로직 계층.**  여러 **도메인 객체나 도메인 서비스**, 그리고 **Repository 인터페이스** 등이 이 레이어에 속합니다. 이 레이어의 코드는 **우리 애플리케이션의 순수한 핵심**으로, 어떤 외부 프레임워크에도 의존하지 않음 (Java라면 java.util 같은 기본 라이브러리 외에는 스프링 어노테이션도 웬만하면 안 쓰는 식으로 구성)
    - **Infrastructure 레이어**: DB 연동, 메시징, 파일 etc. **외부 기술 구현체**들이 모여 있음. Domain 레이어의 **인터페이스(예: Repository)**를 실제로 구현하는 클래스들이 여기에 있고, 그 구현에 스프링, JPA, TypeORM 같은 구체 기술을 사용
- 이 구조에서 **제일 중요한 규칙**:
    - **Domain (안쪽)** 코드는 **Infrastructure나 Presentation (바깥)** 코드를 절대 모름
    - 반대로 **바깥 코드가 안쪽 인터페이스를 사용**
    - 다시 말해, **의존성 화살표가 모두 안쪽을 향하도록** 강제
- 예시코드
    - **프로젝트 구조**
        
        ```text
        src/
         ├ interfaces/          # UI · API
         │   └ web/OrderController.java
         ├ application/         # 유스케이스
         │   └ PlaceOrderService.java
         ├ domain/              # 순수 모델·규칙
         │   ├ model/Order.java
         │   └ repository/OrderRepository.java
         └ infrastructure/
             └ persistence/
                 ├ OrderEntity.java          # JPA DTO
                 └ OrderJpaRepository.java   # 어댑터(매핑 포함)
        ```
        
    - **Domain 모델 차이**
        - **이전 - JPA 의존 엔티티**
            
            ```java
            @Entity
            @Table(name="orders")
            public class Order {          // ← JPA에 직접 의존
                @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
                private Long id;
                private String productName;
                private int quantity;
                private int unitPrice;
                private LocalDateTime orderedAt;
            
                protected Order() {}                      // JPA 기본 생성자
                private Order//(...) { ... }                // 공장 메서드 create()
                public int totalPrice() { ... }           // 규칙 OK
            }
            ```
            
        - 이후 - **순수 POJO + JPA DTO 분리**
            
            ```java
            // domain/model/Order.java          ← **JPA 어노테이션 삭제**
            public class Order {
                private Long id;
                private final String productName;
                private final int quantity;
                private final int unitPrice;
                private final LocalDateTime orderedAt;
            
                private Order(String p,int q,int up){ ... }
                public static Order create(String p,int q,int up){ ... }
                public int totalPrice(){ ... }
                void assignId(Long id){ this.id=id; }     // 인프라만 접근
            }
            ```
            
            ```java
            // infrastructure/persistence/OrderEntity.java
            @Entity @Table(name="orders")
            class OrderEntity {                           // JPA 전용 DTO
                @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
                Long id;
                String productName;
                int quantity;
                int unitPrice;
                LocalDateTime orderedAt;
            }
            ```
            
        - **핵심 규칙 보호**: 도메인 모델에 JPA 어노테이션이 사라지면서, 비즈니스 로직이 *“어떤 DB·ORM을 쓰는지”* 전혀 모르게 됨
        - **프레임워크 교체 자유**: RDB → Mongo / Elastic 같은 저장소 전환 시에도 Order 코드는 **단 한 줄도 수정되지 않음**—새 인프라 DTO·어댑터만 추가하면 끝
        - **모델 경량화**: JPA용 기본 생성자·프록시 보호 등 불필요한 보일러플레이트 제거 → 도메인 클래스가 읽기 쉬워지고 핵심 규칙(총액 계산 등)이 눈에 잘 들어옴
    - **Service / Use-Case 레이어**
        - **이전**
            
            ```java
            package com.example.service;
            
            import com.example.entity.Order;
            import com.example.repository.OrderRepository;
            import org.springframework.stereotype.Service;
            import org.springframework.transaction.annotation.Transactional;
            
            @Service
            public class OrderService {
            
                private final OrderRepository orderRepository;   // Spring-Data Repo에 바로 의존
            
                public OrderService(OrderRepository orderRepository) {
                    this.orderRepository = orderRepository;
                }
            
                /**
                 * 단일 유스케이스: 주문 생성
                 * - 도메인 객체 생성
                 * - JPA Repository 로 바로 저장
                 */
                @Transactional
                public Order placeOrder(String productName, int qty, int unitPrice) {
                    Order order = Order.create(productName, qty, unitPrice);
                    return orderRepository.save(order);          // 영속화 세부 사항 직접 호출
                }
            }
            ```
            
        - 이후
            
            ```java
            package com.example.application;
            
            import com.example.domain.model.Order;
            import com.example.domain.repository.OrderRepository;
            
            /**
             * 순수 유스케이스 클래스
             * - @Service, 스프링 의존성 제거 (DI는 구성 클래스에서)
             * - 트랜잭션 경계만 남기고, 프레임워크·DB 세부 지식 없음
             */
            public class PlaceOrderService {
            
                private final OrderRepository orderRepository;   // 도메인 포트(인터페이스)에만 의존
            
                public PlaceOrderService(OrderRepository orderRepository) {
                    this.orderRepository = orderRepository;
                }
            
                /** 주문 생성 유스케이스 */
                @org.springframework.transaction.annotation.Transactional
                public Order place(String productName, int qty, int unitPrice) {
                    Order order = Order.create(productName, qty, unitPrice);
                    return orderRepository.save(order);          // 추상 포트 호출
                }
            }
            ```
            
        - 이름을 **“무엇을 하는가(PlaceOrder)”** 로 명시 → 유스케이스 의미 강조
        - 스프링 어노테이션·프레임워크 의존 제거 → 테스트 용이
        - 의존 대상이 **Spring Data Repo ⟶ 도메인 인터페이스**
    - **Repository 구현**
        - **이전**
            
            ```java
            public interface OrderRepository   // Spring Data 직접 상속
                   extends JpaRepository<Order, Long> { }
            ```
            
        - 이후
            
            ```java
            // domain.repository.OrderRepository
            public interface OrderRepository {
                Order save(Order order);
            }
            ```
            
            ```java
            // infrastructure.persistence.OrderJpaRepository
            @Repository
            class OrderJpaRepository implements OrderRepository {
                private final SpringOrderJpa jpa;     // 내부 JPA Repo
            
                public Order save(Order o) {
                    OrderEntity e = toEntity(o);
                    OrderEntity saved = jpa.save(e);
                    o.assignId(saved.id);
                    return o;
                }
                
                /** Domain → JPA DTO 매핑 (Infra 전용) */
                private OrderEntity toEntity(Order o) {
                    OrderEntity e = new OrderEntity();
                    e.productName = o.getProductName();
                    e.quantity    = o.getQuantity();
                    e.unitPrice   = o.getUnitPrice();
                    e.orderedAt   = o.getOrderedAt();
                    return e;
                }
            }
            ```
            
        - domain.repository.OrderRepository 가 *추상 포트*
        - JPA 구현은 Infrastructure 레이어로 이동
        - toEntity **왜 Infrastructure 쪽인가?**
            - **도메인 순수성 유지**
                - Order 가 JPA 엔티티를 알게 되면 다시 프레임워크에 결합됩니다. 매핑 책임을 바깥(Infra)으로 밀어야 “도메인은 프레임워크 무지식” 상태가 유지됩니다.
            - **의존성 방향**
                - 의존성 화살표가 *Domain ← Infrastructure* 로 향해야 DIP/OCP가 성립합니다. 매퍼가 Infra 안에 있으면 Order 는 모르는 반면, 어댑터는 두 타입을 모두 볼 수 있습니다.
            - **구현 세부사항 격리**
                - JPA, QueryDSL, MapStruct 등 어떤 라이브러리를 쓰든 **Infra 내부**에서 바꿔 끼우면 되므로 도메인·애플리케이션층은 영향을 받지 않습니다.
    - **Controller (거의 그대로)**
        
        ```java
        @PostMapping
        public ResponseEntity<Res> create(@RequestBody Req r){
            Order o = placeOrderService.place(
                         r.product(), r.qty(), r.unitPrice());
            return ResponseEntity.ok(new Res(o.getId(),o.totalPrice()));
        }
        ```
        
        - 단지 **PlaceOrderService** 타입이 바뀌었을 뿐 로직은 동일
        - 여전히 HTTP ↔ 도메인 호출 변환만 담당
    - NestJS
        - 전체 구조 요약
            
            ```text
            src/
            ├ interfaces/              
            │   └─ web/                    # Controller (UI 레이어)
            │       └─ order.controller.ts
            ├ application/                # Use-case 계층
            │   └─ place-order.service.ts
            ├ domain/                     # 순수 도메인 + 포트
            │   ├─ model/
            │   │   └─ order.ts
            │   └─ repository/
            │       └─ order.repository.ts
            └ infrastructure/
                └─ persistence/
                    ├─ order.entity.ts        # TypeORM 전용 엔티티
                    ├─ spring-order.repo.ts   # TypeORM Repo
                    └─ order-jpa.repository.ts # 실제 어댑터 구현체
            
            ```
            
        - Domain Model (순수 비즈니스 로직)
            
            ```tsx
            // domain/model/order.ts
            export class Order {
              private id?: number;
            
              constructor(
                private readonly productName: string,
                private readonly quantity: number,
                private readonly unitPrice: number,
                private readonly orderedAt: Date = new Date(),
              ) {}
            
              static create(productName: string, quantity: number, unitPrice: number): Order {
                return new Order(productName, quantity, unitPrice);
              }
            
              assignId(id: number) {
                this.id = id;
              }
            
              totalPrice(): number {
                return this.quantity * this.unitPrice;
              }
            
              getId() { return this.id; }
              getProductName() { return this.productName; }
              getQuantity() { return this.quantity; }
              getUnitPrice() { return this.unitPrice; }
              getOrderedAt() { return this.orderedAt; }
            }
            
            ```
            
        - Domain Port (Repository 인터페이스)
            
            ```tsx
            // domain/repository/order.repository.ts
            import { Order } from '../model/order';
            
            export interface OrderRepository {
              save(order: Order): Promise<Order>;
            }
            
            ```
            
        - Infrastructure Entity (ORM 전용 DTO)
            
            ```tsx
            // infrastructure/persistence/order.entity.ts
            import { Entity, Column, PrimaryGeneratedColumn } from 'typeorm';
            
            @Entity('orders')
            export class OrderEntity {
              @PrimaryGeneratedColumn()
              id: number;
            
              @Column()
              productName: string;
            
              @Column()
              quantity: number;
            
              @Column()
              unitPrice: number;
            
              @Column()
              orderedAt: Date;
            }
            
            ```
            
        - Infrastructure Adapter (Repository 구현)
            
            ```tsx
            // infrastructure/persistence/order-jpa.repository.ts
            import { Injectable } from '@nestjs/common';
            import { InjectRepository } from '@nestjs/typeorm';
            import { Repository } from 'typeorm';
            import { OrderRepository } from '../../domain/repository/order.repository';
            import { Order } from '../../domain/model/order';
            import { OrderEntity } from './order.entity';
            
            @Injectable()
            export class OrderJpaRepository implements OrderRepository {
              constructor(
                @InjectRepository(OrderEntity)
                private readonly repo: Repository<OrderEntity>,
              ) {}
            
              async save(order: Order): Promise<Order> {
                const entity = this.toEntity(order);
                const saved = await this.repo.save(entity);
                order.assignId(saved.id);
                return order;
              }
            
              private toEntity(order: Order): OrderEntity {
                const entity = new OrderEntity();
                entity.productName = order.getProductName();
                entity.quantity = order.getQuantity();
                entity.unitPrice = order.getUnitPrice();
                entity.orderedAt = order.getOrderedAt();
                return entity;
              }
            }
            
            ```
            
        - Application Layer (Use-case / Service)
            
            ```tsx
            // application/place-order.service.ts
            import { Injectable } from '@nestjs/common';
            import { Order } from '../domain/model/order';
            import { OrderRepository } from '../domain/repository/order.repository';
            
            @Injectable()
            export class PlaceOrderService {
              constructor(private readonly orderRepository: OrderRepository) {}
            
              async place(productName: string, quantity: number, unitPrice: number): Promise<Order> {
                const order = Order.create(productName, quantity, unitPrice);
                return this.orderRepository.save(order);
              }
            }
            
            ```
            
        - Controller (UI 계층)
            
            ```tsx
            // interfaces/web/order.controller.ts
            import {
              Body,
              Controller,
              Post,
              HttpCode,
              HttpStatus,
            } from '@nestjs/common';
            import { PlaceOrderService } from '../../application/place-order.service';
            import { Order } from '../../domain/model/order';
            
            class CreateOrderRequest {
              productName: string;
              quantity: number;
              unitPrice: number;
            }
            
            class OrderResponse {
              constructor(
                public readonly id: number,
                public readonly totalPrice: number,
              ) {}
            }
            
            @Controller('orders')
            export class OrderController {
              constructor(private readonly placeOrderService: PlaceOrderService) {}
            
              @Post()
              @HttpCode(HttpStatus.CREATED)
              async create(@Body() req: CreateOrderRequest): Promise<OrderResponse> {
                const order: Order = await this.placeOrderService.place(
                  req.productName,
                  req.quantity,
                  req.unitPrice,
                );
                return new OrderResponse(order.getId()!, order.totalPrice());
              }
            }
            
            ```
            
        - DI 등록 (AppModule)
            
            ```tsx
            // app.module.ts
            import { Module } from '@nestjs/common';
            import { TypeOrmModule } from '@nestjs/typeorm';
            import { OrderController } from './interfaces/web/order.controller';
            import { PlaceOrderService } from './application/place-order.service';
            import { OrderJpaRepository } from './infrastructure/persistence/order-jpa.repository';
            import { OrderEntity } from './infrastructure/persistence/order.entity';
            
            @Module({
              imports: [TypeOrmModule.forFeature([OrderEntity])],
              controllers: [OrderController],
              providers: [
                PlaceOrderService,
                OrderJpaRepository,
                {
                  provide: 'OrderRepository',
                  useExisting: OrderJpaRepository,
                },
              ],
            })
            export class AppModule {}
            
            ```
            
            - 적용 방식 요약
                - **Controller**: 여전히 `NestJS`의 `@Controller`를 사용해 요청을 받음.
                - **Service / Use-case**:
                    - `@Injectable()` 은 사용하지만, 내부적으로는 **비즈니스 시나리오 단위의 유스케이스**로 설계.
                    - 주입받는 대상은 TypeORM Repo가 아니라 **도메인 레이어의 인터페이스(Port)**.
                - **Domain**:
                    - `Order` 모델은 **POJO**로 프레임워크 의존 없이 비즈니스 로직에만 집중.
                - **Repository**:
                    - `OrderRepository` 는 **도메인 포트 (인터페이스)**.
                    - 실제 저장소 구현은 **infrastructure/persistence** 에 위치.
                    - `DIP(의존성 역전 원칙)` 을 따름 → 상위 모듈(Service)은 인터페이스에만 의존.
    - **📝 변화 효과 요약**
        
        
        | **항목** | **Before (전통 Layered)** | **After (Clean + Layered)** |
        | --- | --- | --- |
        | **도메인 순수성** | JPA 어노테이션, Spring 의존 | 의존 0 (POJO) |
        | **의존 방향** | Controller → Service → *SpringData*Repo | Controller → Application → *Domain Port* ← Infrastructure |
        | **테스트 난이도** | 도메인 테스트 시 스프링 컨텍스트 필요 | Port Mock 주입 - JVM 단위 테스트 OK |
        | **DB 교체 비용** | Service 메서드·엔티티 수정 필요 | Infrastructure 어댑터 추가/교체만 |
        | **학습 부담** | 낮음 | 소폭 상승(계층·패키지 구분) |
    - 결론
        - 코드는 거의 변하지 않지만 “핵심 규칙이 프레임워크 무관” 해져서
            - 기술 교체·멀티 채널(I/O) 대응이 쉬워지고,
            - 단위 테스트가 가벼워짐
            - 단순 CRUD만 있는 팀이라면 굳이 After 를 강제할 필요는 없고, 확장성·장기 유지보수가 예상될 때 점진적으로 전환하면 됩니다.
        - 실무 TIP: 계층 구조에 클린 아키텍처 도입
            - 처음부터 모든 것을 클린 아키텍처로 작성하는 게 부담스럽다면, 우선 **Repository 의존성 분리 도메인 로직과 기술적 구현을 구분**

### **실무 고민: JPA Entity와 Domain Model 분리해야 할까?**

- 결론부터 말하면 **상황에 따라 다름. 정답은 없고 트레이드오프만 존재**—팀 역량·프로젝트 수명·도메인 복잡도를 보고 결정!
- 동일하게 쓰면 개발 속도는 빠름. JPA 엔티티를 곧바로 서비스 로직에서 사용하면 변환도 필요 없고, 영속성 컨텍스트도 편하게 활용할 수 있음
- 그래서 **짧은 프로젝트나 팀 리소스가 적을 때는 굳이 분리 안 하고 쓰는 경우도 많음.**
(특히 주니어 개발자들로 팀이 구성되었다면 일단 한 클래스로 몰아서 구현하는 편이 실용적일 수 있음.)
- **도메인 규칙이 복잡**하거나 **엔티티가 너무 거대해지는 경우**에는 분리하는 게 유리할 수 있음
- 분리하면 도메인 객체는 순수 자바/자바스크립트 객체로서 **DB와 무관한 상태**를 유지하니 테스트도 편하고, 영속성 이벤트(더티 체킹 등)에 휘둘리지 않게됨.
- 하지만 대가로 엔티티를 분리하면 JPA의 변경 감지(dirty checking)나 연관관계 매핑 같은 편의를 바로 쓸 수 없기때문에 **ORM의 마법을 포기**하고 더 명시적인 코딩을 해야 함
- **예시(Order)** 기준 선택 가이드
    - **단순 CRUD**·짧은 프로젝트 → @Entity Order 한 클래스로 개발 (전통 Layered 버전)
    - **규칙 확장**·채널 다변화 예상 → Order(POJO) + OrderEntity 분리 (Clean + Layered 버전)
- **실전 권장 플로우**
    - **“엔티티 = 도메인”** 으로 MVP·프로토타입 빠르게 완성
    - 도메인 규칙이 복잡·거대해지면 **중요 Aggregate**부터 POJO 분리
    - 매핑 로직은 **infrastructure/persistence** 어댑터 내부 또는 전용 Mapper 클래스로 위치

## **클린 코드와 가독성: 유지보수성을 높이는 팁**

<aside>
💡

지금까지 아키텍처 구조를 잘 잡았다면, **그 안을 채우는 코드**도 깔끔해야 진정한 의미의 “클린”이 완성됩니다. 이번 파트에서는 **클린 코드 원칙** 몇 가지를 짚으며, 아키텍처 측면에서 **가독성과 유지보수성**을 높이는 방법을 알아보겠습니다.

</aside>

- **의미 있는 이름과 일관성있는 설계**
    - 가장 기본이자 중요한 것은 **코드의 이름짓기(Naming)**
    - 클래스, 함수, 변수 이름만 잘 지어도 **코드의 의도**가 드러나서 읽기 쉬움
    - 예를 들어 calculateUserScore()라는 함수명은 내부가 궁금하지 않아도 역할이 명확
    - 반면 processData() 같은 모호한 이름은 코드를 열어봐야 하니 피해야 함
    - 또한 **일관성 있는 설계**를 유지하는 것도 중요
    - 동일한 개념에는 동일한 단어를 쓰고, 비슷한 동작을 하는 메서드는 다른 클래스에서도 유사한 방식으로 작성하면 읽는 사람이 예측하기 쉬움
        - **나쁜 예:** Mgr, calc2() 처럼 약어 또는 의미 없는 숫자 등.
        - **좋은 예:** UserManager, calculateOrderTotal() 등 구체적이고 통일성 있게.
- **주석보다는 코드로 설명하기**
    - 주석(// ...)은 코드 이해에 도움을 주지만, **가능하면 주석 없이도 이해되는 코드**가 이상적
    - 주석이 너무 많다는 건 코드 자체가 복잡하거나 의도가 드러나지 않는다는 방증일 수 있음
    - 예를 들어 복잡한 비즈니스 규칙을 구현해야 한다면, 그 로직을 잘게 쪼개고 적절한 함수 이름으로 추출해볼 수 있음
    - 그러면 굳이 “// ~을 처리함” 같은 주석이 없어도 함수 이름이 설명을 대신해줌
    - 물론 **정말 필요한 주석**(특별한 알고리즘 설명 등)은 쓰되, **가능하면 코드만으로 소통**하는 걸 권장
- **DRY 원칙 – 중복 배제하기**
    - **DRY (Don’t Repeat Yourself)**, 중복을 피하라는 원칙도 유지보수성에 직결됩됨
    - 코드가 여러 군데 복붙되어 있으면, 나중에 요구사항 변경 시 그 부분을 **모두 찾아서 고쳐야 하는 위험**이 커짐
    - 아키텍처 설계 단계에서 중복이 생기지 않도록 책임을 잘 분리해야 하고, 구현 단계에서도 **함수 추출, 공통 모듈화** 등을 통해 중복을 최소화
    - 예를 들어 동일한 검증 로직이 두 서비스에 필요하다면 Validator 유틸 클래스로 뽑아내는 식
    - 단, DRY를 추구한다고 너무 이른 추상화는 하지 말고, **실제로 중복이 발생했을 때 리팩토링**하는 게 좋음
- **작은 함수와 높은 응집도**
    - 클린 코드의 또 다른 핵심은 **함수를 작게 유지**하는 것
    - 한 함수가 100줄이 넘어서 여기저기 분기문을 타고 들어간다면 읽는 사람은 금방 지치고 문제 발생시 해결이 어려워짐
    - 대신 하나의 함수가 **한 가지 일만** 하도록 쪼개는 것이 좋음 (Single Responsibility 원칙의 함수 버전).
    - 함수가 작아지면 **응집도(cohesion)**가 높아지고, 자연히 이름도 간결해지며, 재사용이나 수정도 쉬워짐
    - 객체 지향 설계 원칙에서 가장 기본인 SRP(단일 책임 원칙)을 상기하며, **각 클래스가 자신에게 주어진 역할만 수행해야함**
- **결합도 낮추기 (Loosely Coupling)**
    - 아키텍처 수준에서 의존성 분리를 이야기했지만, 구현 단계에서도 **모듈 간 결합도를 낮추는 습관**이 중요
    - **부품(클래스·함수)끼리 “헐겁게만” 이어 두자**는 뜻
    - 예를 들어 한 함수가 전역 변수나 다른 클래스의 내부 구현에 깊이 의존한다면, 수정 시 연쇄적인 영향이 생겨 유지보수가 어려워짐
    - “이 메서드에 *무엇을 넣으면* *무엇이 나온다*”만 명확히 하고, 내부 구현은 캡슐화(encapsulation)하는것이 이상적
- **테스트 코드로 꾸준히 건강 체크**
    - 클린 코드와 아키텍처 유지보수에서 **테스트 코드**는 빠질 수 없음
    - 작성한 코드에 대한 단위 테스트, 통합 테스트를 통해 **지속적으로 리팩토링해도 기능이 안전하게 보호**됨을 확인해야함
    - 테스트 코드는 개발자를 번거롭게 하는 추가 업무가 아니라, **미래의 자신에게 주는 안전망**
    - 특히 아키텍처 리팩토링처럼 큰 변화가 필요할 때 광범위한 테스트가 있다면 마음 놓고 개선을 시도할 수 있음

- 요약하면, **클린 코드**의 핵심은 **“남이 읽기 쉬운 코드”**
- 남은 때로는 미래의 내가 되기도 하고, 동료나 후임 개발자가 될 수도 있죠. 아키텍처를 멋지게 설계해놓아도 내부 코드가 난해하면 소용없음
- **이해하기 쉬운 코드**를 함께 지향할 때 비로소 클린 아키텍처의 장점이 극대화될 것

## **결론 및 아키텍처 체크리스트**

<aside>
💡

마무리하기 전에 오늘 논의한 내용을 간략히 정리해보겠습니다. 소프트웨어 아키텍처를 설계할 때 우리에게 필요한 것은 결국 **균형 감각**입니다. **현재 주어진 팀의 역량과 프로젝트 상황**에서 최선의 구조를 선택하고, 원칙들은 방향을 제시하는 지침으로 삼으면 됩니다.

</aside>

- 클린 아키텍처의 이상을 좇다 보면 가끔 현실과 부딪히기도 합니다. 일정이 촉박한데 굳이 모든 레이어를 추상화할 필요는 없죠. 그럴 땐 **우선 순위를 정해서 적용**하면 됩니다. 예를 들어 정말 복잡한 핵심 도메인 모듈에만 클린 아키텍처를 적용하고, 주변 모듈은 단순 레이어드로 남겨둘 수도 있어요. 중요한 것은 **팀 내에서 아키텍처에 대한 공감대**를 형성하고, 코드리뷰 등을 통해 같이 지켜나가는 것입니다.
- 마지막으로, **아키텍처 설계 시 자문자답할 질문들**을 **체크리스트** 형식으로 드릴게요. 새로운 서비스를 설계하거나 기존 코드를 리팩토링할 때, 아래 질문을 스스로에게 던져보세요:
    - **의존성 방향이 올바른가?** – 핵심 비즈니스 로직이 외부 세부 사항에 의존하고 있지는 않은지 확인하기 (ex: 도메인 코드에서 DB 모듈 import하고 있으면 X)
    - **변경 용이성 (OCP)** – 새로운 요구사항이 생겼을 때, 기존 코드를 수정하지 않고도 추가할 수 있는 구조인가? 그렇지 않다면 어디가 폐쇄되어 있지 않은가?
    - **단일 책임 (SRP)** – 각각의 모듈/클래스/함수가 하나의 역할만 담당하고 있는가? “그리고(and)”가 클래스 설명에 들어가면 냄새일 수 있습니다.
    - **중복 코드 (DRY)** – 비슷한 코드가 여러 곳 복사되어 있지는 않은가? 공통 유틸이나 추상화로 뽑아낼 여지는 없는지 점검하기.
    - **명확한 의도** – 코드의 의도가 읽어서 바로 이해되는가? 모호한 이름이나 깊은 복잡도가 있다면 주석으로라도 보완했는가? (차후 리팩토링 후보)
    - **테스트 가능성** – 현재 구조에서 핵심 로직을 단위 테스트하기 쉬운가? 만약 어렵다면 의존성 분리가 부족한 부분은 없는지 살펴보기 (ex: 하드코딩된 의존은 없는지).
    - **팀 컨벤션 부합** – 코드 구조와 스타일이 팀이 합의한 가이드라인에 맞는가? 유사 기능 간 일관성이 유지되고 있는지 확인.
- 항목들을 주기적으로 체크하면, **아키텍처가 처음 의도한 방향에서 크게 벗어나지 않도록** 잡아줄 것입니다.
- **클린 아키텍처**란 궁극적으로 **“변경에 유연한 시스템 만들기”**라고 할 수 있습니다. 오늘 배운 개념들을 토대로, 여러분의 실무 프로젝트에 한 가지씩이라도 적용해 보세요. 처음부터 거창하게 모든 걸 바꾸기보다, **작은 개선들**을 통해 점진적으로 아키텍처를 깨끗하게 만들어나가길 바랍니다.

## 과제 : **클린 아키텍처 구현**

<aside>
❓ 아키텍처와 테스트 코드 작성에 집중하며, 견고하고 유연한 서버 개발이 목표인 사람 (챌린지 과제가 포함되어 있습니다)

[e-커머스 서비스](Step%2003%20Clean%20Architecture/e-%E1%84%8F%E1%85%A5%E1%84%86%E1%85%A5%E1%84%89%E1%85%B3%20%E1%84%89%E1%85%A5%E1%84%87%E1%85%B5%E1%84%89%E1%85%B3.md)

[콘서트 예약 서비스](Step%2003%20Clean%20Architecture/%E1%84%8F%E1%85%A9%E1%86%AB%E1%84%89%E1%85%A5%E1%84%90%E1%85%B3%20%E1%84%8B%E1%85%A8%E1%84%8B%E1%85%A3%E1%86%A8%20%E1%84%89%E1%85%A5%E1%84%87%E1%85%B5%E1%84%89%E1%85%B3.md)

</aside>

### `필수 과제 - 로직 구현 및 테스트코드 작성`

- 각 시나리오별 하기 **비즈니스 로직** 개발 및 **단위 테스트** 작성
    - `e-commerce` : 상품 조회, 주문/결제 기능, 포인트 충전 기능
    - `concert` : 콘서트 조회, 예약/결제 기능, 포인트 충전 기능
    - 단, 비지니스 로직에서 다음을 따라야 합니다.
        - 한개의 비지니스 로직을 클린아키텍처로 구현하고 다른 비지니스 로직은 레이어드로 구현할것
            - “e-커머스 시나리오에서 '주문/결제 기능'을 클린 아키텍처로 구현”
                - 도메인 로직이 복잡
                - 여러 모듈(예: 결제 게이트웨이, 재고 관리, 사용자 관리 등)과 상호작용해야 해서 클린 아키텍처의 장점을 살리기 좋기 때문
            - “콘서트 시나리오에서는 '예약/결제 기능'을 클린 아키텍처로 구현”
                - 좌석 예약, 결제 프로세스, 알림 발송 등 여러 기능이 유기적으로 결합되어 있어서 클린 아키텍처를 적용하면 각 책임을 명확히 분리하고 테스트하기 쉬움
            - 여러 기능 Mock을 적극 활용해주세요
                - 예를들어
                    - `주문/결제 기능` 테스트 시에는 `ProductRepository`, `UserRepository`, `PaymentGateway`, `EventPublisher` 등은 모두 Mock으로 대체하고,
                    OrderUseCase 자체의 도메인 로직만 검증하도록 작성해야 합니다.
                    이는 외부 시스템에 의존하지 않고도 순수한 비즈니스 로직 테스트가 가능하게 하기 위함입니다.
                    - `좌석 예약/결제` 기능에서는 `SeatRepository`, `ReservationRepository`, `PaymentService` 등을 전부 Mock 처리하고,
                    좌석 점유/해제, 결제 완료 처리 등의 예약 로직만 검증하도록 작성합니다.

> **단위 테스트** 는 반드시 대상 객체/기능 에 대한 의존성만 존재해야 함
> 

<aside>
<img src="https://www.notion.so/icons/light-bulb_red.svg" alt="https://www.notion.so/icons/light-bulb_red.svg" width="40px" />

**참고 PR 링크**

| 스택 | STEP3 |
| --- | --- |
| Java | [https://github.com/BEpaul/hhplus-e-commerce/pull/20](https://github.com/BEpaul/hhplus-e-commerce/pull/20) |
| Java | [https://github.com/juny0955/hhplus-concert/pull/14](https://github.com/juny0955/hhplus-concert/pull/14) |
| Kotlin | [https://github.com/chapakook/kotlin-ecommerce/pull/30](https://github.com/chapakook/kotlin-ecommerce/pull/30) |
| Kotlin | [https://github.com/psh10066/hhplus-server-concert/pull/19](https://github.com/psh10066/hhplus-server-concert/pull/19) |
| TS | [https://github.com/psyoongsc/hhplus-ts-server/pull/20](https://github.com/psyoongsc/hhplus-ts-server/pull/20) |
| TS | [https://github.com/suji6707/nestjs-case-02-ticketing/pull/3](https://github.com/suji6707/nestjs-case-02-ticketing/pull/3) |
</aside>

### **`(선택)심화 과제 - 심화 로직 구현 및 테스트코드 작성`**

- 각 시나리오별 하기 **비즈니스 로직** 개발 및 **단위 테스트** 작성
    - `e-commerce` : 선착순 쿠폰 관련 기능
    - `concert` : 대기열 관련 기능

> **단위 테스트** 는 반드시 대상 객체/기능 에 대한 의존성만 존재해야 함
심화 과제까지 마무리한 후에는, 로그인 기능등 남은 요구사항들도 차례로 확장해보는 것을 추천드립니다~!
> 

---

Copyright ⓒ TeamSparta All rights reserved.