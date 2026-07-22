-- 픽업 위치 좌표(이슈 #65, docs/superpowers/specs/2026-07-22-vehicle-pickup-coordinates-design.md).
-- 탑승 신청에 위경도 좌표(선택)를 담고 픽업 텍스트를 선택으로 완화한다 — 텍스트·좌표 중 최소 하나는 애플리케이션 검증.
-- 기존 행: 좌표 NULL, pickup_location은 값 보유(하위호환·백필 불요). 좌표 쌍 무결성도 애플리케이션 검증(DB CHECK 없음).
ALTER TABLE vehicle_requests
    ALTER COLUMN pickup_location DROP NOT NULL,
    ADD COLUMN latitude  DOUBLE PRECISION,
    ADD COLUMN longitude DOUBLE PRECISION;
