# QA 정책

정책의 원본은 `docs/design/03_DESIGN.md`다. 이 디렉토리에는 구현 중 확정된 세부 규칙만 도메인별 파일로 남긴다.

| 주제 | 설계 문서 위치 |
|---|---|
| C1 리뷰 결정 15건 | 0-1 |
| 스프린트·작업·셀·자동 실행 상태 전이 | 6 |
| 관리자 인증(`/admin/me` 판정, 캐시) | 7 |
| 자원 격리, 증적 보존 30일 | 8 |
| 페르소나 데이터셋(`auto`/`manual` 세트) | 9 |
| 재시도 정책(작업 최초 1회 + 재시도 3회, 2→4→8분) | 10 |
| 화면 안내(`guide`) | 15 |
| Figma QA 페이지 해석(화면 ID, 상태, 경고) | 5-2, 가이드 3-3 → [`figma-qa-page.md`](figma-qa-page.md) |
| 외부 연동 결정값(재시도, 타임아웃, LLM 호출 제한) | 8, 10 → [`external-adapters.md`](external-adapters.md) |
| 셋업 작업(단계별 처리·실패, 라벨 변환, 분류, LLM 입력·출력, 스냅샷, 상태 동기화) | 5-2, 10 → [`setup-job.md`](setup-job.md) |
| 체크리스트(편집 가능 상태, 셀 잠금, 확정·종료 조건, 경고 위치, guide 표) | 5-1, 5-3, 5-5, 5-7, 15-3 → [`checklist.md`](checklist.md) |

## 작업 큐

- 최대 시도 횟수 `qa.job.max-attempts = 4`: 최초 1회 + 재시도 3회
- 재시도 대기: `qa.job.backoff-base`(2분) × 2^(시도 횟수 - 1) → 2분, 4분, 8분
- heartbeat 제한 `qa.job.heartbeat-timeout = 2m`. 넘기면 회수해 대기 상태로 되돌린다(시도 횟수가 남아 있을 때)
- 스프린트당 실행 중인 `SETUP`, `AUTO_RUN`은 각각 1개 (부분 유니크 인덱스 `ux_job_active_per_sprint`)
- 대기 중 취소는 즉시 `CANCELLED`, 실행 중 취소는 다음 단계 경계에서 `CANCELLED`
