# 회원 이름·전화번호 검색 — 설계 (2026-06-18)

## 배경 / 문제

관리자 회원 조회 `GET /api/members`(`MEMBER_MANAGE`)는 전체 회원을 가입순으로 페이지네이션만 한다. 이름·전화번호로 특정 회원을 찾는 수단이 없어, 회원 수가 늘면 관리자가 가입 승인·역할 부여 대상을 페이지를 넘겨가며 눈으로 찾아야 한다.

전화번호 정확 일치 조회(`findByPhoneAndDeletedAtIsNull`)는 로그인·중복확인 전용 단건 lookup일 뿐, 관리 화면용 부분검색이 아니다.

스펙 §5.2에는 회원 검색이 설계돼 있지 않다. 즉 이건 스펙 갭 보완이 아니라 **신규 기능**이며, 스펙의 다른 도메인(설교 §5.5, 공지 §5.7)이 쓰는 `q` 검색 관행을 회원에 적용한다.

## 범위

- 대상: `GET /api/members` 한 엔드포인트에 `q` 검색어 파라미터 추가.
- 비대상: 다른 회원 엔드포인트, 신규 인덱스/마이그레이션, 응답 형식 변경, 정렬 기본값 변경.

## API 계약

```text
GET /api/members?q={검색어}&page=&size=&sort=    (권한: MEMBER_MANAGE)
```

- `q`: 선택(`@RequestParam(required = false) String q`).
- `q`가 없거나 공백 → 기존 전체 목록과 **동일 동작**(회귀 없음).
- 응답: 기존 `Page<MemberCardResponse>` 그대로. 새 필드 없음. 페이지네이션·정렬 envelope 불변.

검색 의미:
- `q`가 이름의 부분 문자열이면 매칭(대소문자 무시).
- `q`에 숫자가 있으면 그 숫자열이 전화번호(숫자만 정규화 저장)의 부분 문자열일 때 매칭.
- 두 조건은 OR. 예: `q=김철수`(이름 매칭), `q=010-1234`(`0101234`로 정규화 후 전화 매칭).

## 검색 술어 — `MemberSpecifications.filter(q)`

기존 `*Specifications` 패턴을 그대로 따른다(`SermonSpecifications`, `NoticeSpecifications` 참고): 순수 조건 빌더, 항상 미삭제, null/blank 인자는 술어 제외.

```text
deletedAt IS NULL                              (항상)
+ q 비어있지 않으면 OR(
      lower(name)  LIKE %lower(q)%,            (이름 부분일치, 대소문자 무시)
      phone        LIKE %digits(q)%            (digits(q)가 비어있지 않을 때만 추가)
  )
```

- `digits(q)`는 `q`에서 숫자만 추출한 값. **`q`가 순수 이름이면(숫자 0개) 전화 술어를 아예 추가하지 않는다** → 이름 검색이 전화 컬럼에 헛매칭되는 일 없음.
- `phone`은 숫자만 저장(`01012345678`)이라 `lower()` 불필요.
- 검색어(`q`)는 앞뒤 공백을 트림한 값으로 매칭한다(이름 LIKE·전화 숫자 추출 공통) — `" 철수 "` 입력도 매칭.
- `taggedIds` 등 다른 필터는 회원엔 없으므로 인자는 `q` 하나.

## 전화번호 정규화 재사용 (작은 리팩터)

현재 `PhoneNumbers.normalize()`는 숫자가 없으면 `BusinessException`을 던진다 → 검색용으로 직접 못 쓴다(이름-only `q`에서 예외).

- `PhoneNumbers`에 비예외 헬퍼 `extractDigits(String raw) → String`(숫자만, 없으면 `""`)를 추가한다.
- 기존 `normalize()`는 `extractDigits()` 결과를 받아 빈값이면 기존 예외를 던지도록 내부만 정리한다. **공개 시그니처·동작 불변**(DRY).
- `extractDigits(null)` → `""`(검색 경로에선 q가 null/blank면 술어 자체를 안 만들므로 안전).

## 서비스 흐름 — `MemberService.list`

```text
list(String q, Pageable p):
  q == null || q.isBlank()
      → memberRepository.findByDeletedAtIsNull(p)        // 기존 경로(position fetch-join 유지)
  else
      → memberRepository.findAll(MemberSpecifications.filter(q), p)   // 검색 경로
  → .map(MemberCardResponse::from)
```

- **블랭크 경로는 손대지 않는다** → 평상시 목록 브라우징의 동작·성능(position fetch-join)을 100% 보존(회귀 0).
- 검색 경로는 결과가 보통 소수라 position lazy-load N+1이 무해(기존 roles N+1을 "교회 규모에서 무해"로 둔 판단과 같은 선상).
- `MemberRepository extends JpaSpecificationExecutor<Member>`를 추가한다(`findAll(Specification, Pageable)` 제공).

## 인덱스 / 마이그레이션

`LIKE '%...%'`는 선행 와일드카드라 btree 인덱스를 못 쓴다 → seq scan. 교회 규모(수백~수천 행)에서 무해.

- **새 인덱스·Flyway 마이그레이션 없음** → `MigrationIndexTest`에 추가 항목 없음. 스코프 최소.

## 에러 처리

- 신규 에러 코드 없음.
- `q`는 검증 실패 케이스가 없다(빈 문자열 = 전체 목록).
- 권한 미달은 기존 `@PreAuthorize("hasAuthority('MEMBER_MANAGE')")`가 403 `ACCESS_DENIED`로 처리.

## 테스트 (TDD, RED → GREEN)

1. `PhoneNumbers.extractDigits` 단위 테스트
   - `"010-1234-5678"` → `"01012345678"`, `"김철수"` → `""`, `null` → `""`, 공백·특수문자 제거.
   - 기존 `normalize()` 동작 회귀(숫자 없음 → 예외) 유지 확인.
2. Specification/Repository 슬라이스(`@DataJpaTest`, 회원 몇 건 시드)
   - 이름 부분일치(대소문자 무시) 매칭.
   - 하이픈 포함 전화 `q`가 정규화 후 매칭.
   - 이름-only `q`는 전화 술어 스킵(전화 헛매칭 없음).
   - blank/null `q` → 전체(미삭제) 반환.
   - 소프트 삭제 회원은 검색 결과 제외.
   - 페이지네이션 동작 보존.
3. 컨트롤러 슬라이스(webmvc)
   - `?q=` 파라미터 배선(서비스에 전달) 확인.
   - `MEMBER_MANAGE` 미보유 시 403.

## 변경 파일 요약

- `MemberSpecifications.java` (신규) — `filter(q)` 조건 빌더.
- `MemberRepository.java` — `JpaSpecificationExecutor<Member>` 상속 추가.
- `MemberService.java` — `list(q, pageable)` 분기.
- `MemberQueryController.java` — `@RequestParam(required=false) String q` 추가, 서비스로 전달.
- `PhoneNumbers.java` — `extractDigits` 추가 + `normalize` 내부 정리.
- 테스트 3종(위 TDD 항목).
