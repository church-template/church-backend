# 차량 탑승 신청 — 픽업 위치 좌표(위경도) 설계

- 날짜: 2026-07-22
- 상태: 승인됨 (프론트 핸드오프 스펙 그대로)
- 이슈: #65 / 선행: #62(차량운행 도메인, main 머지)
- 관련 프론트 이슈: #114 (프론트 저장소)

## 배경 / 목표

탑승자가 픽업 장소를 **한 번의 조작으로** 정확히 전달하게 한다. 프론트가 브라우저 위치(geolocation)로 현재 좌표를 얻어 신청에 첨부하고, 기사용 명단에서 그 좌표로 지도 핀을 연다(지도 링크는 프론트가 좌표로 조립 — 지도 SDK·API 키 불필요).

백엔드는 **좌표를 저장하고 그대로 돌려주기만** 한다. 프론트가 지도 SDK를 도입하지 않으므로(교회별 템플릿 재사용성 유지) 백엔드도 역지오코딩(좌표→주소 변환)을 하지 않는다. 좌표는 원본 그대로 저장·반환한다.

## 변경 요약

1. `vehicle_requests` 테이블에 nullable 좌표 컬럼 2개 추가 + `pickup_location` NOT NULL 완화. **V16은 이미 main 머지이므로 V17 ALTER 마이그레이션으로 얹는다**(기존 행은 좌표 NULL·백필 불요).
2. 신청 요청/응답·내 신청·명단 응답 스키마에 `latitude`·`longitude` 추가.
3. `pickupLocation`을 **필수 → 선택**으로 완화하고, "픽업 텍스트와 좌표 중 최소 하나" 규칙 추가.

신규 엔드포인트 없음. 권한 변경 없음(좌표는 기존 엔드포인트·기존 권한에 편승). 좌표는 개인 위치정보라 명단 응답의 기존 `VEHICLE_MANAGE` 게이트로 이미 보호된다.

## 데이터 모델 (V17 ALTER)

`vehicle_requests` 테이블:

| 컬럼 | 변경 | 제약 |
|---|---|---|
| `pickup_location` | NOT NULL **제거** | NULL 허용 (VARCHAR(200) 유지) |
| `latitude` | **추가** | `DOUBLE PRECISION` NULL 허용 |
| `longitude` | **추가** | `DOUBLE PRECISION` NULL 허용 |

- 기존 행은 두 좌표 모두 NULL, `pickup_location`은 값 보유(하위호환 — 백필 불요).
- 좌표는 항상 **쌍**으로 저장한다(둘 다 있거나 둘 다 NULL). 아래 검증 규칙으로 보장. DB CHECK 제약은 두지 않고 애플리케이션 검증으로 통일한다(도메인 검증 계층 관례 — event 좌표성 검증 선례).

## API 계약 변경

경로·메서드·상태코드는 그대로. 아래 스키마에 필드만 추가하고 `pickupLocation` 필수 여부만 바꾼다.

### 응답의 좌표 직렬화 정책 (핸드오프 스펙 정정)

핸드오프는 `latitude`·`longitude`가 "값 없으면 키 생략(`@JsonInclude(NON_NULL)`) — 기존 `note`·`email`과 동일"이라 적었으나, **이 저장소에는 전역 NON_NULL 설정이 없어 실제로는 `note`·`email` null도 `"note": null`로 직렬화된다**(#50 교훈: 응답 null 필드는 JSON에 그대로 포함). 따라서 "note와 동일"의 실제 의미는 **null로 직렬화(생략 아님)**이다.

- **결정**: lat/lng에 별도 `@JsonInclude`를 두지 않는다. 같은 DTO 안에서 `note`와 동일하게 null로 직렬화되어 일관성을 유지한다. 프론트의 `latitude != null` 판정은 null이든 키 부재든 동일하게 동작하므로 관찰 계약은 동일하다.

### 1. `VehicleRequestCreateRequest` (요청 본문 — 탑승 신청)

`POST /api/vehicle-runs/{id}/requests`

```
{
  "pickupLocation": "string, 선택, 최대 200자",   // 필수(@NotBlank) → 선택 으로 변경
  "note":           "string, 선택",
  "latitude":       "number(double), 선택, -90 ~ 90",     // 신규
  "longitude":      "number(double), 선택, -180 ~ 180"    // 신규
}
```

검증 규칙(위반 시 `400 INVALID_INPUT_VALUE`) — 레코드 `@AssertTrue`로 구현(event `EventCreateRequest` 교차검증 선례):
- **좌표 동반 필수**: `latitude`와 `longitude`는 둘 다 있거나 둘 다 없어야 한다(한쪽만 오면 거부).
- **최소 하나**: `pickupLocation`(공백 제외 유효 텍스트)과 좌표(쌍) 중 최소 하나를 포함해야 한다(둘 다 비면 거부).
- **범위**: `latitude` ∈ [-90, 90], `longitude` ∈ [-180, 180]. 한국 영역으로 좁히지 않는다(GPS 오차·경계 방어). Double은 `@DecimalMin/@Max`가 미지원이라 `@AssertTrue`에서 범위 검사.
- `pickupLocation` 길이 제약(`@Size(max=200)`)은 유지.

> `pickupLocation`을 선택으로 바꾸는 이유: "현재 위치 첨부" 원탭 흐름에서 프론트는 좌표만 보내고 텍스트를 채우지 않는다(SDK 없이 역지오코딩 불가). "최소 하나" 규칙으로 빈 신청을 막는다.

**공백 픽업 처리**: `pickupLocation`이 공백만(`"  "`)인 경우 "최소 하나" 판정에서 **미존재로 취급**한다. 서비스는 공백 픽업을 저장 전 `null`로 정규화한다(공백 문자열 저장 방지). 이로써 기존 테스트 `apply_blank_pickup_location_is_400`은 "픽업 공백 + 좌표 없음 → 400"으로 결과가 유지된다(근거는 @NotBlank가 아니라 "최소 하나" 규칙으로 이동).

### 2. `VehicleRequestResponse` (신청 응답)

```
{ "id", "runId", "pickupLocation": string|null, "note": string|null,
  "latitude": number|null, "longitude": number|null }   // 좌표 신규
```

### 3. `MyRequestResponse` (`GET /api/vehicle-runs` 응답의 `content[].myRequest`)

```
{ "pickupLocation": string|null, "note": string|null,
  "latitude": number|null, "longitude": number|null }   // 좌표 신규
```

(프론트가 내 신청 카드에서 "내가 첨부한 위치 보기" 링크를 좌표로 조립.)

### 4. `VehicleRosterEntryResponse` (`GET /api/admin/vehicle-runs/{id}/requests` 응답의 `content[]`)

```
{ "name", "phone": string|null, "pickupLocation": string|null, "note": string|null,
  "requestedAt": date-time, "latitude": number|null, "longitude": number|null }   // 좌표 신규
```

(기사가 좌표가 있으면 정확한 핀으로 지도를 연다.)

## 검증 예시 (요청 → 결과)

| 요청 본문 | 결과 |
|---|---|
| `{pickupLocation:"OO아파트 정문"}` | 201 (텍스트만 — 기존 동작) |
| `{latitude:37.5, longitude:127.0}` | 201 (좌표만 — 원탭) |
| `{pickupLocation:"정문", latitude:37.5, longitude:127.0}` | 201 (둘 다) |
| `{note:"2명"}` (텍스트·좌표 없음) | 400 INVALID_INPUT_VALUE |
| `{latitude:37.5}` (경도 누락) | 400 INVALID_INPUT_VALUE |
| `{pickupLocation:"정문", latitude:200, longitude:127}` | 400 INVALID_INPUT_VALUE |

## 바뀌지 않는 것 (Non-goals)

- 신규 엔드포인트 없음. 권한·게이트 변경 없음(`VEHICLE_APPLY` 신청, `VEHICLE_MANAGE` 명단 그대로).
- 역지오코딩·주소 정규화 없음 — 백엔드는 좌표를 원본 그대로 저장·반환.
- 정확도(accuracy)·좌표 출처·타임스탬프 등 부가 메타 저장 안 함.
- 신청 수정 API 없음(취소 후 재신청 — 기존 정책 유지).
- `docs/api-docs.json`은 이 저장소 산출물이 아님(OpenAPI는 런타임 `/v3/api-docs` 제공, 프론트 소비) — 갱신 대상 아님.
- 권한 시드 무변경 → 시드 하드코딩 테스트(RbacSeedIntegrity·PermissionApi·RoleApi·MeApi) 갱신 없음. 신규 인덱스 없음 → MigrationIndexTest 갱신 없음.

## 프론트 후속(참고 — 백엔드 작업 아님)

- 신청 다이얼로그 "현재 위치 첨부" 버튼(브라우저 geolocation) → `latitude`/`longitude` 전송.
- 명단·내 신청 카드에서 좌표가 있으면 지도 링크(카카오맵 URL 스킴, 키 불필요)로 핀 열기.
- PC 위치 부정확 안내(모바일 권장) — 백엔드 무관.
