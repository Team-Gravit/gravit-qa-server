# QA 정책

정책의 원본은 `docs/design/03_DESIGN.md`이고, 2026-11 QA 흐름 재설계(D1~D14)로 바뀐 부분은 이 디렉토리의 문서가 원본이다. 구현 중 확정된 세부 규칙을 도메인별 파일로 남긴다.

| 주제 | 문서 |
|---|---|
| QA 흐름 재설계(2026-11): 상태 전이, 작업과 단계, 다시 시작, 연결, 명세 수집, LLM 입력 상한, 스냅샷 | [`prepare-job.md`](prepare-job.md) |
| 수정 이력 반영(제안, 승인, 거절, Notion 쓰기 허용 범위) | [`reflection.md`](reflection.md) |
| 체크리스트 생성(D7 묶음, D8 분류), 1차·2차 조회, 편집, 셀, 진행도, 종료 | [`checklist.md`](checklist.md) |
| 디자인 검수(Figma 레이어와 명세 C행 비교) | [`design-review.md`](design-review.md) |
| 1차 자동화 검수(코드 고르기, 나눠 담기, 판정 합치기) | [`auto-review.md`](auto-review.md) |
| 수정사항 파일(파트별 TXT, 내려받기 조건, 틀, 사유·근거 고르기), 사람이 Fail로 바꿀 때 남기는 사유 | [`fix-report.md`](fix-report.md) |
| Figma QA 페이지 해석(화면 ID, 상태, 경고) | 가이드 3-3 → [`figma-qa-page.md`](figma-qa-page.md) |
| 외부 연동 결정값(재시도, 타임아웃, LLM 호출 제한, 쓰기 범위) | 설계 8, 10 → [`external-adapters.md`](external-adapters.md) |
| 관리자 인증(`/admin/me` 판정, 캐시) | 설계 7 |

- 설계 `03_DESIGN.md`의 셋업 작업(5-2), 분류 확정(5-3), 자동 실행·증적·리뷰 수집(5-4~5-6), 페르소나(9), 화면 안내 guide(15)는 위 문서로 바뀌었다(D13)

## 작업 큐

- 최대 시도 횟수 `qa.job.max-attempts = 4`: 최초 1회 + 재시도 3회
- 재시도 대기: `qa.job.backoff-base`(2분) × 2^(시도 횟수 - 1) → 2분, 4분, 8분
- heartbeat 제한 `qa.job.heartbeat-timeout = 2m`. 넘기면 회수해 대기 상태로 되돌린다(시도 횟수가 남아 있을 때)
- 작업 종류는 `SPEC_LOAD`, `QA_PREPARE` 두 가지이고 둘 다 api 프로필 워커가 소비한다. 스프린트당 실행 중인 작업은 종류마다 1개(`ux_job_active_per_sprint`)
- 대기 중 취소는 즉시 `CANCELLED`, 실행 중 취소는 다음 단계 경계 또는 다음 단위 경계에서 `CANCELLED`
