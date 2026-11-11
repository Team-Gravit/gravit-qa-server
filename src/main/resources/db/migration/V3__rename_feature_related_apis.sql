-- V3__rename_feature_related_apis.sql

-- 관련 API 저장 형식이 operationId에서 "메서드 URI"로 바뀌어 컬럼 이름을 맞춘다
ALTER TABLE feature RENAME COLUMN related_operation_ids TO related_apis;

-- 예전 형식(operationId) 값이 새 형식과 섞이지 않게 비운다. 다음 준비에서 다시 채운다
UPDATE feature SET related_apis = '{}';
