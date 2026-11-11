# 수정 이력 반영 (D3, D4, D5)

구현: `reflection/support/ReflectionProposer`, `SpecBlockIndex`, `ReflectionValidator`, `ReflectionApplier`, `reflection/facade/ReflectionFacade`

## 수정 이력 표 (PM 작성)

| 항목 | 규칙 |
|---|---|
| 위치 | 명세 본문, 제목 `수정 이력` 바로 다음 표 |
| 열 | `스프린트`, `수정 사항`, `반영일`(첫 행이 머리글, 열 순서는 상관없음) |
| `스프린트` | Notion 스프린트 `이름`과 같아야 한다(앞뒤 공백만 무시, 대소문자 구분) |
| `반영일` | PM은 비워 둔다. qa-server가 반영 뒤 `YYYY-MM-DD`(KST)를 쓴다. 비었거나 `-`면 반영하지 않은 행 |

- 이번 스프린트 행이 있으면 그 화면은 `changed = true`(체크리스트, 디자인 검수 화면의 `변경` 표시)
- 이번 스프린트 행 중 `반영일`이 빈 행만 반영한다. 다른 스프린트 행과 반영일이 찬 행은 다시 반영하지 않는다

## 반영 제안 만들기 (SPEC_COLLECT)

- 본문을 블록 목록으로 바꿔 LLM에 준다. 글 블록 `[b1]`, 표 `[t1]`, 표 행 `[t1.r2]`. 수정 이력 절은 목록에서 뺀다. 머리글 행과 글이 아닌 블록은 `(수정 금지)`
- 프롬프트: `prompts/spec-reflection-{system,user}.txt`. 출력: `evidence`, `operations[{historyRow, op, target, blockType, text, cells}]`, `skippedRows[{historyRow, reason}]`
- 편집 종류: `UPDATE_TEXT`, `UPDATE_ROW`, `INSERT_BLOCK_AFTER`, `INSERT_ROW_AFTER`, `DELETE`
- 코드 검증(실패하면 오류를 붙여 최대 2회 다시 요청):
  - 대상 키가 목록에 있음, 편집 종류와 대상이 맞음(글 블록에 UPDATE_TEXT, 머리글이 아닌 행에 UPDATE_ROW 등)
  - 표 행 칸 수가 그 표의 열 수와 같음, 맨 위 표는 첫 칸(항목 이름)을 바꾸지 않고 행을 추가하지 않음
  - 같은 대상을 두 번 바꾸거나 지우지 않음
  - 모든 이력 행을 편집이나 `skippedRows`로 다룸
- 바뀌기 전, 후 마크다운은 코드가 만든다(`before`는 지금 본문, `after`는 편집을 적용한 본문)
- 검증을 3번 모두 통과하지 못하면 `FAILED`, `appliable = false`, 실패 메시지. 이 제안은 승인할 수 없고 거절하거나 재수집한다
- 같은 기능의 APPLIED가 아닌 이전 제안은 새 제안으로 바꾼다

## 상태

| 상태 | 뜻 | 할 수 있는 일 |
|---|---|---|
| PENDING | 승인 대기 | 승인, 거절 |
| APPROVED | Notion에 쓰는 중(동시 승인 막기) | 거절(멈춘 경우 정리용) |
| APPLIED | 반영 끝 | - |
| FAILED | 반영 실패 또는 제안 생성 실패 | 승인(반영 실패일 때만, 남은 편집부터), 거절 |
| REJECTED | 반영하지 않음 | - |

- `pendingReflectionCount`는 PENDING, APPROVED, FAILED 수
- 승인, 거절은 SPEC_LOAD이고 실행 중인 명세 로드 작업이 없을 때만(아니면 409 `SPRINT_4092`). `version`이 다르면 409 `GLOBAL_4091`
- 모든 제안을 처리하면(`proceedAfterReflections`) CHECKLIST로 넘기고 QA 준비 작업을 등록한다

## 승인 (Notion 반영)

1. 상태를 APPROVED로 바꾼다
2. 페이지 본문을 다시 읽는다. 아직 편집을 하나도 쓰지 않았으면 대상 블록의 `last_edited_time`을 제안 때와 비교한다. 대상이 없거나 바뀌었으면 쓰지 않고 FAILED, 409 `REFLECTION_4091`
3. 반영할 이력 행이 그대로 있는지(스프린트, 수정 사항) 확인한다. 바뀌었으면 2와 같다
4. 쓰기 범위(`NotionWriteScopeDto`)를 만들어 편집을 순서대로 쓴다. 하나 쓸 때마다 적용 여부와 새 블록 ID를 저장한다. 같은 대상 뒤 추가는 앞서 추가한 블록 뒤에 이어 붙인다
5. 반영할 이력 행의 `반영일` 칸에 오늘(KST)을 쓴다(이미 찬 행은 건너뛴다)
6. 페이지를 다시 읽어 기준 명세(`feature`, `sprint_feature`)를 바꾸고 APPLIED
- Notion 읽기·쓰기가 실패하면 FAILED와 `Notion 반영 실패: …`, 502 `REFLECTION_5021`. 다시 승인하면 적용하지 않은 편집부터 이어서 쓴다(이때는 2의 비교를 하지 않는다)
- 편집한 블록의 글은 서식 없이 평문으로 쓴다

## 거절

- 반영하지 않고 지금 본문을 기준 명세로 쓴다. 이미 일부 편집을 쓴 제안이면 페이지를 다시 읽어 기준 명세를 바꾼다(읽기 실패는 502 `REFLECTION_5021`)
- `반영일`은 쓰지 않는다. 같은 스프린트에서 재수집하면 다시 제안된다

## Notion 쓰기 허용 범위 (`NotionWriteGuard`)

| 요청 | 허용 조건 |
|---|---|
| GET, `POST /v1/search`, `POST /v1/databases/{id}/query` | 항상 |
| `PATCH /v1/blocks/{id}` 글 블록 | 범위의 블록이고 블록 종류가 같으며 본문이 `{종류: {rich_text}}`뿐 |
| `PATCH /v1/blocks/{id}` 표 행 | 범위의 본문 표 행이고 칸 수가 열 수와 같음 |
| `PATCH /v1/blocks/{id}` 수정 이력 행 | 이번 제안의 이력 행이고 `반영일` 칸만 바뀌며 값이 `YYYY-MM-DD` |
| `PATCH /v1/blocks/{id}/children` | 부모가 대상 페이지나 범위의 글 블록(글 블록만), 또는 본문 표(열 수가 맞는 행만). `after`는 페이지 안 블록. 하위 블록을 함께 보내지 않음, 100개까지 |
| `DELETE /v1/blocks/{id}` | 범위의 글 블록이나 본문 표 행(휴지통 이동) |
| 그 밖(`PATCH /v1/pages/*` 포함) | 막음 |

- 범위는 승인 때 이번 스프린트 `대상 기능` 페이지 하나를 다시 읽어 만든다. 수정 이력 제목과 표, 머리글 행, 하위 페이지·DB는 범위에 넣지 않는다
- Notion Integration에 "Insert content" 권한이 있어야 블록을 추가할 수 있다
