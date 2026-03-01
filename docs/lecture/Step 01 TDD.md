# Step 01. TDD

## 이번 주차에서 배울 것

<aside>
⛵ **TDD 챕터 목표**

</aside>

- **테스트 가능한 코드(Testable Code)의 의미를 명확히 이해하고, 다양한 종류의 테스트를 작성하며, TDD 기반의 요구사항 기능 개발을 학습합니다.**
- TDD(Test-Driven Development)의 개념과 프로세스(Red-Green-Refactor)를 학습하고, 실제 실무에서 적용할 수 있도록 연습합니다.
- 상황에 따라 적절한 테스트를 작성하는 전략을 학습합니다.
- 단순히 테스트를 작성하는 것을 넘어, 왜 테스트가 필요한지 근본적인 목적과 중요성을 이해합니다.
- 주어진 과제를 분석하고, TDD 방식을 이용해 직접 기능을 구현하는 경험을 쌓습니다.

**🔎 왜 이번 주차의 목표가 중요할까요?**

- 최근에는 GitHub Copilot, Cursor AI와 같은 AI 도구들이 개발자의 테스트 코드 작성을 도와줍니다. 그러나 실제로 AI가 작성한 코드가 내가 원하는 대로 정확히 작동하는지 검증하는 일은 여전히 개발자의 책임입니다.
- TDD를 학습하고 활용하는 것은 앞으로의 시대에서 개발자의 기본기를 더욱 탄탄하게 만들어줄 것입니다. AI 도구가 발전할수록 개발자가 스스로 작성한 테스트 코드와, 이를 통해 기능을 명확히 검증할 수 있는 능력이 매우 중요해지기 때문입니다.
- TDD는 단순히 테스트를 작성하는 방법론에 그치지 않고, 유지보수하기 쉬운 코드, 명확한 설계 구조를 만드는 기반입니다. 개발자로서 TDD를 깊이 있게 이해하고 활용할 수 있다면, AI와의 협업에서도 효율적이고 신뢰성 있는 개발을 할 수 있게 됩니다.

🔥 이번 학습을 통해 TDD의 본질적인 가치를 이해하고, 더 견고하고 명확한 소프트웨어 개발자로 성장하는 계기로 삼아봅시다.

## Why TDD?

<aside>
❗ **TDD, 왜 중요할까?**

소프트웨어의 규모가 커지고 사용자 수가 많아짐에 따라 **예측하기 어려운 장애**가 자주 발생합니다. 전통적인 개발 방식으로는 이러한 문제를 안정적으로 해결하기 어렵고, **지속적인 품질 유지와 빠른 변화 대응**을 위해 **자동화된 테스트와 TDD의 중요성**이 더욱 강조되고 있습니다.

특히, 요구사항 변경 시 기존 기능에 영향을 주지 않는지를 빠르게 확인할 수 있는 TDD는, **유지보수와 확장에 유리한 개발 방식**으로 각광받고 있습니다.

</aside>

- **왜 테스트 케이스가 중요할까요?**
    
    소프트웨어 개발은 단순히 새로운 기능 추가가 아니라, 다양한 상황과 복잡한 조건 속에서 개발자의 의도와 다르게 작동할 수 있습니다. 예를 들어, 운영자의 권한 등급, 사용자 유형 등 다양한 조건이 끼어드는 실무 환경에서는 개발자가 모든 상황을 수동으로 테스트하기 어렵습니다.
    
    이때, **테스트 케이스가 없다면** 우리는 매번 수작업으로 기능을 점검하거나 QA팀에 의존할 수밖에 없으며, 이는 많은 시간과 비용을 소모하게 됩니다.
    
    반면 **테스트 케이스가 존재한다면**, 자동화된 프로세스를 통해 빠르게 문제를 확인하고, **지속적으로 안정적인 품질을 유지할 수 있습니다**. 이는 결과적으로 빠른 배포 주기와 높은 신뢰성으로 이어져, 팀 전체의 효율성을 높여줍니다.
    
- **현업에서 TDD를 어떻게 적용할 수 있을까요?**
    
    많은 개발자들이 TDD의 중요성을 인지하고 있으며, 현업에서도 이를 실질적으로 적용하는 사례가 점점 늘고 있습니다. TDD를 통해 얻을 수 있는 대표적인 장점은 다음과 같습니다:
    
    - 빠른 결함 발견 및 문제 해결
    - 팀 내 코드 품질 및 협업 효율 향상
    - CI/CD 자동화를 통한 빠른 배포 사이클
    
    처음부터 TDD를 완벽히 적용하는 것이 어렵더라도, 우선적으로 **핵심 기능에 대한 단위 테스트(Unit Test)를 작성하는 습관**을 들이는 것이 좋은 시작점입니다. 이를 통해 점진적으로 테스트 주도 개발의 문화와 경험을 쌓을 수 있습니다.
    
- **TDD가 협업과 품질에 미치는 영향**
    
    테스트 코드가 잘 작성된 프로젝트에서는:
    
    - 코드 리뷰가 쉬워지고 협업이 원활해집니다.
    - 리팩토링과 유지보수가 쉬워져 팀의 코드 개선이 적극적으로 이뤄집니다.
    - CI/CD 파이프라인에서 자동화된 테스트를 통해 코드 품질이 지속적으로 유지됩니다.
    
    이러한 프로세스를 통해, 개발자 개인이 아니라 팀 전체가 안정적이고 빠르게 대응할 수 있는 협업 환경이 조성됩니다.
    
- **TDD, 왜 그렇게 많이 언급 되는건지? 왜 중요할까?**
    
    <aside>
    ❗
    
    개발해야되는 스코프가 점점 더 많아지고 거대한 규모의 소프트웨어가 많아짐에 따라, **유지보수 및 장애 발생시 대처를 유연하게 할 수 있는 방법론으로 다들 회귀**하기 시작했습니다. 
    
    </aside>
    
    즉, 코드의 규모는 점점 커지고 유저가 많아짐에 따라 **예측하기 힘든 행동 패턴들에 의한 장애가 발생**하기 시작한 거죠. 소위 새로운 기능을 만들기 위해 “요구사항을 찍어낸다” 식의 단순 재래식 개발로는 소프트웨어의 품질을 지속적으로 유지하고 향상시킬 수 없었기에 테스트 자동화에 대한 중요성은 점점 대두되어 왔습니다. 
    
    요구사항이 변경되었을 때, 기존의 기능이 영향이 없는가? 등을 검증하기 위한 방법론들이 주목받지 못하고 있다가 **빠른 변화에도 유연하게 새로운 기능을 적용하고 변경할 수 있는 기반을 다질 수 있는 TDD 에 대한 중요성이 더욱 더 중요**해지고 있습니다.
    
- **AI 시대에도 TDD는 왜 필요한가요?**
    
    최근 AI 도구(GitHub Copilot, TestGPT 등)는 테스트 코드 작성 시간을 크게 줄여주고 있습니다. 그러나 AI가 만든 테스트가 정확한지, 충분히 검증하는지는 여전히 개발자의 책임입니다.
    
    앞으로는 AI가 작성한 코드와 테스트를 **정확히 평가하고 관리할 수 있는 개발자의 기본기와 경험치가 더욱 중요**해집니다. AI를 활용하여 생산성을 높이되, 이를 감독할 수 있는 능력 역시 TDD 학습을 통해 기를 수 있습니다.
    
    또한, AI가 발전할수록 **TDD를 잘 작성해두면 AI가 코드를 더욱 정확히 인식하고 이해**할 수 있게 될 가능성도 있습니다. 
    

## What is TDD?

- **TDD 프로세스 흐름  (Red-Green-Refactor)**
    
    TDD는 일반적으로 다음과 같은 순환을 반복합니다.
    
    1. **Red** (테스트 먼저 작성, 실패 상태 확인)
    2. **Green** (최소한의 코드로 테스트 통과)
    3. **Refactor** (리팩토링을 통한 코드 개선)
    
    이러한 과정을 **Red-Green-Refactor 사이클**이라고 합니다.
    
    - **예제: 회원 가입 기능 개발하기**
        - **🔴 Red 단계 - 실패하는 테스트 작성**
            
            이 단계에서는 registerUser 메서드가 없으므로 당연히 **테스트가 실패**합니다.
            
            ```java
            @Test
            @DisplayName("회원 가입 시 유효한 ID가 반환된다")
            void registerUser_ReturnsValidId() {
                // given
                String email = "test@example.com";
                String password = "password123";
            
                // when
                long userId = userService.registerUser(email, password);
            
                // then
                assertThat(userId).isGreaterThan(0L);
            }
            ```
            
        - **🟢 Green 단계 - 최소한의 코드 구현으로 통과**
            
            테스트를 통과시키기 위해 간단한 회원 가입 로직을 작성합니다.
            
            **테스트는 성공(Green)** 상태가 됩니다.
            
            ```java
            @Service
            public class UserService {
                private final UserRepository repository;
            
                public UserService(UserRepository repository) {
                    this.repository = repository;
                }
            
                public long registerUser(String email, String password) {
                    User user = new User(email, password);
                    repository.save(user);
                    return user.getId();
                }
            }
            
            @Entity
            public class User {
                @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
                private Long id;
                private String email;
                private String password;
            
                protected User() {}
            
                public User(String email, String password) {
                    this.email = email;
                    this.password = password;
                }
                // getters, setters 생략
            }
            ```
            
        - **🔵 Refactor 단계 - 코드 개선하기**
            - **도메인 객체로 로직 이동 및 캡슐화**
            
            ```java
            @Entity
            public class User {
                @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
                private Long id;
                private String email;
                private String password;
            
                protected User() {}
            
                public static User register(String email, String password) {
                    validate(email, password);
                    return new User(email, password);
                }
            
                private User(String email, String password) {
                    this.email = email;
                    this.password = password;
                }
            
                private static void validate(String email, String password) {
                    if (email == null || email.isBlank()) 
                        throw new IllegalArgumentException("이메일을 입력해주세요.");
                    if (password == null || password.length() < 8)
                        throw new IllegalArgumentException("비밀번호는 최소 8자 이상이어야 합니다.");
                }
                // getters, setters 생략
            }
            ```
            
            - 서비스 계층 간결화
            
            ```java
            @Service
            public class UserService {
                private final UserRepository repository;
            
                public UserService(UserRepository repository) {
                    this.repository = repository;
                }
            
                public long registerUser(String email, String password) {
                    User user = User.register(email, password);
                    return repository.save(user).getId();
                }
            }
            ```
            
    - **예시 코드로 살펴본 TDD의 핵심 원칙들**
        
        우리는 예제처럼 다음의 원칙들을 지키는 것이 중요합니다.
        
        - **실패 테스트 우선 작성 (Red 단계)**
            - 항상 성공할 수 없는 테스트를 먼저 작성하여 요구사항을 명확히 정의하고, 구현 방향을 설정합니다.
        - **작고 명확한 테스트 작성**
            - 하나의 요구사항에 대해 하나의 테스트만 작성하도록 합니다. 각 테스트가 작고 명확한 목적을 갖추도록 유지하여, 빠르고 명확한 피드백을 얻습니다.
        - **명확한 책임 분리와 느슨한 결합(Loose Coupling)**
            - 각 객체가 자신의 책임만 명확히 수행할 수 있도록 역할을 분리합니다. 특히 회원 가입 예시에서처럼, 유효성 검증 책임을 서비스가 아닌 도메인 객체(User) 내부에 배치하여, 서비스와 도메인 객체 사이의 느슨한 결합(Loose Coupling) 구조를 구현합니다. 객체 간 의존성을 낮춰 변경에 유연하게 대응할 수 있습니다.
        - **단위 테스트 우선**
            - 외부 시스템(DB, API 등)에 대한 직접적인 의존을 최소화하고 Mockito 등의 Mock 객체를 적극 활용하여 빠르고 독립적인 단위 테스트(Unit Test)를 작성합니다. 이를 통해 개발자는 코드 변경 후 즉시 피드백을 얻을 수 있고, 문제 발생 시 빠르게 대응 가능합니다.
        - **검증 → 실행 → 저장 → 반환의 명확한 흐름 준수**
            - 예시의 회원 가입 기능처럼 Parameter 유효성 검증 → 도메인 객체 생성 → DB 저장 → 결과 반환의 명확한 흐름을 유지합니다. 이 명확한 흐름은 요구사항과 기능의 분리라는 TDD의 원칙을 잘 표현합니다.
        - **지속적 리팩토링**
            - 테스트 통과 이후에도 지속적으로 코드 리팩토링을 수행하여 설계를 개선하고 코드의 유지보수성을 높입니다. 리팩토링 과정에서도 테스트를 통해 기능의 정상 동작 여부를 항상 점검해야 합니다.
- **TDD 의 장단점**
    - 장점
        - 코드 품질 향상 : 	코드에 대한 자신감 증가, 결함 감소
        - 유지 보수성 증가 : 변경사항 적용 시 기존 코드에 미치는 영향 파악 용이
        - 명확한 설계 : 설계가 명확하고 간단해지며 중복 최소화
        - 빠른 피드백 : 개발 초기에 문제를 발견하고 수정
    - 단점
        - 초기 개발 시간 소모 증가 가능성
        - 테스트 코드 관리 부담이 증가할 수 있음
        - 경험이 부족한 개발자에게 학습 곡선 존재
- **TDD를 위한 권장사항**
    - 테스트는 **작고 명확하게** 유지한다.
    - 테스트는 빠르게 실행 가능하도록 작성한다.
    - 통합 테스트보다 **단위 테스트**를 우선시한다.
    - 테스트 이름은 명확하고 의미 있게 짓는다.
    - **항상 실패하는 테스트부터 작성한다.**
- **테스트 더블(Test Double) 소개**
    
    테스트 더블(Test Double)은 **실제 객체를 대신하여 테스트를 위해 사용되는 대역 객체**입니다.
    
    이 객체들은 외부 시스템(DB, API 등)에 의존하지 않고 독립적인 테스트 환경을 구성하기 위해 사용됩니다.
    
    실제 객체를 사용하면 외부 시스템과의 결합이 높아져 테스트가 느려지고 복잡해지지만, Test Double을 활용하면 외부 시스템에 대한 의존을 효과적으로 제어할 수 있으며, 테스트의 신뢰성과 속도를 크게 높일 수 있습니다.
    
    - **Test Double의 두 가지 유형: Mock과 Stub**
        
        
        | **구분** | **Mock** | **Stub** |
        | --- | --- | --- |
        | **역할** | 특정 함수가 호출되었는지, 정해진 행동을 수행했는지 검증 | 미리 준비된 응답을 제공하여 특정 상태를 유지 |
        | **초점** | 객체의 **행동(Behavior)** | 객체의 **상태(State)** |
        | **사용 예시** | 특정 메서드 호출 여부 검증(ex. 호출 횟수, 파라미터 검증) | 미리 정해둔 데이터를 반환하여 테스트 수행 |
    - 예시
        
        아래의 예시처럼 Mock은 “메소드가 호출되었는지” 같은 행동 검증을, Stub은 “메소드가 호출되었을 때 정해진 데이터를 반환하는지” 상태를 미리 정의해 테스트할 때 사용합니다.
        
        - **Mock 예시 코드 (Mockito)**
            
            ```java
            @Test
            void userRepository_save_호출검증() {
                // given
                UserRepository mockRepository = mock(UserRepository.class);
                UserService userService = new UserService(mockRepository);
                User user = new User("test@example.com", "password123");
            
                // when
                userService.registerUser(user.getEmail(), user.getPassword());
            
                // then (저장 메서드가 정확히 1회 호출됐는지 검증)
                verify(mockRepository, times(1)).save(any(User.class));
            }
            ```
            
        - **Stub 예시 코드 (Mockito)**
            
            ```java
            @Test
            void userRepository_stub_응답제공() {
                // given
                UserRepository stubRepository = mock(UserRepository.class);
                UserService userService = new UserService(stubRepository);
                User user = new User("test@example.com", "password123");
            
                when(stubRepository.save(any(User.class))).thenReturn(new User(1L, user.getEmail(), user.getPassword()));
            
                // when
                long id = userService.registerUser(user.getEmail(), user.getPassword());
            
                // then
                assertThat(id).isEqualTo(1L);
            }
            ```
            
    - **Test Double 활용과 강결합 해소 전략**
        - 테스트 더블을 사용하여 외부 의존성을 제거하면 독립적이고 빠른 테스트가 가능합니다.
        - 외부 의존성이 강한 코드에서는 외부 환경에 따라 테스트 결과가 불안정하고 예측하기 어렵습니다.
        - **느슨한 결합을 통한 장점:**
            - 외부 시스템과의 결합도를 낮추면 코드가 유연해지고, 테스트가 빠르고 안정적이며 유지보수가 용이합니다.
        
        ```java
        /**
         * PaymentService는 PaymentGateway(외부 결제 서비스)에 직접적으로 의존합니다.
         * 이렇게 구현하면 실제 외부 시스템 상태(네트워크 문제, 서버 장애 등)에 따라 테스트가 영향을 받기 때문에,
         * 테스트가 불안정해지고 실행 시간이 길어질 수 있습니다.
         */
        @Service
        public class PaymentService {
            private final PaymentGateway paymentGateway;
        
            public PaymentService(PaymentGateway paymentGateway) {
                this.paymentGateway = paymentGateway;
            }
        
            public boolean processPayment(PaymentRequest request) {
                return paymentGateway.charge(request); // 실제 외부 시스템 호출 (강결합)
            }
        }
        
        /**
         * Mock을 사용한 예시입니다.
         *
         * Mock을 활용하여 외부 시스템 호출 여부(행동 검증)를 명확하게 확인할 수 있습니다.
         * 실제 외부 결제 서비스(PaymentGateway)에 접근하지 않고도 PaymentService가 
         * PaymentGateway의 charge 메서드를 정확히 1회 호출했는지 검증합니다.
         * 이를 통해 외부 시스템 상태에 의존하지 않는 독립적인 테스트가 가능합니다.
         */
        @Test
        void paymentService_mock을활용한결제요청호출검증() {
            // given: Mock 객체 생성 및 설정
            PaymentGateway mockGateway = mock(PaymentGateway.class);
            PaymentService service = new PaymentService(mockGateway);
            PaymentRequest request = new PaymentRequest(1000);
        
            // 외부 시스템(PaymentGateway)의 charge 메서드 호출 시 무조건 true를 반환하도록 설정
            when(mockGateway.charge(request)).thenReturn(true);
        
            // when: 서비스의 결제 처리 메서드 실행
            boolean result = service.processPayment(request);
        
            // then: 외부 시스템의 메서드 호출 여부와 결과 값 검증
            verify(mockGateway, times(1)).charge(request);  // 호출 횟수(행동) 검증
        }
        
        /**
         * Stub을 사용한 예시입니다.
         *
         * Stub은 특정 입력 값에 대한 반환 값을 미리 설정하여, 외부 시스템의 상태를 완벽하게 통제합니다.
         * 이 예시에서는 PaymentGateway가 '결제 실패(false)' 상태를 반환하도록 명시하여,
         * 결제가 실패하는 상황에서도 PaymentService가 올바르게 동작하는지 확인합니다.
         * 실제 PaymentGateway를 사용하지 않고도 원하는 상태 조건 하에서의 동작을 정확히 테스트할 수 있습니다.
         */
        @Test
        void paymentService_stub을활용한반환값검증() {
            // given: Stub 객체 생성 및 상태(반환값) 설정
            PaymentGateway stubGateway = mock(PaymentGateway.class);
            PaymentService service = new PaymentService(stubGateway);
            PaymentRequest request = new PaymentRequest(1000);
        
            // Stub 설정: PaymentGateway의 charge 메서드가 특정 요청에 대해 false(실패)를 반환하도록 명시
            when(stubGateway.charge(request)).thenReturn(false);  // 결제 실패 상황 가정
        
            // when: 서비스의 결제 처리 메서드 실행
            boolean result = service.processPayment(request);
        
            // then: 반환된 결과가 미리 정의한 상태와 일치하는지 확인
            assertThat(result).isFalse();  // 명시적으로 설정한 반환 값(상태) 검증
        }
        ```
        

## **Test Pyramid & Test Types**

<aside>
💡 Testable한 코드와 다양한 테스트 코드의 종류를 정의하고, 각각의 상황에서 어떤 테스트를 작성해야 하는지 함께 알아봅시다

</aside>

- **Test Pyramid (테스트 피라미드)**
    
    테스트 피라미드는 테스트의 종류와 중요성, 비용과 효율성의 관계를 나타낸 개념입니다.
    
    ![Untitled.png](Step%2001%20TDD/Untitled.png)
    
    - **피라미드 하단으로 갈수록 테스트 작성 비용이 낮고, 실행 속도가 빠릅니다.**
    - **피라미드 상단으로 갈수록 통합 수준이 높아지고, 테스트 작성 비용 및 실행 속도가 느립니다.**
    - 가장 효율적인 테스트 전략은 피라미드의 아래쪽(단위 테스트)에 집중하고, 상단부(E2E 테스트)는 최소화하는 것입니다.
- **테스트 코드의 종류**
    - **Unit Testing (단위 테스트)**
        - **대상** : 단일 기능이나 함수, 클래스 단위의 작은 코드 블록
        - **목적** : 빠르게 독립적으로 코드의 정확한 동작 여부를 검증
        - **특징**
            - 다른 모듈이나 외부 시스템에 의존하지 않고 빠르게 실행됨
            - 변경사항이 기존 기능에 미치는 영향을 즉시 확인 가능
            - **작성시점** : 가장 자주 작성하며, 모든 개발자가 코드 작성과 동시에 작성해야 함
        - 강의에서는 다른 객체나 시스템에 의존하지 않고, 온전히 자신의 기능만 테스트하는 독립적인 테스트 라고 정의합니다.
        
    - **Integration Testing (통합 테스트)**
        - **대상** : 서로 다른 모듈 간의 상호작용 및 협력
        - **목적** : 여러 모듈이 통합될 때 예상한 대로 상호작용하는지 검증
        - **특징**
            - 복수의 모듈이 결합된 기능에 대한 검증
            - Mock 또는 실제 시스템을 사용하여 작성 가능
            - **작성시점** : 핵심 비즈니스 로직이 포함된 모듈이나 서비스 간의 상호작용이 중요한 지점에서 작성
        
    - **End-to-End Testing (E2E 테스트)**
        - **대상** : 실제 사용자의 전체 애플리케이션 흐름
        - **목적** : 최종 사용자의 관점에서 시스템 전체가 정상적으로 작동하는지 검증
        - **특징**
            - 실제 시스템 환경과 유사하게 작성
            - 비용이 크고, 실행 시간이 오래 걸리므로 최소화
            - **작성시점** : 배포 전 시스템이 실제 사용자의 관점에서 문제없이 작동하는지 검증할 필요가 있을 때 작성
- **상황별 테스트 코드 작성 전략**
    
    다음과 같은 기준으로 테스트의 종류를 선택하여 작성하면 효율적입니다.
    
    | **테스트 종류** | **작성 시점 및 권장 상황** | **비용** | **속도** | **신뢰성** |
    | --- | --- | --- | --- | --- |
    | **Unit Test** | 모든 개발 시 항상 작성, 가장 자주 활용 | 매우 낮음 | 빠름 | 매우 높음 |
    | **Integration Test** | 모듈 간 협력이 중요한 서비스 레벨에서 주기적으로 작성 | 중간 | 중간 | 높음 |
    | **E2E Test** | 릴리즈 전 최종 검증 단계에서 최소한의 필수 시나리오만 작성 | 높음 | 느림 | 중간 |
- **Testable Code 작성법과 주의사항**
    - 모든 코드를 테스트 가능하게 구현하는 것이 목표입니다.
    - 테스트가 성공하면 기능 구현이 완료된 것으로 판단합니다.
    - 테스트 커버리지 100%보다 **유의미한 테스트를 작성하는 것**에 집중하세요.
    - 테스트 불가능한 코드 (private 접근자, 강결합 코드)는 최소화합니다.
    - 기능에 대해 의미 있고 신뢰성 높은 테스트 케이스를 고민하고 작성하세요.

## 실무 테스트 코드 작성하기

<aside>
💡 지난 강의에서는 테스트코드의 종류에 대해 정리했습니다.
이번에는 각 테스트를 어떤 시점에 어떻게 작성하고 수행하는지 알아보도록 하겠습니다.

</aside>

### 실무에서는 어떤 테스트를 해야하나?

<aside>

- 기본적으로 유닛 테스트를 수행은 진행(권장)
    - 유닛 테스트는 개발자의 방어 수단!!
- 다만 실무에서는 각 팀, 프로젝트, 회사의 전략에 따라 통합테스트를 중점에 두거나 E2E테스트를 수행하지 않을수도 있음
- 만약 테스트 전략이 없다면 반드시 단위 테스트를 작성하는것을 권장
- 여기서 단위 테스트는 여러분이 알고 있는 통합테스트를 어느정도 커버하는 수준
</aside>

- **통합테스트도 두 종류가 있습니다!!**
    - **개발 레벨에서의 통합 테스트 (Integration Test)**
        - 애플리케이션 내부에서 **여러 컴포넌트(Controller, Service, Repository)**가 정상적으로 동작하는지 검증.
        - 코드 단위 → 모듈 간 상호작용 테스트
        - 보통 JUnit + @SpringBootTest, Testcontainers, H2 등 사용
        - 목적: **개발자 테스트 자동화** → 빠른 피드백, CI 단계에서 실행
    - **QA/배포 단계에서의 통합 테스트 (System Integration Test)**
        - **현업에서 흔히 말하는 “통합 테스트 환경”**
        - 여러 개발자가 만든 기능을 **통합 환경(Integration Environment)**에 배포하고 함께 테스트.
        - 특징:
            - DB, API Gateway, 외부 시스템 포함 → **실제 서비스에 가까운 환경**
            - 테스트 케이스: QA팀이 준비한 시나리오 기반 수동 테스트 + 일부 자동화
            - 목적: **서비스 전반의 연동 상태 확인** (특히 마이크로서비스 환경에서 중요)
        - 예시:
            - 회원 서비스, 결제 서비스, 상품 서비스 각각 개발 → 통합 환경에서 “결제 플로우” 전체 확인
- **개발 레벨에서의 통합 테스트**는 **단위 테스트와 가장 가까운 개념**
    - **단위 테스트**: 비즈니스 로직이 올바른지, 작은 단위로 빠르게 검증
    - **통합 테스트**: 실제 Bean 간 연동, DB 연동, 트랜잭션 등 **실제 환경에 가까운 상황**을 검증
        
        
        | **구분** | **단위 테스트** | **통합 테스트** |
        | --- | --- | --- |
        | **범위** | 메서드 / 클래스 단위 | Controller + Service + Repository |
        | **실행 속도** | 매우 빠름 | 중간 |
        | **외부 의존성** | Mock으로 대체 | 실제 Bean + H2 DB |
        | **목적** | 로직 검증 | 환경/구성 검증 |
- **개발 레벨 통합 테스트의 핵심**
    - **단위 테스트**와 달리, 실제로 **Repository ↔ DB ↔ Service ↔ Controller**의 연결을 검증해야 함.
    - Mock을 거의 쓰지 않고, **실제 의존성을 살려서 테스트**.
    - DB는 **인메모리 or 실제 DB** 두 방식 중 선택.
        - 빠른 테스트 → 인메모리 DB (Spring Boot: H2 / NestJS: SQLite)
        - 실제 환경과 동일 → 로컬 DB, Docker, Testcontainers

### 그러면 예를들어 신규 사원 생성 → 목록 조회 이거 두개가 하나의 테스트에 있다면?

<aside>

- **통합 테스트라는 용어를 쓸 수 있지만, 포커스가 조금다름**
- 중요한 건 “한 테스트 안에 여러 시나리오가 있느냐”가 아니라, **실제 의존성(빈, DB 등)이 함께 작동하느냐**
- 테스트를 하나에서 여러 단계로 연결한다고 통합 테스트가 되는 건 아님.
- 통합 테스트는 ‘진짜 의존성’이 함께 동작해야 함. **Mock으로 대체했다면 그건 단위 테스트**
</aside>

- 🤔 유닛테스트 범위를 어떻게 해야할까… 컨트롤러는 단위 테스트 대상?
    
    <aside>
    
    - 컨트롤러는 일반적으로 **비즈니스 로직을 담지 않고**, 요청 매핑 + HTTP 변환 + Service 호출 역할만 수행.
    - 이 역할은 **스프링 MVC 프레임워크**가 이미 충분히 검증된 영역임.
    - 이럴때는 필요할 수도…
        - **Controller에 비즈니스 로직이 섞여 있을 때**
            
            (권장하지 않는 설계지만, 레거시 코드에서 발생 가능)
            
        - **커스텀 Request/Response 변환 로직**이 있는 경우
            
            예: 복잡한 JSON 파싱, 헤더 기반 조건 처리 등.
            
    </aside>
    
- **E2E 테스트는 어떻게 하나?**
    - **가장 비용이 큰 테스트(운영 환경과 거의 동일)** → **핵심 사용자 플로우만 선택**
    - **CI/CD 파이프라인에서 마지막 단계**에 실행 (배포 전 품질 보증)
    - **UI 포함 여부에 따라 도구 달라짐**
        - UI 포함: **Cypress, Playwright, Selenium** (브라우저 자동화)
        - UI 제외(API 기반): **RestAssured, Postman, Newman**
    - **API 기반 E2E 조건**
        - 실제 애플리케이션 환경과 동일하게 **엔드포인트를 통해 상태 변화** 검증
        - 예: **회원가입 → 로그인 → 주문 → 마이페이지 조회**
    - **테스트 작성 시 주의**
        - 실행 속도 느리므로 **핵심 시나리오에만 집중**
        - 테스트가 깨지면 빌드 파이프라인 전체 지연 가능 → **소규모, 안정적 테스트 유지**
- 실무 테스트 운영 사례
    
    테스트 전략은 회사, 프로젝트 성격에 따라 달라집니다.
    
    다음은 실제 실무에서 테스트를 운영하는 사례입니다. 
    
    <aside>
    
    ### **Case 1.**
    
    - 단위 테스트 80% / 통합 테스트 20%
    - E2E 테스트는 진행하지 않음
    - 비즈니스 로직 검증 중심
    - 최대한 간결하게 **Mock / Fake** 활용
    </aside>
    
    <aside>
    
    ### **Case 2.**
    
    - 통합 테스트 대부분 기능에서 진행
    - E2E 테스트는 **중요 로직 위주**
    - **통합 테스트 → E2E 테스트** 순서로 작성
    </aside>
    
    <aside>
    
    ### **Case 3.**
    
    - E2E, 통합 테스트 위주 운영
    - 기능별 검증할 수 있도록 **JUnit를 활용하여 E2E 검증함**
    - 예를 들면, API Endpoint까지만 검증 후 주요 비즈니스 로직만 단위 테스트 작성
    - 나머지는 API Docs + 프론트 붙여서 테스트를 진행
    </aside>
    
    <aside>
    
    ### **Case 4.**
    
    - 유닛 테스트 코드를 작성하고 자동화를 통해 CI 환경에서 통과 테스트 자동 수행
    - QA/배포 단계에서의 통합 테스트(자동화 및 직접 프론트를 통한 사용자 테스트)
    - 자동화 툴을 사용한 E2E 테스트 수행(MSA API일 경우 수행)
    </aside>
    

### 지켜서 연습해야 하는 테스트 코드 규칙들

- **규칙 1. 한 테스트는 한 가지 책임만 검증**
    - **잘못된 예시**
        
        ```java
        @Test
        void testOrderProcess() {
            // 주문 생성
            Order order = new Order("user", "product-1", 2, new BigDecimal("50.00"), "SAVE10");
        
            // 재고 체크, 결제, 배송까지 한 번에 검증
            assertEquals(new BigDecimal("90.00"), orderService.placeOrder(order));
            assertEquals(OrderStatus.PAID, order.getStatus());
            assertEquals(0, inventoryRepo.getStock("product-1")); // 외부 동작까지
        }
        ```
        
    - **개선된 예시**
        
        ```java
        @Test
        void givenValidOrder_whenPlaceOrder_thenReturnsDiscountedTotal() {
            Order order = createOrder("user", "product-1", 2, new BigDecimal("50.00"), "SAVE10");
            BigDecimal total = orderService.placeOrder(order);
        
            assertEquals(new BigDecimal("90.00"), total);
        }
        ```
        
- **규칙 2. 외부 의존성(Mock) 처리**
    - **잘못된 예시**
        
        ```java
        @Test
        void testFindById() {
            Product product = new Product("p1", "Test Product");
            productRepository.save(product); // 실제 DB에 저장
        
            Product result = productRepository.findById("p1").orElseThrow();
            assertEquals("Test Product", result.getName());
        }
        ```
        
    - **개선된 예시**
        
        ```java
        @Test
        void testFindByIdWithMock() {
            ProductRepository mockRepo = mock(ProductRepository.class);
            when(mockRepo.findById("p1")).thenReturn(Optional.of(new Product("p1", "Test Product")));
        
            Product result = mockRepo.findById("p1").orElseThrow();
            assertEquals("Test Product", result.getName());
        }
        ```
        
- **규칙 3. 테스트는 재현 가능해야 함 (랜덤/시간 의존 제거)**
    - **잘못된 예시**
        
        ```java
        @Test
        void testRandomCoupon() {
            String coupon = "SAVE" + new Random().nextInt(100); // 실행마다 값 다름
            Order order = new Order("user", "product", 1, new BigDecimal("100.00"), coupon);
        
            BigDecimal total = orderService.placeOrder(order);
            assertTrue(total.compareTo(new BigDecimal("0")) > 0);
        }
        ```
        
    - **개선된 예시**
        
        ```java
        @Test
        void testFixedCoupon() {
            String coupon = "SAVE10"; // 항상 동일
            Order order = new Order("user", "product", 1, new BigDecimal("100.00"), coupon);
        
            BigDecimal total = orderService.placeOrder(order);
            assertEquals(new BigDecimal("90.00"), total);
        }
        ```
        
- **규칙 4. 명확한 테스트 이름 (Given-When-Then)**
    - **잘못된 예시**
        
        ```java
        @Test
        void test1() {
            // 의미 없는 이름
        }
        ```
        
    - **개선된 예시**
        
        ```java
        @Test
        void givenSufficientStock_whenPlaceOrder_thenInventoryDecreased() {
            // 조건 + 실행 + 기대 결과
        }
        ```
        
- **규칙 5. Assertion은 명확하게**
    - **잘못된 예시**
        
        ```java
        assertTrue(total.compareTo(BigDecimal.ZERO) > 0); // 의미 모호
        ```
        
    - **개선된 예시**
        
        ```java
        assertEquals(new BigDecimal("120.00"), total); // 기대 값 명확
        ```
        

### 피해야 하는 안티패턴

- **안티패턴 1. 테스트 간 상태 공유**
    - **나쁜 예시
     실행 순서에 따라 결과가 달라지고, 독립성 깨짐.**
        
        ```java
        static int sharedCount = 0;
        
        @Test
        void testA() {
            sharedCount++;
            assertTrue(sharedCount > 0);
        }
        
        @Test
        void testB() {
            // testA 실행 여부에 따라 결과 달라짐
            assertEquals(1, sharedCount);
        }
        ```
        
- **안티패턴 2. Sleep 사용**
    - **나쁜 예시
     테스트가 느리고 불안정.**
     → **해결책:** 비동기 처리 대기 시 Awaitility 라이브러리 등 사용.
        
        ```java
        @Test
        void testWithSleep() throws InterruptedException {
            service.startAsyncTask();
            Thread.sleep(2000); // 작업 끝나길 기다림
            assertEquals("done", service.getStatus());
        }
        ```
        
- **안티패턴 3. 의미 없는 이름**
    - **나쁜 예시**
        
        ```java
        @Test
        void testMethod1() {
            // 무슨 시나리오인지 알 수 없음
        }
        ```
        
- **안티패턴 4. 과도한 Mock 검증**
    - **나쁜 예시
    이 코드는 “어떤 메서드가 몇 번 호출됐는지”에 집착 → 결과보다 내부 구조를 테스트.
     행동이 아니라 ‘구현 방법’을 검증해야함**
        
        ```java
        @Test
        void givenValidOrder_whenPlaceOrder_thenVerifyEveryInternalCall() {
            when(inventoryRepo.getStock("product-1")).thenReturn(10);
            when(paymentGateway.charge(anyString(), any())).thenReturn(true);
        
            Order order = new Order("user1", "product-1", 2, new BigDecimal("50.00"), "SAVE10");
            orderService.placeOrder(order);
        
            // 내부 구현을 너무 상세히 검증
            verify(inventoryRepo, times(1)).getStock("product-1");
            verify(inventoryRepo, times(1)).decreaseStock("product-1", 2);
            verify(paymentGateway, times(1)).charge("4111111111111111", new BigDecimal("100.00"));
            verify(inventoryRepo, never()).delete(any());  // 이런 것도 굳이?
        }/
        ```
        
    - 개선 예시
        
        ```java
        @Test
        void givenValidOrder_whenPlaceOrder_thenReturnsCorrectTotal() {
            when(inventoryRepo.getStock("product-1")).thenReturn(10);
            when(paymentGateway.charge(anyString(), any())).thenReturn(true);
        
            Order order = new Order("user1", "product-1", 2, new BigDecimal("50.00"), "SAVE10");
        
            BigDecimal total = orderService.placeOrder(order);
        
            // 외부로 보이는 결과만 검증 (Behavior 중심)
            assertEquals(new BigDecimal("90.00"), total);
        }
        ```
        

## 과제

<aside>
🚩 **과제 : 이번 챕터 과제**

</aside>

### 💻 과제 첨부파일 다운로드

Spring 의 경우 Kotlin / Java 중 하나, Nest.js 의 경우 Typescript 로 작성합니다.
프로젝트에 첨부된 설정 파일은 수정하지 않도록 합니다.

<aside>
💾

**Java / Kotlin Spring 프로젝트**

[hhplus-tdd-jvm-java.zip](Step%2001%20TDD/hhplus-tdd-jvm-java.zip)

[hhplus-tdd-jvm-kotlin.zip](Step%2001%20TDD/hhplus-tdd-jvm.zip)

**Typescript Nest.js 프로젝트**

[hhplus-tdd-nest.zip](Step%2001%20TDD/hhplus-tdd-nest.zip)

</aside>

---

### `필수과제`

프로젝트 내의 주석에 작성된 내용을 참고하여 `/point` 패키지의 TODO 와 테스트코드를 작성해주세요.

**1. `/point` 패키지 (디렉토리) 내에 TODO 기본 기능 작성**

총 4가지 기본 기능 (포인트 조회, 포인트 충전/사용 내역 조회, 충전, 사용) 을 구현합니다.

- PATCH  `/point/{id}/charge` : 포인트를 충전한다.
- PATCH `/point/{id}/use` : 포인트를 사용한다.
- *GET `/point/{id}` : 포인트를 조회한다.*
- *GET `/point/{id}/histories` : 포인트 내역을 조회한다.*
- *잔고가 부족할 경우, 포인트 사용은 실패하여야 합니다.*

* `/database` 패키지의 구현체는 수정하지 않고, 이를 활용해 기능을 구현

**2. 각 기능에 대한 단위 테스트 작성**

- 테스트 케이스의 작성 및 작성 이유를 주석으로 작성하도록 합니다.
- 분산 환경은 고려하지 않습니다.

### `(선택)심화과제`

1. 동시성 제어 방식에 대한 분석 및 보고서 작성 ( **README.md** )
2. 동시에 여러 요청이 들어오더라도 순서대로 (혹은 한번에 하나의 요청씩만) 제어될 수 있도록 리팩토링
3. 동시성 제어에 대한 통합 테스트 작성

### `(공통) 과제 제출 방법`

1. 필수과제 / (선택)심화과제 를 진행한 뒤, 아래 PR템플릿에 맞추어 PR을 작성해주세요.
2. 작성한 PR URL 링크를 제출합니다.

**앞으로 모든 과제는 위 과제 제출 방법과 동일하게 진행해주세요.**

<aside>
📝

**PR 템플릿 세팅하고 작성하기!**

- Repo를 생성하고 `.github` 폴더를 생성 후 `pull_request_template.md` 파일을 만들어서 아래 템플릿을 복사/붙여넣기해주세요!
- PR 템플릿
    
    ```markdown
    ### **커밋 설명**
    <!-- 
    좋은 피드백을 받기 위해 가장 중요한 것은 커밋입니다.
    코드를 작성할 때 커밋을 작업 단위로 잘 쪼개주세요!
    
    예시)
    동시성 처리 : c83845
    동시성 테스트 코드 : d93ji3
    -->
    
    ### **과제 셀프 피드백**
    <!-- 예시
    - 과제에서 모호하거나 애매했던 부분
    - 과제에서 좋았던 부분
    -->
    
    ### 기술적 성장
    <!-- 예시
    - 새로 학습한 개념
    - 기존 지식의 재발견/심화
    - 구현 과정에서의 기술적 도전과 해결
    -->
    ```
    
</aside>

<aside>
<img src="https://www.notion.so/icons/light-bulb_red.svg" alt="https://www.notion.so/icons/light-bulb_red.svg" width="40px" />

**참고 PR 링크**

| 스택 | STEP1 |
| --- | --- |
| Java | https://github.com/Goddohi/hhplus-tdd-java/pull/1 |
| Java | https://github.com/juny0955/hhplus-tdd-java/pull/1 |
| Kotlin | https://github.com/yoon-chaejin/hhplus-tdd-jvm/pull/9 |
| Kotlin | https://github.com/psh10066/hhplus-tdd/pull/1 |
| TS | https://github.com/psyoongsc/hhplus-tdd-nest/pull/6 |
| TS | https://github.com/suji6707/nestjs-case-01-point-charge/pull/1 |
</aside>

<aside>
⚠️ **기초 학습 자료**

테스트 코드의 개념이 부족하거나 테스트 코드를 작성이 어려우신 분들은 기초자료를 참고하여 과제를 진행해주세요~!

‣ 

‣ 

‣ 

‣ 

*(참고자료) 학습 관련 링크*

- [JPA 에 대한 이해 ( 영상 자료 )](https://www.youtube.com/watch?v=WnYPdkNSLy8&themeRefresh=1)
- [JUnit 3일 안에 배우기](https://www.guru99.com/ko/junit-tutorial.html)
- [nestjs + jest 로 unit test 따라하기](https://www.tomray.dev/nestjs-unit-testing)

</aside>

<aside>
<img src="https://www.notion.so/icons/kind_blue.svg" alt="https://www.notion.so/icons/kind_blue.svg" width="40px" />

**토스 시니어 코치의 실무 AI 에이전트 활용 가이드**

[SuperClaude로 자동화 레벨 업 — 나만의 AI 팀 만들기.pdf](Step%2001%20TDD/SuperClaude%EB%A1%9C_%EC%9E%90%EB%8F%99%ED%99%94_%EB%A0%88%EB%B2%A8_%EC%97%85__%EB%82%98%EB%A7%8C%EC%9D%98_AI_%ED%8C%80_%EB%A7%8C%EB%93%A4%EA%B8%B0.pdf)

</aside>