---
description: 테스트 코드 작성 규칙 (통합 테스트 중심)
paths:
  - "src/test/java/**"
---

# Test Convention

테스트는 통합 테스트(`@QaSpringBootTest`)로 작성한다. `@ExtendWith(MockitoExtension.class)` 기반 단위 테스트는 새로 만들지 않는다.

## 네이밍 & 설정

- 파일명: `{Class}IntegrationTest.java`
- 클래스 어노테이션: `@QaSpringBootTest`. 아래 구성이 함께 들어온다
  - PostgreSQL: 환경 변수 `QA_TEST_DB_URL`(+`QA_TEST_DB_USERNAME`, `QA_TEST_DB_PASSWORD`)이 있으면 그 DB, 없으면 Testcontainers (`PostgresTestConfig`)
  - 조작 가능한 시계 `TestClock` (`@Primary`, 기준 `2026-10-16T12:00+09:00`). `advance(Duration)`으로 시간을 진행한다
  - `/admin/me` 스텁 서버 `AdminMeStubServer` (`qa.auth.host`가 스텁을 가리킨다)
  - `DatabaseClearExtension`: 매 테스트 전에 public 스키마 TRUNCATE(시퀀스 초기화), 시계·스텁·인증 캐시 초기화
  - `test` 프로필: 스케줄러 비활성(`qa.worker.enabled=false`). 작업은 테스트에서 `JobRunner.runNext()`로 직접 실행한다
- 의존성 주입: `@Autowired`
- Controller 테스트: `@AutoConfigureMockMvc` + `MockMvc`. 인증은 `Authorization: Bearer …` 헤더를 넣고 스텁 응답으로 제어한다
- 검증 라이브러리는 AssertJ (`assertThat`, `assertSoftly`, `assertThatThrownBy`)
- 테스트 패키지는 `src/main/java`의 도메인 구조를 미러링하라

## 목(mock)과 스텁

- 외부 HTTP 연동은 JDK `HttpServer` 스텁으로 대체한다 (`AdminMeStubServer` 참고). 실제 외부 서비스를 호출하지 마라
- 작업 처리기 스텁은 별도 설정 클래스를 `@Import`로 등록한다(`@Nested`와 함께 쓰면 중첩 `@TestConfiguration`이 컨텍스트를 분리시킨다) (`JobQueueIntegrationTest` 참고)
- 실패 주입·호출 검증 용도에 한해 `@MockitoBean`, `@MockitoSpyBean`을 쓴다

## 순수 JUnit 테스트

Spring 의존 없이 입력만으로 결과가 정해지는 domain, util, support 로직은 순수 JUnit으로 작성해도 된다. 파일명은 `{Class}Test.java`로 한다.

## 메서드 작성

- 메서드명은 한글 서술형 (`성공하면_SUCCEEDED가_되고_단계가_DONE이_된다()`)
- `@Nested` + `@DisplayName`으로 그룹화 (`@DisplayName("작업을 취소할 때")`)
- 본문은 `// given` / `// when` / `// then` 주석으로 구간을 구분하라
  - 준비 단계가 없으면 `// given`을 생략한다
  - 실행과 검증이 한 문장이면 `// when & then`으로 합친다 (`assertThatThrownBy`)

## 예외 검증

`RestApiException`을 던지는 예외 케이스는 반드시 `errorCode`까지 검증하라.

```java
import static gravit.qa.global.exception.domain.CustomErrorCode.JOB_NOT_FOUND;

assertThatThrownBy(() -> jobCommandService.requestCancel(999L))
        .isInstanceOf(RestApiException.class)
        .extracting(e -> ((RestApiException) e).getErrorCode())
        .isEqualTo(JOB_NOT_FOUND);
```

## Fixture

| 항목 | 규칙 |
|---|---|
| 위치 | `src/test/java/gravit/qa/{domain}/fixture/` |
| 메서드명 | 한글로 시나리오 표현 (`SprintFixture.준비_전_스프린트()`) |
| static 팩토리 fixture | 저장하지 않은 Entity나 DTO를 반환하는 `{Domain}Fixture` 클래스 |
| 저장형 fixture | `@TestComponent` + `@RequiredArgsConstructor`로 Repository를 주입받아 저장까지 하는 fixture |
