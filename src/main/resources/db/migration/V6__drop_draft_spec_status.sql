-- V6__drop_draft_spec_status.sql

-- 쓰지 않는 명세 상태 DRAFT를 없애고 확인 필요(NEEDS_CHECK), 확정(CONFIRMED)만 남긴다
ALTER TABLE feature
    DROP CONSTRAINT ck_feature_spec_status,
    ADD CONSTRAINT ck_feature_spec_status CHECK (spec_status IN ('NEEDS_CHECK', 'CONFIRMED'));
