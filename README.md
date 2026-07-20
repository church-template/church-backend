# church-backend

교회 홈페이지용 **재사용 템플릿 백엔드**. 코드는 한 교회 기준으로 깨끗하게 유지하고,
교회별 차이(이름·도메인·시크릿)는 전부 `.env`로 주입한다. 새 교회 추가 = 레포 복사 + `.env` 작성 + 배포 —
**코드 수정 0**. 멀티테넌시는 의도적으로 없다(교회당 별도 DB·별도 인스턴스).

**스택**: Spring Boot 4.0.x · Java 21 · PostgreSQL 16 · Redis 7 · Flyway · Docker Compose · GitHub Actions(GHCR)

## 문서 지도

| 목적 | 문서 |
|---|---|
| 로컬 개발 시작 (clone → 실행 → 테스트) | [docs/setup-dev.md](docs/setup-dev.md) |
| 새 교회 인스턴스 배포 | [docs/setup-new-church.md](docs/setup-new-church.md) |
| 서버 프로비저닝 (OCI VM, 1회) | [docs/deploy-server-setup.md](docs/deploy-server-setup.md) |
| 전체 설계 스펙 (정본, 한국어) | [docs/church-backend-spec.md](docs/church-backend-spec.md) |
| AI 에이전트 진입점 | [AGENTS.md](AGENTS.md) (Claude Code는 `CLAUDE.md`) |

## 빠른 시작

```bash
docker compose up -d --build
curl -s localhost:8080/actuator/health   # {"status":"UP"}
```

목데이터·시드 계정·검증 절차는 [docs/setup-dev.md](docs/setup-dev.md) 참조.

## License

[LICENSE](LICENSE)
