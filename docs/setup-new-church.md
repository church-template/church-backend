# 새 교회 인스턴스 배포 (setup-new-church)

**원칙: 코드 수정 0.** 새 교회 = 레포 복사 → `.env` 작성 → 배포. 코드를 고쳐야 한다면
멀티-교회 템플릿 원칙 위반이다(`.claude/rules/multi-church-template.md`) — 변화를 config로 밀어낼 것.

## 1. 레포 준비

템플릿 레포를 새 교회 레포로 복사(fork/템플릿 생성). 이후 절차에서 코드는 건드리지 않는다.

## 2. `.env` 작성 (전체 키 목록 정본: `.env.example`)

**교회마다 반드시 달라야 하는 값:**

| 키 | 설명 |
|---|---|
| `JWT_SECRET` | 교회별 필수 상이 — 유출 시 토큰 위조 가능 |
| `DB_PASSWORD` / `REDIS_PASSWORD` | 강한 랜덤 값 |
| `CORS_ALLOWED_ORIGIN` | 해당 교회 프론트 도메인 (예: `https://www.<church-domain>`) |
| `FILE_BASE_URL` | `https://api.<church-domain>/api/media` 형태 |

**최초 관리자 부트스트랩(3종 모두 채워야 동작):** `ADMIN_PHONE`·`ADMIN_NAME`·`ADMIN_PASSWORD` —
기동 시 활성 SUPER_ADMIN이 없을 때만 1회 멱등 생성. **최초 로그인 후 비밀번호 즉시 변경.**
(SUPER_ADMIN은 API로 부여 불가 — 이 부트스트랩이 유일한 생성 경로다.)

**선택 조정값:** `SWAGGER_ENABLED`(공개 서버는 `false` 권장), `FILE_MAX_SIZE`, `CACHE_TTL`,
`VIEW_FLUSH_INTERVAL`, `APP_TIMEZONE`(기본 Asia/Seoul), `JWT_*_EXPIRY`.

`.env`는 서버(`/srv/church-backend/.env`)에만 존재 — git에 절대 커밋 금지.

## 3. 서버 프로비저닝 (교회별 1회)

정본: **`docs/deploy-server-setup.md`** — Docker 설치(§1), 배포 디렉터리+`.env`(§2),
GHCR 인증(§3), 방화벽 개방(§4), 배포용 SSH 키+GitHub Secrets(§5).

GitHub Secrets는 3개만: `DEPLOY_HOST` / `DEPLOY_USER` / `DEPLOY_SSH_KEY`. 앱 시크릿은 서버 `.env`에만.

## 4. 배포 트리거

- **`deploy` 브랜치 push** → GHCR 멀티아치 이미지 빌드 + SSH 배포(`.github/workflows/deploy.yml`),
  `up -d` 후 `/actuator/health` 180초 헬스 게이트.
- 버전봇 커밋(`[skip ci]`) 때문에 워크플로우가 안 돌면 수동 트리거:
  `gh workflow run deploy.yml --ref deploy`
- `DEPLOY_HOST` 시크릿이 비어 있으면 이미지 push까지만 수행(서버 배포 skip).

## 5. HTTPS

정본: `docs/deploy-server-setup.md` §7 — Caddy 리버스 프록시(host network, Let's Encrypt 자동),
DNS `api.<church-domain>` → 서버 IP, `.env` 도메인 반영 후 백엔드 재기동.
앱은 `forward-headers-strategy: framework`로 프록시 뒤 https를 인식한다(8080 직접 노출 금지 전제).

## 6. 배포 검증 체크리스트

```bash
curl -s https://api.<church-domain>/actuator/health   # {"status":"UP"}
# 최초 관리자 로그인(§2의 ADMIN_PHONE/ADMIN_PASSWORD):
curl -s -X POST https://api.<church-domain>/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"phone":"<ADMIN_PHONE>","password":"<ADMIN_PASSWORD>"}'
```

- [ ] health UP · [ ] 관리자 로그인 → 즉시 비번 변경 · [ ] 프론트에서 CORS 통과
- [ ] 목데이터 없음 확인(운영은 dev 프로필 미사용 — 회원 목록에 9000번대 id가 없어야 정상)
- [ ] `SWAGGER_ENABLED=false`라면 `/docs/swagger-ui.html` 404

운영 DB 원격 점검: `docs/db-remote-access.md`.
