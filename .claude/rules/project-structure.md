---
description: 프로젝트 디렉토리 구조. 새 파일을 생성하거나 패키지 위치를 결정할 때 참조
paths:
  - "src/main/java/**/*.java"
---

# 프로젝트 구조

```
src/main/java/gravit/qa/
├── global/              # 공통 설정, 예외, 어노테이션, 유틸(시간)
├── security/            # Spring Security 설정, /admin/me 판정 필터·캐시
│
├── job/                 # 작업 큐(등록·점유·heartbeat·재시도·회수), 작업 처리기 계약
├── sprint/              # 스프린트, 기준점 (C4)
├── feature/             # 기능 명세 사본 (C3)
├── snapshot/            # 수집 스냅샷 (C3)
├── checklist/           # 체크 항목, 체크 셀, 셀 이력 (C4)
├── run/                 # 자동 실행, 케이스 결과 (C7)
├── evidence/            # 증적 파일 (C7)
├── review/              # 코드 검수 리포트 수집 (C8)
├── persona/             # 페르소나 매니페스트, 토큰 발급 (C7)
└── adapter/             # 외부 연동: figma, notion, github, openapi, llm, gravitserver (C3)
```

괄호 안은 해당 패키지를 만드는 단계다. 아직 없는 패키지는 그 단계에서 만든다.

## 도메인 패키지 내부 구조

```
{domain}/
├── controller/
│   ├── {Domain}Controller.java
│   └── docs/
│       └── {Domain}ControllerDocs.java   # Swagger 문서 인터페이스
├── service/
│   ├── {Domain}Service.java              # 조회/변경 분리 시 {Domain}QueryService, {Domain}CommandService
│   └── port/                             # 인프라 구현을 가리는 인터페이스, 구현은 infrastructure/ (선택)
├── repository/
│   ├── {Domain}Repository.java           # JPA Repository 인터페이스
│   ├── custom/                           # 커스텀 조회 인터페이스 + JdbcTemplate Impl (선택)
│   └── sql/                              # custom/ Impl이 쓰는 SQL 상수 (선택)
├── domain/
│   └── {Domain}.java                     # Entity, enum
└── dto/
    ├── request/
    ├── response/
    └── internal/                         # 레이어 간 내부 전달용, 쿼리 프로젝션 (선택)
```

필요할 때만 추가하는 선택적 하위 패키지:

- `facade/` - 여러 도메인 Service를 조합할 때 (`@Facade`)
- `support/` - 도메인 보조 로직. `job/support/`에는 작업 실행기(`JobRunner`)와 처리기 계약(`JobHandler`, `JobContext`)을 둔다
- `scheduler/` - 스케줄
- `handler/` - 도메인별 `JobHandler` 구현 (예: `sprint/handler/SetupJobHandler`)
- `infrastructure/` - 외부 연동, `service/port/` 구현
- `config/` - 도메인 전용 설정과 props

## job 도메인 예외

`job`은 조건부 원자 갱신(`UPDATE … WHERE status = 'RUNNING' AND locked_by = ?`)과 `SKIP LOCKED`가 핵심이라
JPA 엔티티 없이 `repository/custom/JobQueueRepository` + `JdbcTemplate`으로만 다룬다. 상태 변경을 JPA로 옮기지 마라.

## 작업 처리기 작성

- 새 작업 처리는 `JobHandler`를 구현한 빈으로 만든다. 유형당 처리기는 하나다
- 단계는 `JobType`에 정의된 이름으로 `context.step("NAME", ...)`을 호출한다. 단계 경계마다 취소 요청을 확인한다
- 재시도해도 소용없는 실패는 `NonRetryableJobException`을 던진다. 그 외 예외는 재시도 대상이다
- 처리기는 같은 입력으로 다시 실행해도 결과가 같아야 한다(재시도·회수 시 처음부터 다시 실행된다)
