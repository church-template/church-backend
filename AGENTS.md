# AGENTS.md — AI 에이전트 진입점

교회 홈페이지용 **재사용 템플릿 백엔드**(Spring Boot 4.0.x · Java 21 · PostgreSQL 16 · Redis 7 · Flyway).
코드는 single-church로 유지하고 교회별 차이는 전부 `.env`로 주입한다 — **멀티테넌시 없음**
(`church_id`/`tenant_id` 금지; 교회당 별도 DB·별도 배포). 전체 설계 정본: `docs/church-backend-spec.md`(한국어).

작업 언어는 **한국어**: 커밋 `<type> : <설명>`(콜론 앞 공백, feat/fix/refactor/docs/test/chore/perf/ci),
커밋·푸시는 요청받았을 때만.

## 시작하기

| 하려는 일 | 읽을 문서 |
|---|---|
| 로컬에서 앱 실행·개발 | `docs/setup-dev.md` |
| 새 교회 인스턴스 배포 | `docs/setup-new-church.md` |
| 도메인 기능 구현 | `docs/church-backend-spec.md` 해당 § + 아래 규칙 파일 |
| 운영 DB 점검 | `docs/db-remote-access.md` |

## 핵심 불변식 (요약 — 상세는 `.claude/rules/`의 각 파일이 정본)

- **RBAC**: 직분(position)과 권한은 독립 축. 인가는 항상 권한 단위
  `@PreAuthorize("hasAuthority('SERMON_WRITE')")` — role·직분으로 검사 금지. 역할 priority 계층 가드
  (부여는 strictly-below, 역할 수정은 at-or-below). → `rbac-authorization.md`
- **경로 인가**: `/api/admin/**` 인증+메서드 권한 · `/api/gallery/**`·`/api/bible-challenges/**`·`/api/sermons/**`·`/api/vehicle-runs/**`
  회원전용(각 `GALLERY_VIEW`/`CHALLENGE_PARTICIPATE`/`SERMON_VIEW`/`VEHICLE_APPLY`) · 나머지 public(`/api/main` 포함, 의도적).
  정본: `SecurityConfig` 매처 체인. → `rbac-authorization.md`
- **JWT**: `sub`=member uuid(BIGINT id 금지), payload는 평탄화된 permissions+maxPriority. → `rbac-authorization.md`
- **영속성**: soft delete(`deleted_at`) 전면 + 모든 목록 인덱스는 partial(`WHERE deleted_at IS NULL`),
  낙관락 `@Version`(충돌 409), 작성자 표시는 `updated_by`. → `persistence-conventions.md`
- **API**: RFC 7807 단일 에러 envelope + 정해진 errorCode 표, 목록은 `{content, page{...}}` 페이지 envelope,
  본문 markdown은 raw 저장. → `api-conventions.md`
- **미디어**: 이미지·PDF는 중앙 `media` 테이블 하나, 본문 참조는 `media:{id}` 문자열(URL 금지),
  삭제는 참조 있으면 409 차단. → `media-library.md`
- **SB4**: web 스타터는 `spring-boot-starter-webmvc`, 테스트는 모듈별 `*-test`. → `spring-boot-4.md`

## 명령어

```bash
./gradlew build     # 컴파일+전체 테스트(Testcontainers — Docker 필요)+jar
./gradlew test --tests 'com.elipair.church.SomeTest.someMethod'
docker compose up -d --build    # 로컬 전체 기동(상세·시드·검증: docs/setup-dev.md)
```

## 하지 말 것

- `version.yml`·`build.gradle`의 `version`·`CHANGELOG.*` 수동 편집(자동화 소유 — `versioning-ci.md`)
- `.github/workflows/PROJECT-COMMON-*`·`.github/scripts/*` 수정(템플릿 관리)
- 테넌시 컬럼·SMTP·복잡한 비밀번호 정책 추가(의도된 비기능 — `multi-church-template.md`)
- 교회별 실값(도메인·시크릿) 하드코딩 — 전부 `.env`
