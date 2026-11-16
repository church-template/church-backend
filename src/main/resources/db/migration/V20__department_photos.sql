-- 부서 대표 사진·연결 앨범(#92). V20=이 파일, 멀티테넌시 2차 배포의 DEFAULT 제거는 V21로 재번호.
-- hero_media_id는 ON DELETE SET NULL(V12 주보 선례): 삭제된 부서는 참조 목록에서 빠져 미디어 삭제가 허용되므로
--   SET NULL이 없으면 FK 위반(500)이 난다. 앨범은 soft delete만 하므로 album_id는 절 없음. 인덱스 없음(부서 수 적음).
ALTER TABLE departments
    ADD COLUMN hero_media_id BIGINT REFERENCES media (id) ON DELETE SET NULL,
    ADD COLUMN album_id      BIGINT REFERENCES gallery_albums (id);
