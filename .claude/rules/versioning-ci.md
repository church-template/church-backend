# Versioning & CI (SUH-DEVOPS-TEMPLATE)

This repo is wired to the SUH-DevOps template. Version and changelog files are **automation-owned** — hand-editing them fights the workflows.

## Do NOT hand-edit

- `version.yml` — the version source of truth (synced to `build.gradle` by `.github/scripts/version_manager.sh`).
- `build.gradle`'s `version` line.
- `CHANGELOG.md` / `CHANGELOG.json`.
- `.github/workflows/PROJECT-COMMON-*` and `.github/scripts/*` — treat as template-managed; don't touch during feature work.

## How versioning happens automatically

- **Push to `main`** → `PROJECT-COMMON-VERSION-CONTROL` auto-bumps the **patch** version and commits it with `[skip ci]`. (`version.yml`, `CHANGELOG.*`, `build.gradle` version are path-ignored to avoid loops.)
- `minor` / `major` bumps are manual edits to `version.yml` — only when explicitly intended.
- So: just commit your feature changes; **don't** also bump the version yourself, or you'll collide with the bot.

## Other automation

- **Publish to GitHub Packages**: push to the **`deploy`** branch or a `v*.*.*` tag (`PROJECT-SPRING-GITHUB-PACKAGES-PUBLISH`).
- **Changelog control**: runs on PRs opened against **`deploy`**.
- **CodeRabbit** auto-reviews PRs to `main` in Korean (`ko-KR`, `.coderabbit.yaml`).

## Commits

`<type> : <description>` (콜론 앞 공백은 저장소 히스토리 관행; feat/fix/refactor/docs/test/chore/perf/ci), in Korean to match history. Commit/push only when asked.
