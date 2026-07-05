# 배포 서버 초기 세팅 가이드 (OCI VM, 교회별 1회)

새 교회 배포 = **코드 수정 0**. 이 문서의 체크리스트만 수행하면
`deploy` 브랜치 push마다 GitHub Actions가 자동 배포한다.

전제: OCI VM (Ubuntu 22.04+ 권장, Ampere ARM/x86 모두 지원 — 이미지가 멀티아치).

## 1. Docker 설치

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER   # 재로그인 필요
docker compose version           # v2.x 확인
```

## 2. 배포 디렉터리 + `.env` 작성

```bash
sudo mkdir -p /srv/church-backend
sudo chown $USER:$USER /srv/church-backend
```

레포의 `.env.example`을 참고해 `/srv/church-backend/.env`를 작성한다.
반드시 채울 값: `DB_PASSWORD`, `REDIS_PASSWORD`, `JWT_SECRET`(교회마다 반드시 다르게),
`CORS_ALLOWED_ORIGIN`, `FILE_BASE_URL`, (최초 1회) `ADMIN_PHONE`/`ADMIN_NAME`/`ADMIN_PASSWORD`.

```bash
chmod 600 /srv/church-backend/.env
```

> `docker-compose.yml`과 `docker-compose.prod.yml`은 배포 워크플로우가 매번 scp로
> 동기화하므로 직접 복사할 필요 없다.

## 3. GHCR pull 인증 (레포가 private인 경우만)

read:packages 권한만 가진 PAT(classic)를 만들어 1회 로그인:

```bash
echo "<PAT>" | docker login ghcr.io -u <github-username> --password-stdin
```

## 4. 네트워크 개방 (OCI는 2중 방화벽)

**(a) OCI 콘솔** — VCN → 서브넷의 Security List(또는 NSG)에 인그레스 규칙 추가:
- TCP 22 (SSH — 가능하면 관리자 IP로 제한)
- TCP 8080 (또는 리버스 프록시 도입 시 80/443)

**(b) OS 방화벽** — OCI 기본 이미지는 iptables가 별도로 막고 있다:

```bash
sudo iptables -I INPUT -p tcp --dport 8080 -j ACCEPT
sudo netfilter-persistent save   # Ubuntu (iptables-persistent)
```

## 5. 배포용 SSH 키 + GitHub Secrets

로컬(관리자 PC)에서 배포 전용 키 생성:

```bash
ssh-keygen -t ed25519 -f church-deploy-key -C "church-backend-deploy" -N ""
```

- 공개키(`church-deploy-key.pub`) 내용을 VM의 `~/.ssh/authorized_keys`에 추가.
- GitHub 레포 → Settings → Secrets and variables → Actions에 등록:

| Secret | 값 |
|---|---|
| `DEPLOY_HOST` | VM 공인 IP |
| `DEPLOY_USER` | SSH 유저 (Ubuntu 이미지 `ubuntu`, Oracle Linux `opc`) |
| `DEPLOY_SSH_KEY` | `church-deploy-key` **개인키 전문** |

앱 시크릿(DB/Redis 비밀번호, JWT_SECRET 등)은 GitHub에 **절대 넣지 않는다** — 서버 `.env`에만 존재.

## 6. 첫 배포 & 확인

`main → deploy` PR을 머지(또는 deploy에 push)하면 Actions가 배포한다. 서버에서 확인:

```bash
cd /srv/church-backend
docker compose ps                                   # backend/postgres/redis healthy
curl -f http://localhost:8080/actuator/health       # {"status":"UP"}
```

롤백: Actions → Deploy → Run workflow → `image_tag`에 이전 버전(예: `0.0.31`) 입력 후 실행.

## 범위 외 (별도 이슈)

- HTTPS 리버스 프록시(Caddy/Nginx) — 도입 시 OCI 인그레스는 80/443만 열고 8080은 닫는다.
