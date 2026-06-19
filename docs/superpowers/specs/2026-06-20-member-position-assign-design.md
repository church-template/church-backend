# 설계: 회원 직분 부여·해제 API

## 1. 개요

관리자가 특정 회원에게 직분(목사·집사 등)을 지정/변경/해제하는 API를 추가한다.

현재 직분 카탈로그 CRUD(`/api/positions`, `/api/admin/positions`, `POSITION_MANAGE`)는 있으나, 회원에게 직분을 지정하는 경로가 없다. 회원 직분은 개발 시드 SQL의 `position_id`로만 채워지고, 가입(signup)은 직분 null이며, 관리자 회원수정(`AdminMemberUpdateRequest`)에도 `positionId`가 없다. 엔티티에 `Member.changePosition(Position)` 메서드는 있으나 호출하는 곳이 없는 미배선 상태다. 이번 작업은 그 메서드를 전용 엔드포인트로 연결한다.

핵심 전제: 직분(position)은 권한(RBAC)과 완전히 분리된 축이다. 직분은 어떤 인가도 부여하지 않으므로, 직분 부여에는 위계(priority) 검증이 필요 없다.

## 2. 엔드포인트 계약

```
PUT /api/admin/members/{uuid}/position          @PreAuthorize("hasAuthority('MEMBER_MANAGE')")
Request body:  PositionAssignRequest { Long positionId }   // nullable: null이면 직분 해제
Response: 200 MemberDetailResponse                          // 변경된 직분이 반영된 회원 상세
```

- 메서드 `PUT`: "회원의 직분"이라는 단일 값을 통째로 설정(교체)하는 멱등 연산. `positionId: null`은 명시적 해제를 의미하므로 PATCH 부분수정의 null 모호성이 없다.
- 경로는 회원 관리 영역(`/api/admin/members/{uuid}/...`)에 둔다 — 역할 부여/회수(`/{uuid}/roles`), 비밀번호 초기화(`/{uuid}/reset-password`)와 동일한 per-action 패턴.
- 인가: `MEMBER_MANAGE`. 직분 지정은 "회원의 속성 편집"이며, `POSITION_MANAGE`는 직분 카탈로그(종류) 관리 전용이라 의미가 다르다.

## 3. 컴포넌트

| 요소 | 변경 | 비고 |
|---|---|---|
| `dto/PositionAssignRequest` | 신규 — `Long positionId` (nullable, `@NotNull` 없음) | null = 해제 허용 |
| `controller/MemberAdminController` | `PUT /{uuid}/position` 핸들러 추가 | `MemberService` 이미 주입됨 |
| `MemberService.changePosition(UUID uuid, Long positionId)` | 신규 메서드, `MemberDetailResponse` 반환 | `PositionRepository` 의존성 추가 |
| `Member.changePosition(Position)` | 기존 메서드 재사용 | 미배선 데드코드 → 연결 |

의존성 방향: `Member` 엔티티가 이미 `domain.position.Position`을 참조하므로, `MemberService`가 `PositionRepository`를 직접 주입하는 것은 기존 도메인 결합 범위 내다(별도 cross-service 우회 불필요).

## 4. 서비스 로직 / 데이터 흐름

```
@Transactional
changePosition(uuid, positionId):
  member = findActive(uuid)                         // 미존재/탈퇴 → 404 RESOURCE_NOT_FOUND
  position = (positionId == null)
               ? null
               : positionRepository.findById(positionId)
                   .orElseThrow(() -> RESOURCE_NOT_FOUND)   // 미존재 직분 → 404
  member.changePosition(position)                   // null이면 직분 해제
  return MemberDetailResponse.from(member)          // 트랜잭션 flush로 직분 반영
```

`findActive(uuid)`는 기존 `MemberService`의 미삭제 회원 조회 헬퍼를 재사용한다.

## 5. 가드 / 에러 처리

- 위계(priority) 검증 없음 — 직분은 권한과 분리되어 인가 영향이 0이다. 역할 부여의 `validateGrantable`류는 적용하지 않는다.
- 자기 자신 직분 변경 허용 — 직분은 권한이 아니므로 self-protection 불필요(역할의 `validateNotSelf`와 대비).
- 에러 매핑(기존 RFC 7807 envelope 재사용):
  - 회원 미존재/탈퇴 → `404 RESOURCE_NOT_FOUND`
  - positionId 미존재 → `404 RESOURCE_NOT_FOUND`
  - 미인증 → `401`, `MEMBER_MANAGE` 미보유 → `403`(기존 시큐리티/`@PreAuthorize`)

## 6. 테스트 (TDD)

통합(MockMvc + Testcontainers, `MemberAdminApiTest` 또는 신규 테스트):
- 직분 부여 → 200, `MemberDetailResponse.position`에 직분명 반영
- 직분 변경(다른 직분으로) → 200, 새 직분 반영
- 직분 해제(`positionId: null`) → 200, `position` null
- 미존재 positionId → 404 `RESOURCE_NOT_FOUND`
- 미존재/탈퇴 회원 uuid → 404 `RESOURCE_NOT_FOUND`
- `MEMBER_MANAGE` 없는 토큰 → 403
- 자기 자신 직분 변경 허용 → 200 (직분은 권한과 무관함을 박제)

## 7. 문서

- `docs/church-backend-spec.md` §5.2(회원 관리)에 엔드포인트 추가.
- `MemberAdminController`의 `@Operation` 설명 작성(인증·경로변수·요청본문·반환값·부수효과).

## 8. 범위 외 (YAGNI)

- 회원수정 DTO(`AdminMemberUpdateRequest`)에 positionId 병합하지 않음 — 전용 엔드포인트로 분리(null 의미 명확).
- 직분 부여 이력/감사 로그 별도 테이블 없음 — 기존 BaseEntity 감사 범위로 충분.
- 직분에 따른 권한 자동 부여 없음 — 직분과 권한은 분리 원칙 유지.
