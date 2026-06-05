-- stored_path는 FileStorage 키이자 파일↔레코드 1:1 매핑의 진실. 중복 시 삭제/서빙 매핑이 깨지므로 유니크 보장.
-- (V5는 이미 적용돼 있어 편집 대신 후속 마이그레이션으로 제약을 추가한다.)
ALTER TABLE media ADD CONSTRAINT uq_media_stored_path UNIQUE (stored_path);
