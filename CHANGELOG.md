# Changelog

**현재 버전:** 0.0.40  
**마지막 업데이트:** 2026-07-20T07:23:36Z  

---

## [0.0.40] - 2026-07-20

**PR:** #58  

**기타**
- Merge pull request #57 from church-template/20260720_#56_AI_에이전트_개발자_온보딩_문서_체계_구축
- fix : CodeRabbit 반영 — 시드 정리 조건 9000~9999 한정(실데이터 보호)·플랜 Expected 정합 #56
- docs : CLAUDE.md 스테일 단락 현행화 — early scaffold·미배선 목록 정정 (최종 리뷰 반영) #56
- docs : README 신설(문서 지도) + CLAUDE.md에 setup 문서 링크 추가 #56
- docs : AI 공용 진입점 AGENTS.md 신설 (불변식 요약·읽기 순서·명령어) #56
- docs : 새 교회 배포 정본 문서 setup-new-church.md 신설 (코드 수정 0 원칙) #56
- docs : 로컬 개발 정본 문서 setup-dev.md 신설 (오버라이드·시드·검증·함정) #56
- docs : 경로 인가 5단·신규 도메인 현행화 (CLAUDE.md·rbac 규칙 동기화) #56
- docs : 스펙 §6 정정 — override는 처음부터 미추적(오독), Task 1은 no-op #56
- Merge branch 'main' of https://github.com/church-template/church-backend
- docs : AI 온보딩 문서 체계 구현 플랜

---

## [0.0.38] - 2026-07-20

**PR:** #55  

**기타**
- Merge branch 'main' of https://github.com/church-template/church-backend
- docs : AI 온보딩 문서 체계 설계 스펙 (AGENTS.md·README·setup 문서 + 기존 문서 동기화)
- chore : 필요 파일 생성
- Merge pull request #54 from church-template/20260719_#53_설교_회원전용_전환
- docs : 설교 회원전용 전환 스펙·플랜(+/api/main 공개 유지 결정 명시) #53
- test : /api/main 클릭스루 상세(/api/sermons/{id}) 회원전용 차단 검증 추가 #53
- feat : 설교 조회를 SERMON_VIEW 회원전용으로 게이트 #53
- feat : 설교 회원전용용 SERMON_VIEW 권한 신설·시드 #53

---

## [0.0.35] - 2026-07-16

**PR:** #52  

**기타**
- docs : 배포 가이드에 Caddy HTTPS 리버스 프록시 섹션 추가
- fix : 리버스 프록시 뒤 https 인식 위해 forward-headers-strategy 설정

---

