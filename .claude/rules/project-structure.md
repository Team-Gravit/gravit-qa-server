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
├── sprint/              # 스프린트, 연결 상태, 준비 단계·단위 진행, 명세 로드·QA 준비 처리기
├── feature/             # 기능 명세 사본, 스프린트 대상 기능(기준 명세), 명세 페이지 해석
├── reflection/          # 수정 이력 반영 제안, 승인 시 Notion 반영
├── snapshot/            # 수집 스냅샷 (파일 저장, 행)
├── checklist/           # 체크 항목, 체크 셀, 셀 이력, 체크리스트 생성
├── designReview/        # 디자인 검수 항목, 이력, Figma 시안과 명세 비교
├── autoReview/          # 1차 판정: 저장소 코드 선택, 나눠 담기, 판정 (엔티티 없음, 결과는 checklist 셀)
├── fixReport/           # 파트별 수정사항 파일 (엔티티 없음, 읽기 전용, LLM 없음)
└── adapter/             # 외부 연동: figma, notion, github, openapi, llm
```

자동 실행(run), 증적(evidence), 리뷰 수집(review), 페르소나(persona) 패키지는 QA 흐름 재설계(D13)로 없앴다.

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
- `handler/` - 도메인별 `JobHandler` 구현 (예: `sprint/handler/SpecLoadJobHandler`)
- `infrastructure/` - 외부 연동, `service/port/` 구현
- `config/` - 도메인 전용 설정과 props

## job 도메인 예외

`job`은 조건부 원자 갱신(`UPDATE … WHERE status = 'RUNNING' AND locked_by = ?`)과 `SKIP LOCKED`가 핵심이라
JPA 엔티티 없이 `repository/custom/JobQueueRepository` + `JdbcTemplate`으로만 다룬다. 상태 변경을 JPA로 옮기지 마라.

## 작업 처리기 작성

- 새 작업 처리는 `JobHandler`를 구현한 빈으로 만든다. 유형당 처리기는 하나다
- 단계는 `JobType`에 정의된 이름으로 `context.step("NAME", ...)`을 호출한다. 단계 경계마다 취소 요청을 확인한다
- 재시도해도 소용없는 실패는 `NonRetryableJobException`을 던진다. 그 외 예외는 재시도 대상이다
- 처리기는 같은 입력으로 다시 실행해도 결과가 같아야 한다. 재시도·회수 때 처리기는 처음부터 다시 불리지만, 끝난 단계(`sprint_step`)와 끝난 단위(`prepare_unit`)는 건너뛴다(`SetupStepRunner`)
