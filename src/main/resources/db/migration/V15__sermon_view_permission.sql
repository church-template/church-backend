-- 설교 회원전용 전환: SERMON_VIEW 신설 (V13 패턴).
-- 관리자에겐 조회+쓰기 모두, MEMBER(승인 교인)에겐 조회만.
INSERT INTO permissions (name, description) VALUES
    ('SERMON_VIEW', '설교 조회(회원 전용 열람)');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r
JOIN permissions p ON p.name = 'SERMON_VIEW'
WHERE r.name IN ('SUPER_ADMIN', 'ADMIN', 'MEMBER');
