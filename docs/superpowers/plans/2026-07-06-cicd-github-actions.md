# CI/CD (GitHub Actions) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** PR 테스트 CI + deploy 브랜치 기반 CD(GHCR 멀티아치 이미지 push + OCI VM SSH 배포)를 구축한다.

**Architecture:** CI는 `main` 대상 PR/push에서 `./gradlew build`(Testcontainers 포함, 시크릿 0개). CD는 `deploy` push 시 buildx로 amd64+arm64 이미지를 GHCR에 push한 뒤, SSH로 VM에 접속해 compose 파일을 동기화하고 `docker compose pull && up -d`를 실행한다. 앱 시크릿은 서버 `.env`에만 존재한다.

**Tech Stack:** GitHub Actions, docker buildx(QEMU), GHCR, appleboy/ssh-action·scp-action, docker compose 오버레이.

**Spec:** `docs/superpowers/specs/2026-07-05-cicd-github-actions-design.md`

## Global Constraints

- 템플릿 소유 파일 수정 금지: `.github/workflows/PROJECT-COMMON-*`, `.github/workflows/PROJECT-SPRING-*`, `.github/scripts/*`, `version.yml`, `build.gradle`의 `version` 줄, `CHANGELOG.*`.
- 신규 워크플로우 파일명에 `PROJECT-` 접두사 금지 (템플릿 네임스페이스 구분).
- 커밋 메시지: `<type> : <한국어 설명> #<이슈번호>` (콜론 앞 공백), **Co-Authored-By 태그 절대 금지**.
- 교회별 값·시크릿 하드코딩 금지 — 앱 시크릿은 서버 `.env`에만, GitHub Secrets에는 `DEPLOY_HOST`/`DEPLOY_USER`/`DEPLOY_SSH_KEY` 3개만.
- 이미지명: `ghcr.io/church-template/church-backend` (GHCR은 소문자 필수 — 현재 owner/repo 그대로 사용 가능).
- 서버 배포 경로 고정: `/srv/church-backend`.
- 인프라 파일은 단위테스트 대상이 아님 — 각 태스크의 "테스트"는 **실행 가능한 검증 명령**(YAML 파스, `docker compose config`, `docker build`, PR에서의 실제 CI 실행)이다.
- 로컬 검증 중 `docker build`/`compose config`는 Docker Desktop(또는 colima)이 떠 있어야 한다. 데몬이 없으면 해당 스텝은 "PR CI에서 검증"으로 대체하고 넘어간다.

---

### Task 1: 이슈·브랜치 생성 + 스펙/플랜 문서 커밋

**Files:**
- Commit: `docs/superpowers/specs/2026-07-05-cicd-github-actions-design.md` (이미 작성됨)
- Commit: `docs/superpowers/plans/2026-07-06-cicd-github-actions.md` (이 문서)

**Interfaces:**
- Produces: 이슈 번호 `$ISSUE` (이후 모든 커밋 메시지 꼬리에 `#$ISSUE`), 작업 브랜치 `20260706_#$ISSUE_CICD_구축`.

- [ ] **Step 1: GitHub 이슈 생성**

```bash
gh issue create \
  --title "GitHub Actions CI/CD 구축 (테스트 CI + GHCR/SSH 배포 CD)" \
  --body "$(cat <<'EOF'
## 목표
- PR/main push에서 ./gradlew build를 돌리는 테스트 CI 추가 (시크릿 0개, Testcontainers)
- deploy 브랜치 push 시 GHCR 멀티아치(amd64+arm64) 이미지 push + OCI VM SSH 배포 CD 추가

## 산출물
- .github/workflows/ci.yml
- .github/workflows/deploy.yml
- Dockerfile 빌드 스테이지 BUILDPLATFORM 고정 (1줄)
- docker-compose.prod.yml (프로덕션 오버레이)
- docs/deploy-server-setup.md (교회별 서버 1회 세팅 가이드)

## 설계 문서
docs/superpowers/specs/2026-07-05-cicd-github-actions-design.md
EOF
)"
```

Expected: 이슈 URL 출력. 번호를 `$ISSUE`로 기억한다 (예: `47`).

- [ ] **Step 2: 브랜치 생성**

```bash
git checkout -b "20260706_#${ISSUE}_CICD_구축"
```

Expected: `Switched to a new branch ...`

- [ ] **Step 3: 스펙 + 플랜 문서 커밋**

```bash
git add docs/superpowers/specs/2026-07-05-cicd-github-actions-design.md \
        docs/superpowers/plans/2026-07-06-cicd-github-actions.md
git commit -m "docs : CI/CD 설계 스펙 및 구현 플랜 추가 #${ISSUE}"
```

Expected: 커밋 성공, 파일 2개.

---

### Task 2: 테스트 CI 워크플로우 (`ci.yml`)

**Files:**
- Create: `.github/workflows/ci.yml`

**Interfaces:**
- Consumes: 없음 (레포의 gradlew, Testcontainers 테스트 스위트).
- Produces: `main` 대상 PR에서 자동 실행되는 `CI` 워크플로우 — Task 7의 PR이 이 워크플로우로 그린 여부를 검증한다.

- [ ] **Step 1: 워크플로우 파일 작성**

`.github/workflows/ci.yml` 전체 내용:

```yaml
# 테스트 CI — main 대상 PR과 main push에서 전체 빌드+테스트를 실행한다.
# 시크릿 불필요: 통합테스트는 Testcontainers가 러너 내장 Docker로 실제 Postgres를 띄운다.
# 버전봇 커밋은 "[skip ci]"를 달고 오므로 push 트리거에서 자동 제외된다(GitHub 기본 동작).
name: CI

on:
  pull_request:
    branches: ["main"]
  push:
    branches: ["main"]

concurrency:
  group: ci-${{ github.ref }}
  cancel-in-progress: true

permissions:
  contents: read

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v5

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Build & Test
        run: ./gradlew build
```

- [ ] **Step 2: YAML 문법 검증**

```bash
ruby -ryaml -e 'YAML.load_file(".github/workflows/ci.yml"); puts "OK"'
```

Expected: `OK` (macOS 내장 ruby 사용. 파스 에러 시 들여쓰기 수정).

- [ ] **Step 3: 커밋**

```bash
git add .github/workflows/ci.yml
git commit -m "ci : 테스트 CI 워크플로우 추가(main PR/push, Testcontainers) #${ISSUE}"
```

---

### Task 3: Dockerfile 멀티아치 대비 (1줄 수정)

**Files:**
- Modify: `Dockerfile:4` (빌드 스테이지 FROM 줄)

**Interfaces:**
- Produces: `$BUILDPLATFORM` 고정된 빌드 스테이지 — Task 5의 buildx 멀티아치 빌드가 QEMU 에뮬레이션 없이 Gradle을 러너 네이티브로 1회만 돌리는 전제.

- [ ] **Step 1: FROM 줄 수정**

변경 전:

```dockerfile
FROM eclipse-temurin:21-jdk AS build
```

변경 후:

```dockerfile
# 빌드 스테이지는 빌드 호스트 네이티브 아키텍처로 고정 — jar는 아키텍처 무관 바이트코드이므로
# Gradle 빌드는 1회만 수행하고, 런타임 스테이지만 타깃 플랫폼(amd64/arm64)별로 생성된다.
# (이 고정이 없으면 buildx 멀티아치 빌드 시 QEMU 에뮬레이션으로 Gradle이 돌아 배포당 15분+ 소요)
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk AS build
```

- [ ] **Step 2: 로컬 빌드 스모크 (Docker 데몬 필요)**

```bash
docker build -t church-backend:ci-smoke .
```

Expected: `Successfully built`/`naming to ...` 성공 종료 (수 분 소요). 데몬이 없으면 이 스텝은 건너뛰고 Task 7 PR 이후 첫 deploy 실행에서 검증.

- [ ] **Step 3: 커밋**

```bash
git add Dockerfile
git commit -m "ci : Dockerfile 빌드 스테이지 BUILDPLATFORM 고정(멀티아치 대비) #${ISSUE}"
```

---

### Task 4: 프로덕션 compose 오버레이 (`docker-compose.prod.yml`)

**Files:**
- Create: `docker-compose.prod.yml`

**Interfaces:**
- Consumes: base `docker-compose.yml`의 `backend` 서비스 정의.
- Produces: `IMAGE_TAG` 환경변수로 버전을 지정하는 GHCR 이미지 참조 — Task 5의 SSH 스크립트가 `-f docker-compose.yml -f docker-compose.prod.yml` + `IMAGE_TAG=<버전>`으로 사용한다.

- [ ] **Step 1: 오버레이 파일 작성**

`docker-compose.prod.yml` 전체 내용:

```yaml
# 프로덕션 오버레이 — 서버에서 base와 함께 사용한다:
#   IMAGE_TAG=<버전> docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
# backend의 로컬 build 대신 GHCR에서 pull한 이미지를 쓴다.
# IMAGE_NAME은 배포 워크플로우가 export(ghcr.io/<owner>/<repo>) — 레포를 복사한 교회에서도 코드 수정 0.
# base의 build 키는 병합 시 남지만, pull된 이미지가 존재하는 한 up이 빌드를 시도하지 않는다
# (서버에는 소스가 없으므로 pull 실패 시 빌드도 실패 = 배포 실패가 조용히 숨지 않는다).
services:
  backend:
    image: ${IMAGE_NAME:-ghcr.io/church-template/church-backend}:${IMAGE_TAG:-latest}
```

> **최종 반영 노트(플랜 이후 수정분):** 이미지 참조는 위처럼 `IMAGE_NAME` 주입형이 최종이다(하드코딩이면 레포 복사 시 템플릿 이미지만 pull하는 split-brain — 최종 리뷰에서 수정). Task 5의 deploy.yml도 실제 구현은 다음이 다르다: ① ssh 스크립트에 `${{ }}` 직접 템플릿 대신 `envs: IMAGE_NAME,IMAGE_TAG`로 전달(명령 주입 방지), ② `packages: write`는 build-push job 레벨로 축소, ③ 모든 checkout에 `persist-credentials: false`, ④ `up -d` 후 `/actuator/health` 180초 헬스 게이트. 정본은 `.github/workflows/deploy.yml`과 스펙 §3.2를 참조.

- [ ] **Step 2: 병합 결과 검증 (docker CLI 필요, 데몬 불필요)**

```bash
IMAGE_TAG=9.9.9 docker compose -f docker-compose.yml -f docker-compose.prod.yml config | grep "image:"
```

Expected: `image: ghcr.io/church-template/church-backend:9.9.9` 가 backend에 표시 (postgres/redis 이미지 줄도 함께 출력됨).

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml config | grep "church-backend:"
```

Expected: `image: ghcr.io/church-template/church-backend:latest` (IMAGE_TAG 미지정 시 latest 폴백).

- [ ] **Step 3: 커밋**

```bash
git add docker-compose.prod.yml
git commit -m "ci : 프로덕션 compose 오버레이 추가(GHCR 이미지 참조) #${ISSUE}"
```

---

### Task 5: CD 워크플로우 (`deploy.yml`)

**Files:**
- Create: `.github/workflows/deploy.yml`

**Interfaces:**
- Consumes: Task 3의 Dockerfile(멀티아치), Task 4의 `docker-compose.prod.yml`, 템플릿 스크립트 `.github/scripts/version_manager.sh`(읽기만, 수정 금지), GitHub Secrets `DEPLOY_HOST`/`DEPLOY_USER`/`DEPLOY_SSH_KEY`.
- Produces: `deploy` push 시 GHCR push + SSH 배포. `workflow_dispatch`의 `image_tag` 입력 = 빌드 생략 재배포(롤백 경로).

- [ ] **Step 1: 워크플로우 파일 작성**

`.github/workflows/deploy.yml` 전체 내용:

```yaml
# CD — deploy 브랜치 push 시: GHCR 멀티아치 이미지 push → OCI VM에 SSH 배포.
# 배포는 항상 명시적 버전 태그로 pull한다(latest 아님) — 서버에 떠 있는 버전이 항상 추적 가능.
# workflow_dispatch의 image_tag 입력 = 해당 태그로 빌드 없이 재배포(롤백 경로).
# DEPLOY_HOST 시크릿이 비어 있으면 서버 배포는 건너뛰고 이미지 push까지만 수행한다
# (서버 준비 전에도 파이프라인이 그린 상태를 유지).
name: Deploy

on:
  push:
    branches: ["deploy"]
  workflow_dispatch:
    inputs:
      image_tag:
        description: "재배포할 이미지 태그 (비우면 새로 빌드해서 배포)"
        required: false
        default: ""

concurrency:
  group: deploy
  cancel-in-progress: false

permissions:
  contents: read
  packages: write

env:
  IMAGE_NAME: ghcr.io/${{ github.repository }}

jobs:
  build-push:
    # image_tag 지정 재배포(롤백) 시에는 빌드를 건너뛴다
    if: ${{ github.event_name == 'push' || inputs.image_tag == '' }}
    runs-on: ubuntu-latest
    outputs:
      version: ${{ steps.version.outputs.version }}
    steps:
      - uses: actions/checkout@v5

      - name: version.yml에서 버전 추출
        id: version
        run: |
          chmod +x .github/scripts/version_manager.sh
          VERSION=$(./.github/scripts/version_manager.sh get | tail -n 1)
          echo "version=$VERSION" >> "$GITHUB_OUTPUT"
          echo "이미지 버전: $VERSION"

      - uses: docker/setup-qemu-action@v3
      - uses: docker/setup-buildx-action@v3

      - name: GHCR 로그인
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: 멀티아치 이미지 빌드 & push
        uses: docker/build-push-action@v6
        with:
          context: .
          platforms: linux/amd64,linux/arm64
          push: true
          tags: |
            ${{ env.IMAGE_NAME }}:${{ steps.version.outputs.version }}
            ${{ env.IMAGE_NAME }}:${{ github.sha }}
            ${{ env.IMAGE_NAME }}:latest
          cache-from: type=gha
          cache-to: type=gha,mode=max

  deploy:
    needs: build-push
    # build-push가 skip된 경우(image_tag 재배포)에도 실행되어야 한다
    if: ${{ always() && (needs.build-push.result == 'success' || needs.build-push.result == 'skipped') }}
    runs-on: ubuntu-latest
    env:
      # image_tag 입력이 있으면 그 태그로, 없으면 방금 빌드한 버전으로 배포
      IMAGE_TAG: ${{ inputs.image_tag != '' && inputs.image_tag || needs.build-push.outputs.version }}
    steps:
      # 시크릿은 job 수준 if에서 참조할 수 없어 게이트 스텝으로 판별한다
      - name: 배포 가능 여부 확인
        id: gate
        env:
          DEPLOY_HOST: ${{ secrets.DEPLOY_HOST }}
        run: |
          if [ -n "$DEPLOY_HOST" ]; then
            echo "ready=true" >> "$GITHUB_OUTPUT"
          else
            echo "ready=false" >> "$GITHUB_OUTPUT"
            echo "::notice::DEPLOY_HOST 시크릿이 없어 서버 배포를 건너뜁니다(이미지 push까지만 수행)."
          fi

      - uses: actions/checkout@v5
        if: steps.gate.outputs.ready == 'true'

      - name: compose 파일 서버 동기화
        if: steps.gate.outputs.ready == 'true'
        uses: appleboy/scp-action@v1
        with:
          host: ${{ secrets.DEPLOY_HOST }}
          username: ${{ secrets.DEPLOY_USER }}
          key: ${{ secrets.DEPLOY_SSH_KEY }}
          source: "docker-compose.yml,docker-compose.prod.yml"
          target: /srv/church-backend

      - name: 서버 배포 (pull & up)
        if: steps.gate.outputs.ready == 'true'
        uses: appleboy/ssh-action@v1
        with:
          host: ${{ secrets.DEPLOY_HOST }}
          username: ${{ secrets.DEPLOY_USER }}
          key: ${{ secrets.DEPLOY_SSH_KEY }}
          script: |
            set -e
            cd /srv/church-backend
            export IMAGE_TAG="${{ env.IMAGE_TAG }}"
            docker compose -f docker-compose.yml -f docker-compose.prod.yml pull backend
            docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
            docker image prune -f
```

- [ ] **Step 2: YAML 문법 검증**

```bash
ruby -ryaml -e 'YAML.load_file(".github/workflows/deploy.yml"); puts "OK"'
```

Expected: `OK`.

- [ ] **Step 3: 액션 태그 존재 확인 (appleboy 액션 핀 검증)**

```bash
gh api repos/appleboy/ssh-action/tags --jq '.[].name' | head -5
gh api repos/appleboy/scp-action/tags --jq '.[].name' | head -5
```

Expected: 각각 `v1`대 태그가 목록에 존재. `v1`이 없으면 출력된 최신 안정 태그로 워크플로우의 `@v1`을 교체하고 다시 Step 2 실행.

- [ ] **Step 4: 커밋**

```bash
git add .github/workflows/deploy.yml
git commit -m "ci : deploy 브랜치 CD 워크플로우 추가(GHCR 멀티아치 push + SSH 배포) #${ISSUE}"
```

---

### Task 6: 서버 초기 세팅 가이드 (`docs/deploy-server-setup.md`)

**Files:**
- Create: `docs/deploy-server-setup.md`

**Interfaces:**
- Consumes: Task 4의 compose 오버레이 사용법, Task 5의 GitHub Secrets 3종.
- Produces: 교회별 VM 1회 세팅 체크리스트 (사람이 따라 하는 운영 문서).

- [ ] **Step 1: 문서 작성**

`docs/deploy-server-setup.md` 전체 내용:

````markdown
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
````

- [ ] **Step 2: 커밋**

```bash
git add docs/deploy-server-setup.md
git commit -m "docs : 배포 서버 초기 세팅 가이드 추가 #${ISSUE}"
```

---

### Task 7: PR 생성 + CI 실증 + 수동 후속 조치

**Files:**
- 없음 (push, PR, 운영 조치만)

**Interfaces:**
- Consumes: Task 2의 `CI` 워크플로우 — 이 PR 자체가 CI의 첫 실전 검증.

- [ ] **Step 1: push + PR 생성**

```bash
git push -u origin "20260706_#${ISSUE}_CICD_구축"
gh pr create --base main \
  --title "ci : GitHub Actions CI/CD 구축 (테스트 CI + GHCR/SSH 배포 CD) #${ISSUE}" \
  --body "$(cat <<'EOF'
## 요약
- `ci.yml`: main 대상 PR/push에서 `./gradlew build` (Testcontainers, 시크릿 0개)
- `deploy.yml`: deploy push 시 GHCR 멀티아치(amd64+arm64) 이미지 push → OCI VM SSH 배포. `image_tag` 수동 입력 = 롤백 경로. DEPLOY_HOST 시크릿 부재 시 서버 배포 스킵(이미지 push까지만)
- `Dockerfile`: 빌드 스테이지 `--platform=$BUILDPLATFORM` 고정 — 멀티아치 빌드에서 Gradle을 네이티브로 1회만 실행
- `docker-compose.prod.yml`: 서버용 GHCR 이미지 오버레이 (`IMAGE_TAG` 주입)
- `docs/deploy-server-setup.md`: 교회별 VM 1회 세팅 체크리스트 (OCI 2중 방화벽 포함)

## 설계 문서
docs/superpowers/specs/2026-07-05-cicd-github-actions-design.md

## 테스트 계획
- [ ] 이 PR에서 CI 워크플로우 그린 (ci.yml의 첫 실전 검증)
- [ ] 머지 후 main→deploy PR로 첫 CD 실행 → GHCR 멀티아치 매니페스트 확인 (`docker manifest inspect`)
- [ ] 서버 준비 후: `docker compose ps` healthy + `/actuator/health` 200
- [ ] 롤백 리허설: workflow_dispatch + image_tag로 직전 버전 재배포

## 수동 후속 조치 (머지 후)
- [ ] `gh workflow disable PROJECT-SPRING-GITHUB-PACKAGES-PUBLISH` — publishing 블록이 없어 deploy push마다 실패 예정인 라이브러리 배포용 템플릿 워크플로우 비활성화 (파일 무수정)
- [ ] VM 준비되면 docs/deploy-server-setup.md 수행 + Secrets 3종 등록
EOF
)"
```

Expected: PR URL 출력.

- [ ] **Step 2: PR에서 CI 그린 확인**

```bash
gh pr checks --watch
```

Expected: `CI / build` 성공. 실패 시 로그 확인(`gh run view --log-failed`) 후 수정 — Testcontainers 실패라면 러너 Docker 문제가 아니라 테스트 자체 문제일 가능성이 높으므로 로컬에서 `./gradlew test` 재현 먼저.

- [ ] **Step 3: 템플릿 publish 워크플로우 비활성화 (사용자 확인 후)**

```bash
gh workflow disable PROJECT-SPRING-GITHUB-PACKAGES-PUBLISH
gh workflow list --all | grep -i publish
```

Expected: 해당 워크플로우 `disabled_manually` 표시. **레포 상태를 바꾸는 조치이므로 실행 전 사용자에게 1회 확인.**

- [ ] **Step 4: 완료 보고**

머지·deploy 브랜치 생성·Secrets 등록은 사용자 결정 사항이므로 요청 대기. 남은 수동 체크리스트(PR 본문의 "수동 후속 조치")를 사용자에게 전달.

---

## 실행 노트

- 첫 deploy 실행은 서버/Secrets 없이도 그린이어야 정상 (build-push만 수행, deploy job은 게이트에서 스킵 notice).
- buildx GHA 캐시 덕에 두 번째 이후 이미지 빌드는 크게 단축된다. 첫 빌드는 QEMU 셋업 포함 10분 내외 예상.
- `deploy` 브랜치는 머지 후 `main`에서 분기해 생성한다: `git checkout -b deploy origin/main && git push -u origin deploy` (첫 push가 곧 첫 CD 실행).
