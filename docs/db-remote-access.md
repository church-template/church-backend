# 배포 DB 원격 접속 (SSH 터널)

배포 서버의 PostgreSQL에 로컬 DB 툴(DBeaver, IntelliJ, psql 등)로 붙는 방법.

## 전제 — 왜 SSH 터널이 필요한가

- 배포 `docker-compose.yml`에서 **postgres는 호스트에 포트를 발행하지 않는다** (backend 8080만 발행, postgres/redis는 도커 네트워크 내부 전용). 인터넷에 5432가 열려 있지 않다 — 의도된 설계.
- `api.<도메인>`(Caddy HTTPS 리버스 프록시)은 **백엔드 REST API(8080)**용이다. DB는 PostgreSQL 와이어 프로토콜(TCP 5432)이라 이 프록시로는 못 붙는다.
- 따라서 접속은 **SSH 터널**로만 한다. 터널 접속지(SSH Host)는 GitHub Actions `DEPLOY_HOST` 시크릿과 **같은 서버**다(배포도, DB 접속도 같은 VM).

## 1회 서버 설정 — postgres를 loopback에만 발행

배포 compose는 postgres 포트를 안 열어두므로, 서버에서 override로 **`127.0.0.1`에만** 바인딩한다. 인터넷 노출 없이 SSH 터널로만 닿게 하는 게 목적.

```bash
ssh -i <SSH_KEY_FILE> <SSH_USER>@<DEPLOY_HOST>
cd /srv/church-backend

# override 생성 (한 줄, 들여쓰기 깨질 일 없음)
printf 'services:\n  postgres:\n    ports:\n      - "127.0.0.1:5432:5432"\n' > docker-compose.override.yml
cat docker-compose.override.yml   # 4줄 YAML 확인

docker compose up -d --force-recreate postgres
docker compose ps postgres
```

`ps`의 PORTS가 **`127.0.0.1:5432->5432/tcp`**로 바뀌면 성공.
(데이터는 named volume `postgres-data`에 있어 재생성돼도 유실 없음. 백엔드는 잠깐 재연결만 함.)

> `docker-compose.override.yml`은 gitignore 대상이라 레포엔 안 올라간다 — 서버에만 존재하는 로컬 오버레이다.

## 2. DBeaver 연결 설정

**SSH 탭** (Use SSH Tunnel 체크):

| 항목 | 값 |
|---|---|
| Host/IP | `<DEPLOY_HOST>` (= Actions `DEPLOY_HOST` 시크릿, 예: `api.<도메인>`도 같은 서버) |
| Port | `22` |
| User Name | `<SSH_USER>` (= `DEPLOY_USER` 시크릿; Ubuntu 이미지 `ubuntu`, Oracle Linux `opc`) |
| Authentication | Public Key |
| Private Key | OCI 인스턴스 키 파일 (`.key`) |
| Passphrase | 키에 암호 안 걸렸으면 **공란** |

**메인(PostgreSQL) 탭** — 주소는 SSH 서버 호스트 관점:

| 항목 | 값 | 비고 |
|---|---|---|
| Host | `localhost` | `.env`의 `postgres`(컨테이너 이름) 아님 — 터널이 서버 호스트에 도착해 발행된 loopback 포트로 붙기 때문 |
| Port | `5432` | |
| Database | 서버 `.env`의 `DB_NAME` | |
| Username | 서버 `.env`의 `DB_USERNAME` | |
| Password | 서버 `.env`의 `DB_PASSWORD` | 저장소·문서에 남기지 말 것 |

Test Connection → 성공하면 완료.

## 값의 출처

민감정보는 문서에 두지 않는다. 실제 값 위치:

- **SSH Host/User/Key**: GitHub Actions 시크릿 `DEPLOY_HOST` / `DEPLOY_USER`, OCI 인스턴스 SSH 키 파일.
- **DB 이름/계정/비번**: 서버 `/srv/church-backend/.env` (`grep -E '^DB_' .env`).

## 주의

- override는 `127.0.0.1` 바인딩이라 인터넷엔 안 열린다. `0.0.0.0`으로 바꾸지 말 것.
- **운영 DB**다 — 조회 위주로 쓰고 수정/삭제는 신중히.
- 목데이터(id ≥ 9000)는 **dev 프로필에서만** 시드되므로 운영엔 없다. 실데이터만 보인다.
- DB 비번이 화면·로그에 노출됐다면 `.env` 갱신 후 `docker compose up -d`로 로테이션.
