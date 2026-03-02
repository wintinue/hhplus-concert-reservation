# HttpSession과 Session Clustering

<aside>
<img src="HttpSession%EA%B3%BC%20Session%20Clustering/SpartaIconScale9.png" alt="HttpSession%EA%B3%BC%20Session%20Clustering/SpartaIconScale9.png" width="40px" /> **목표**

- Session Clustering에 대해서 알아본다.
- HttpSession의 동작에 대해 알아본다.
</aside>

<aside>
<img src="%EB%A0%88%EB%94%94%EC%8A%A4%20%ED%83%80%EC%9E%85%20%EC%82%B4%ED%8E%B4%EB%B3%B4%EA%B8%B0/SpartaIconS24.png" alt="%EB%A0%88%EB%94%94%EC%8A%A4%20%ED%83%80%EC%9E%85%20%EC%82%B4%ED%8E%B4%EB%B3%B4%EA%B8%B0/SpartaIconS24.png" width="40px" /> **목차**

</aside>

<aside>
<img src="%EC%9D%B8%EB%A9%94%EB%AA%A8%EB%A6%AC%20%EC%A0%80%EC%9E%A5%EC%86%8C%EC%99%80%20Redis%20%EC%86%8C%EA%B0%9C/scc%25E1%2584%258F%25E1%2585%25A2%25E1%2584%2585%25E1%2585%25B5%25E1%2586%25A8%25E1%2584%2590%25E1%2585%25A5_%25E1%2584%258B%25E1%2585%25A1%25E1%2584%2592%25E1%2585%25A1_280x280.png" alt="%EC%9D%B8%EB%A9%94%EB%AA%A8%EB%A6%AC%20%EC%A0%80%EC%9E%A5%EC%86%8C%EC%99%80%20Redis%20%EC%86%8C%EA%B0%9C/scc%25E1%2584%258F%25E1%2585%25A2%25E1%2584%2585%25E1%2585%25B5%25E1%2586%25A8%25E1%2584%2590%25E1%2585%25A5_%25E1%2584%258B%25E1%2585%25A1%25E1%2584%2592%25E1%2585%25A1_280x280.png" width="40px" /> **필수 프로그램 설치**

없음

</aside>

<aside>
<img src="https://www.notion.so/icons/code_red.svg" alt="https://www.notion.so/icons/code_red.svg" width="40px" /> 모든 토글을 열고 닫는 단축키
Windows : `Ctrl` + `alt` + `t` 
Mac : `⌘` + `⌥` + `t`

</aside>

## HttpSession

HTTP 요청에는 상태가 없습니다. 이 말은 각각의 요청이 독립적으로 이뤄지며, 서버는 사용자가 보낸 몇번째 요청인지에 대한 정보 같은걸 저장되지 않는다는 의미입니다. 다른 방향에서 해석하면, 사용자 브라우저 측에서 자신을 식별할 수 있는 정보를 서버에 요청할 때 마다 알려줘야 한다는 의미입니다.

이때 사용자의 브라우저에 응답을 보내면서, 브라우저에 특정 데이터를 저장하도록 전달할 수 있습니다. 이때 서버에서 작성해서 응답을 받은 브라우저가 저장하는 데이터를 쿠키라고 부릅니다.

![Untitled](HttpSession%EA%B3%BC%20Session%20Clustering/Untitled.png)

저희는 응답을 보내면서 쿠키에 요청을 보낸 브라우저를 특정지을 값을 보내줄 수 있습니다. 이후 사용자가 요청을 보낼때, 해당 값을 보내주면 서버에서 저장 해놓은 정보를 기반으로 브라우저에 로그인한 사용자가 누구인지를 구분할 수 있습니다. 이런식으로 상태를 저장하지 않는 HTTP 통신을 사용하면서, 이전에 요청을 보낸 사용자를 기억하는 상태를 유지하는 것을 세션이라고 부릅니다.

### HttpSession

[redis-sessions.zip](HttpSession%EA%B3%BC%20Session%20Clustering/redis-sessions.zip)

Spring Boot를 사용하면, Spring Boot 프로젝트에 내장되어 있는 Tomcat 서버가 세션을 생성합니다. 이때, 이 세션을 확인하고 싶다면, Chrome의 개발자 모드로 들어가, Application > Storage > Cookie를 확인해볼 수 있습니다.

![Untitled](HttpSession%EA%B3%BC%20Session%20Clustering/Untitled%201.png)

이 JSESSIONID라고 하는 쿠키값은, 서버 내부의 Tomcat이 처음 접근한 브라우저에게 발급하는 쿠키입니다. Tomcat은 이 쿠키의 값을 바탕으로 세션을 관리하고, `HttpSession` 객체로 만들어 줍니다. 그리고 Spring Boot의 컨트롤러 메서드에서 해당 `HttpSession`을 가져와 사용할 수 있습니다.

아래 컨트롤러를 이용해 확인해 보겠습니다.

- **SessionController.java**
    
    ```java
    @RestController
    public class SessionController {
        @GetMapping("/set")
        public String set(
                @RequestParam("q")
                String q,
                HttpSession session
        ) {
            session.setAttribute("q", q);
            return "Saved: " + q;
        }
    
        @GetMapping("/get")
        public String get(
                HttpSession session
        ) {
            return String.valueOf(session.getAttribute("q"));
        }
    }
    
    ```
    
1. [`http://localhost:8080/set?q=password`](http://localhost:8080/set?q=password)으로 이동하면, `HttpSession`에 `password`가 저장됩니다. 브라우저에서 JSESSIONID를 확인할 수 있습니다.
2. [`http://localhost:8080/get`](http://localhost:8080/get으로)으로 이동하면, 저장해둔 `password`가 조회됩니다.

만약 이 과정에서 JSESSIONID를 제거하고 `/get`으로 이동하면, Tomcat이 연결된 세션이 없다고 판단, 세션에서 데이터를 찾지 못하고 오류가 발생하게 됩니다.

## 문제 인식

<aside>
❗ 그런데 만약, 사용자가 늘어서 하나의 서버로는 사용자의 요청을 감당하기 어려워지면 어떡할까요? 한가지 대안은 똑같은 기능을 하는 Spring Boot 서버를 여러개 사용하는 것입니다. 여러 서버가 동작한다면, 각 서버들에게 요청을 분산하여 부하를 줄일 수 있습니다(Load Balancing). 이런 방식의 확장을 Scale-Out이라고 부릅니다.

<aside>
❗ 반대로 사용하는 서버 자원의 크기 자체를 높이는 방식의 확장을 Scale-Up이라고 부릅니다.

</aside>

![Untitled](HttpSession%EA%B3%BC%20Session%20Clustering/Untitled%202.png)

이런 상황에서 사용자가 A 서버로 요청을 했다가, B 서버로 요청을 하게 된다면 세션이 유지될 수 있을까요??

</aside>

직접 확인해 봅시다!

- **Intellij 설정으로 포트 바꿔서 여러 서버 올리기**
    
    실행 버튼 옆에 실행할 설정을 클릭하면,
    
    ![Untitled](HttpSession%EA%B3%BC%20Session%20Clustering/Untitled%203.png)
    
    Edit Configurations를 찾을 수 있습니다.
    
    ![Untitled](HttpSession%EA%B3%BC%20Session%20Clustering/Untitled%204.png)
    
    구분하기 쉽게 이 설정의 이름을 바꾸고, 복제 버튼을 누릅니다.
    
    ![Untitled](HttpSession%EA%B3%BC%20Session%20Clustering/Untitled%205.png)
    
    그럼 두번째 설정이 나오게 되는데, 이름을 8081로 바꿉니다.
    
    ![Untitled](HttpSession%EA%B3%BC%20Session%20Clustering/Untitled%206.png)
    
    그다음 오른쪽의 Modify options를 누르고, Add VM options를 선택합니다.
    
    ![Untitled](HttpSession%EA%B3%BC%20Session%20Clustering/Untitled%207.png)
    
    ![Untitled](HttpSession%EA%B3%BC%20Session%20Clustering/Untitled%208.png)
    
    그럼 JVM에 전달할 설정을 정할 수 있는데, 여기에 `-Dserver.port=8081`를 작성하고, Apply → OK
    
    ![Untitled](HttpSession%EA%B3%BC%20Session%20Clustering/Untitled%209.png)
    
    그럼 서로다른 포트에 두개의 Spring Boot 프로젝트를 실행할 수 있습니다!
    
    ![Untitled](HttpSession%EA%B3%BC%20Session%20Clustering/Untitled%2010.png)
    
    ![Untitled](HttpSession%EA%B3%BC%20Session%20Clustering/Untitled%2011.png)
    
- **실험 화면**
    
    먼저 `8080`의 `/set?q=password`로 이동하고,
    
    ![Untitled](HttpSession%EA%B3%BC%20Session%20Clustering/Untitled%2012.png)
    
    `8080`의 `/get`으로 이동해서 결과를 확인합니다.
    
    ![Untitled](HttpSession%EA%B3%BC%20Session%20Clustering/Untitled%2013.png)
    
    저장은 잘 되었다는걸 확인했으니, `8081`의 `/get`으로 이동해 봅시다.
    
    ![Untitled](HttpSession%EA%B3%BC%20Session%20Clustering/Untitled%2014.png)
    
    Spring Boot의 기본 에러 페이지를 볼 수 있습니다…
    

포트 번호를 바꿔가면서 세션에 정보를 저장, 회수를 진행해 보면, 회수를 다른 포트의 서버에서 하려고 할때 데이터를 정상적으로 찾을 수 없습니다.

결국 여러 서버를 가동하게 되면, 세션의 정보를 서버 내부에서 관리하기 어려워집니다. 이에 따라 적당한 대안을 마련해야 합니다.

### Sticky Session

한가지 방법은 특정 사람이 보낸 요청을 하나의 서버로 고정하는 방법입니다. 이를 Sticky Session이라고 합니다.

요청을 분산하는 로드밸런서를 통해 요청을 보낸 사용자를 기록, 해당 사용자가 다시 요청을 할 경우 최초로 요청이 전달된 서버로 요청을 전달하는 방식입니다.

![Untitled](HttpSession%EA%B3%BC%20Session%20Clustering/Untitled%2015.png)

가능한 이야기이지만, 이 경우 약간의 문제가 생길 수 있습니다. 특정 서버로 보내진 사용자만 활발하게 활동한다면, 요청이 균등하게 분산되지 않는다는 점입니다.

9명의 사용자가 동일한 요청을 보내서 3대의 서버로 요청을 분산한다고 가정해 봅시다. 서버는 이들을 위해 세션을 발급합니다.

![Untitled](HttpSession%EA%B3%BC%20Session%20Clustering/Untitled%2016.png)

근데 이중 A, C 서버로 요청이 연결된 사용자들은 서비스에 관심이 떨어져 금방 떠난 반면, B 서버의 사용자들은 활발하게 서비스를 사용한다고 가정하면?

![Untitled](HttpSession%EA%B3%BC%20Session%20Clustering/Untitled%2017.png)

결과적으로는 다른 널널한 서버들에 비해 B 서버가 더 바쁘게 동작하게 되며, 이는 과부하로 이어질 수 있습니다. 여기에 만약 B 서버가 다운되게 된다면, 해당 서버에서 관리하는 세션도 다 사라지고, 사용자는 갑자기 자기 데이터가 사라지게 될 수 있습니다.

### Session Clustering

그래서 떠오르는 대안이 Session Clustering 입니다. 이는 여러 서버들이 하나의 저장소를 공유하고, 해당 저장소에 세션에 대한 정보를 저장함으로서, 요청이 어느 서버로 전달이 되든 세션 정보가 유지될 수 있도록 하는 방법입니다.

아무 사용자나 저희 서버(의 로드밸런서)로 요청을 보낸다고 가정해 봅니다. 그러면 저희 서버는 세션을 생성하되, 이 세션에 연결된 정보를 내부에 저장하는게 아닌 외부의 저장소에 저장을 하는 겁니다.

![Untitled](HttpSession%EA%B3%BC%20Session%20Clustering/Untitled%2018.png)

그러면 이제 해당 사용자가 다시 요청을 할 경우, 다른 서버로 요청이 보내진다고 하더라도, 세션 정보 자체는 외부 저장소에 저장되어 있기 때문에 확인이 가능합니다.

![Untitled](HttpSession%EA%B3%BC%20Session%20Clustering/Untitled%2019.png)

여기에 더해 외부 저장소를 사용하기 때문에, 사용자 요청을 처리하는 서버의 자유로운 추가 제거도 가능해 집니다. 중간에 서버를 제거하더라도 세션이 사라지지 않으며, 사용자가 늘어나면 그에 맞춰서 서버를 증가시키는 유연한 대처가 가능해 집니다.

![Untitled](HttpSession%EA%B3%BC%20Session%20Clustering/Untitled%2020.png)

다만, 이렇게 될 경우 서버 외부에 세션을 저장하는 것이므로, 관리 포인트가 늘어나게 되며, 통신 과정에서 어쩔 수 없는 지연이 발생합니다. 그래서 지연이 적은 Redis와 같은 인메모리 데이터베이스가 많이 사용됩니다.

간단하게 정리해보면 다음과 같습니다.

|  | Sticky Session | Session Clustering |
| --- | --- | --- |
| 장점 | 애플리케이션 입장에선 구현이 쉬움
외부와 통신할 필요가 없음 | Load Balancer에서 균등하게 요청 분산 가능
서버의 추가 제거가 비교적 자유로움 |
| 단점 | 요청이 분산되지 않아 과부하 가능성 존재
한 서버가 다운되면 그 서버가 관리하는 세션도 삭제 | 외부 저장소라는 관리 포인트 추가
외부와 통신하는 지연이 발생 |

### Spring Session

Spring Boot와 Redis를 함께 사용하고 있다면, 매우 쉽게 Session Clustering을 적용할 수 있습니다. `build.gradle`에 의존성을 추가해 줍니다.

- **build.gradle**
    
    ```groovy
    dependencies {
    	implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    	implementation 'org.springframework.session:spring-session-data-redis'  // 추가!
    	implementation 'org.springframework.boot:spring-boot-starter-web'
    	compileOnly 'org.projectlombok:lombok'
    	annotationProcessor 'org.projectlombok:lombok'
    	testImplementation 'org.springframework.boot:spring-boot-starter-test'
    	testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
    }
    ```
    

Spring Session은 Spring의 하위 프로젝트 중 하나로, 사용자의 세션 정보를 다루는데 유용한 API를 제공합니다. 이중 지금 추가한 Spring Session Data Redis를 추가하면, 내장 Tomcat의 세션 기능을 사용하지 않고 Redis에 별도로 세션을 저장하게 됩니다.

추가한 다음 동일한 실험을 다시 해보면,

- **양쪽 모두 동작합니다.**
    
    ![Untitled](HttpSession%EA%B3%BC%20Session%20Clustering/Untitled%2021.png)
    
    ![Untitled](HttpSession%EA%B3%BC%20Session%20Clustering/Untitled%2022.png)
    

또한 더이상 Tomcat을 사용하지 않기 때문에 JSESSIONID 대신 SESSION이라는 새로운 쿠키를 사용하고 있는것을 확인할 수 있습니다!

![Untitled](HttpSession%EA%B3%BC%20Session%20Clustering/Untitled%2023.png)

Redis를 확인해보면, 세션이 저장된 모습을 볼 수 있습니다.

![Untitled](HttpSession%EA%B3%BC%20Session%20Clustering/Untitled%2024.png)

단, 지금은 Java 기반 직렬화가 적용되고 있습니다. 만약 `RedisTemplate`을 사용할때 처럼 JSON을 비롯한 방식으로 직렬화를 하고 싶다면, `springSessionDefaultRedisSerializer` Bean을 등록해주면 됩니다.

- **springSesssionDefaultRedisSerializer()**
    
    ```java
    @Configuration
    public class RedisConfig {
        // ...
        @Bean
        public RedisSerializer<Object> springSessionDefaultRedisSerializer() {
            return RedisSerializer.json();
        }
    }
    
    ```
    

![Untitled](HttpSession%EA%B3%BC%20Session%20Clustering/Untitled%2025.png)

이럴 경우 읽을 수 있는 형태로 Redis에 저장되기는 하지만, Spring Security의 일부인 `SecurityContext`와 같은 객체가 Redis에 저장될 경우, `SecurityContext`의 기본 생성자가 없기 때문에 오류가 발생할 수도 있습니다.

<aside>
⚠️

`SecurityContext` 객체의 직렬화, 역직렬화의 경우 본 강의의 범주를 벗어나기 때문에, 지금 단계에서는 해당 오류가 발생할 수 있다는 점만 염두에 두도록 하겠습니다. 차후 공부를 통해 직접 `RedisSerializer`를 구성하거나, 직렬화가 쉽게 되는 `SecurityContext`를 만드는 것을 연구해 보세요!

</aside>