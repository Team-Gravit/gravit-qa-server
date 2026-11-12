-- V5__design_not_applicable_and_cell_evidence.sql

-- 디자인 검수 항목을 사람이 해당 없음(NOT_APPLICABLE)으로 바꿀 수 있게 상태 값을 넓힌다
ALTER TABLE design_review_item
    DROP CONSTRAINT ck_design_review_item_state,
    ADD CONSTRAINT ck_design_review_item_state CHECK (state IN ('UNCHECKED', 'PASS', 'FAIL', 'NOT_APPLICABLE'));

-- 1차 판정 근거(경로, 시작 줄, 끝 줄)를 사유와 따로 둔다
ALTER TABLE check_cell
    ADD COLUMN evidence JSONB NOT NULL DEFAULT '[]'::jsonb;

-- 셀 이력에도 그 판정의 근거를 남긴다
ALTER TABLE check_cell_history
    ADD COLUMN evidence JSONB NOT NULL DEFAULT '[]'::jsonb;
