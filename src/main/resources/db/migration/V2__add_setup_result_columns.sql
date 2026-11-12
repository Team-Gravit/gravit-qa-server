-- V2__add_setup_result_columns.sql

-- 스프린트 대상 기능별 Figma 프레임 node ID와 준비 경고({code, message} 배열)
ALTER TABLE sprint_feature
    ADD COLUMN figma_node_ids TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN warnings       JSONB  NOT NULL DEFAULT '[]'::jsonb;

-- 스프린트 단위 경고(종료 시 Notion 기록 실패 등, {code, message} 배열)
ALTER TABLE sprint
    ADD COLUMN warnings JSONB NOT NULL DEFAULT '[]'::jsonb;

-- 마지막 준비 실패 사유: 화면 안내(guide)의 막힌 이유 판정용
ALTER TABLE sprint
    ADD COLUMN prepare_failure_reason VARCHAR(255),
    ADD CONSTRAINT ck_sprint_prepare_failure_reason CHECK (prepare_failure_reason IN ('RELEASE_BRANCH_MISSING'));
