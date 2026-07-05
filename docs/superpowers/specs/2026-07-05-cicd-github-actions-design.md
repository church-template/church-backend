# CI/CD (GitHub Actions) 설계

- 날짜: 2026-07-05
- 상태: 설계 승인됨 (구현 전)
- 배포 대상: OCI VM (Ampere ARM 또는 x86 — 멀티아치로 양쪽 지원)

## 1. 배경

- 현재 워크플로우는 SUH 템플릿(버전 관리·체인지로그·라이브러리 publish)뿐, **테스트 CI와 배포 CD가 없다.**
- 설정 모델 정리 (이 설계의 전제):
  - Spring은 `.env` 파일을 **읽지 않는다.** Spring이 읽는 것은 `application.yml`과 **OS 환경변수**뿐이며, yml의 `${DB_PASSWORD}` 플레이스홀더가 환경변수를 참조한다.
  - `.env`는 **docker-compose의 입력 파일**이다. compose가 읽어 컨테이너 환경변수로 주입한다.
  - 따라서 CI에는 `.env`가 필요 없고(git-ignore라 체크아웃에 없음), 서버에는 교회별 `.env`가 1회 배치된다.

## 2. 확정된 결정

| 항목 | 결정 |
|---|---|
| CI 트리거 | `main` 대상 PR + `main` push (버전봇 커밋은 `[skip ci]`로 자동 제외) |
| CD 트리거 | `deploy` 브랜치 push + `workflow_dispatch` (main→deploy PR 플로우 권장 — 기존 체인지로그 자동화와 호환) |
| 레지스트리 | GHCR (`ghcr.io/church-template/church-backend`, GITHUB_TOKEN 인증) |
| 이미지 아키텍처 | **멀티아치** `linux/amd64,linux/arm64` (OCI Ampere ARM 대비) |
| 배포 방식 | SSH 접속 → compose 파일 scp 동기화 → `docker compose pull` → `up -d` |
| 앱 시크릿 위치 | **서버 `.env`에만** 존재. GitHub Secrets에는 배포 인프라 시크릿(SSH)만 |

## 3. 구성요소

### 3.1 `.github/workflows/ci.yml` (신규)

- 트리거: `pull_request`(main), `push`(main).
- 단계: checkout → setup-java 21 (temurin) → `gradle/actions/setup-gradle`(캐싱) → `./gradlew build`.
- Testcontainers가 러너 내장 Docker로 실제 Postgres를 띄우므로 **시크릿 0개**.
- `concurrency`로 같은 PR의 이전 실행 자동 취소.

### 3.2 `.github/workflows/deploy.yml` (신규)

**Job 1 — build-push:**

- `docker/setup-qemu-action` + `docker/setup-buildx-action` → `docker/build-push-action`.
- `--platform linux/amd64,linux/arm64`, GHA 레이어 캐시 사용.
- 태그: `{version}`(version_manager.sh get 재사용), `latest`, `{sha}`. 이미지명은 `ghcr.io/${{ github.repository }}` (소문자 필수 — 현재 owner/repo는 이미 소문자).
- 권한: `packages: write`, 인증 `GITHUB_TOKEN`.

**Job 2 — deploy (needs: build-push):**

- `scp`로 `docker-compose.yml` + `docker-compose.prod.yml`를 서버 `/srv/church-backend/`에 동기화 (compose 파일 드리프트 방지).
- SSH로 실행:
  ```
  cd /srv/church-backend
  IMAGE_TAG={version} docker compose -f docker-compose.yml -f docker-compose.prod.yml pull backend
  IMAGE_TAG={version} docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
  docker image prune -f
  ```
- `workflow_dispatch`에 선택 입력 `image_tag` — 지정 시 해당 태그로 배포(=**롤백 경로**: 이전 버전 태그를 넣고 수동 실행).
- 배포는 항상 **버전 태그**로 pull (latest 아님) — 서버에 떠 있는 버전이 항상 명시적.

### 3.3 `Dockerfile` 수정 (1줄)

빌드 스테이지를 `FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk AS build`로 변경.
jar는 아키텍처 무관 바이트코드이므로 Gradle 빌드는 러너 네이티브(amd64)로 1회만 수행하고, 런타임 스테이지만 타깃 아키텍처별로 생성 — QEMU 에뮬레이션 하의 Gradle 빌드(배포당 15분+)를 회피한다.

### 3.4 `docker-compose.prod.yml` (신규, 커밋됨)

```yaml
services:
  backend:
    image: ghcr.io/church-template/church-backend:${IMAGE_TAG:-latest}
```

- base의 `build: .`를 GHCR 이미지 참조로 대체하는 프로덕션 오버레이.
- 로컬 개발(`docker compose up` = base + gitignore된 override)은 영향 없음.
- 교회 추가 = 서버에 compose 2장 + 교회별 `.env` 배치 — 코드 수정 0 (멀티처치 원칙 유지).

### 3.5 GitHub Secrets (배포 인프라용만)

| 시크릿 | 내용 |
|---|---|
| `DEPLOY_HOST` | OCI VM 공인 IP/호스트 |
| `DEPLOY_USER` | SSH 유저 (Ubuntu 이미지 `ubuntu`, Oracle Linux `opc`) |
| `DEPLOY_SSH_KEY` | 배포 전용 개인키 (VM `authorized_keys`에 공개키 등록) |

앱 시크릿(DB/Redis 비밀번호, JWT_SECRET 등)은 **어디에도 넣지 않는다** — 서버 `.env`에만 존재.

### 3.6 서버 초기 세팅 문서 — `docs/deploy-server-setup.md` (신규)

교회별 1회 체크리스트:

1. Docker + compose plugin 설치.
2. `/srv/church-backend/` 생성, `.env` 작성 (`.env.example` 기반 — JWT_SECRET은 교회마다 상이).
3. 레포가 private인 경우 `docker login ghcr.io` 1회 (read:packages PAT).
4. **OCI 네트워크 개방**: Security List/NSG에서 22, 80/443(또는 8080) 인그레스 허용 + OS단 iptables 규칙 개방 (OCI 기본 이미지는 OS 방화벽이 별도로 막고 있음).
5. 배포용 SSH 키페어 생성, 공개키 등록, 개인키를 GitHub Secrets에 등록.

## 4. 기존 템플릿과의 충돌 처리

- `PROJECT-SPRING-GITHUB-PACKAGES-PUBLISH`는 deploy push마다 실행되지만 `build.gradle`에 `publishing` 블록이 없어 실패한다. 라이브러리 배포 전용(헤더 명시: "Spring Boot 애플리케이션 배포가 아닙니다")이므로 이 레포에는 해당 없음 → **GitHub UI에서 워크플로우 비활성화** (파일 무수정 — 템플릿 소유 파일 불변 원칙 준수).
- 신규 워크플로우 파일명은 `PROJECT-` 접두사를 쓰지 않는다 (템플릿 네임스페이스와 구분).

## 5. 검증 계획

- **CI**: PR을 열어 `./gradlew build`(전체 테스트 포함)가 시크릿 없이 그린인지 확인.
- **CD Job 1**: deploy push 후 GHCR에 멀티아치 매니페스트(amd64+arm64)가 존재하는지 확인 (`docker manifest inspect`).
- **CD Job 2**: 서버에서 `docker compose ps` 헬시 + `curl http://localhost:8080/actuator/health` 200 확인. (서버 준비 전에는 Job 2를 시크릿 부재 시 skip 처리해 Job 1만으로도 파이프라인이 그린이 되게 한다.)
- **롤백**: workflow_dispatch + `image_tag`로 직전 버전 재배포가 되는지 1회 리허설.

## 6. 범위 외 (의도적 제외)

- 무중단 배포(blue-green 등) — 단일 VM + compose 재기동으로 충분 (짧은 다운타임 허용).
- 스테이징 환경 — 교회별 단일 운영 인스턴스 모델.
- HTTPS 리버스 프록시(Caddy/Nginx) 구성 — 별도 이슈로 분리 (서버 세팅 문서에서 언급만).
