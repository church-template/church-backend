-- 메뉴·글 공개 범위 설정(#95, 설계 eunsaem-frontend docs/superpowers/specs/2026-11-19-content-visibility-design.md).
-- V20 주석이 예약한 "멀티테넌시 2차 배포의 DEFAULT 제거"는 아직 파일이 없어 이 번호를 쓴다 — 그 작업은 다음 번호로 간다.

-- 교회별 메뉴 공개 범위. level은 문자열이라 나중에 단계를 더할 수 있다(설계 6.2).
CREATE TABLE content_visibility (
    id         BIGSERIAL   PRIMARY KEY,
    church_id  BIGINT      NOT NULL REFERENCES churches (id),
    domain     VARCHAR(20) NOT NULL,
    level      VARCHAR(20) NOT NULL,
    created_at TIMESTAMP   NOT NULL,
    updated_at TIMESTAMP,
    CONSTRAINT uq_content_visibility_church_domain UNIQUE (church_id, domain)
);

-- 글 한 건의 "교인만". 기존 글은 모두 표시 없음으로 시작한다(설계 5.2).
ALTER TABLE sermons ADD COLUMN members_only BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE gallery_albums ADD COLUMN members_only BOOLEAN NOT NULL DEFAULT FALSE;

INSERT INTO permissions (name, description) VALUES
    ('NOTICE_VIEW',       '공지 조회(교인 전용일 때)'),
    ('EVENT_VIEW',        '일정 조회(교인 전용일 때)'),
    ('BULLETIN_VIEW',     '주보 조회(교인 전용일 때)'),
    ('VISIBILITY_MANAGE', '메뉴 공개 범위 설정');

-- 새 조회 권한은 GALLERY_VIEW를 가진 모든 역할에 준다 — 역할 이름으로 고르면 교회가 만든 커스텀 역할이 빠진다(설계 5.3).
INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, p.id
FROM role_permissions rp
JOIN permissions gv ON gv.id = rp.permission_id AND gv.name = 'GALLERY_VIEW'
JOIN permissions p ON p.name IN ('NOTICE_VIEW', 'EVENT_VIEW', 'BULLETIN_VIEW')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r
JOIN permissions p ON p.name = 'VISIBILITY_MANAGE'
WHERE r.name IN ('SUPER_ADMIN', 'ADMIN')
ON CONFLICT DO NOTHING;

-- 기존 교회는 지금 동작 그대로: 설교·갤러리 = 교인만, 나머지 = 누구나(설계 5.2).
INSERT INTO content_visibility (church_id, domain, level, created_at)
SELECT c.id, d.domain, d.level, now()
FROM churches c
CROSS JOIN (VALUES
    ('NOTICE', 'PUBLIC'),
    ('EVENT', 'PUBLIC'),
    ('BULLETIN', 'PUBLIC'),
    ('SERMON', 'MEMBERS'),
    ('GALLERY', 'MEMBERS')) AS d (domain, level);
