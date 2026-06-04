package com.elipair.church.global.storage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 파일 저장 설정(스펙 §8·§10). uploadDir=저장 루트, maxSize=업로드 최대 바이트.
 * baseUrl(공개 URL 베이스)은 G4에서는 바인딩/검증만 하며 LocalFileStorage 동작에는 관여하지 않는다(media 도메인이 소비).
 * @Validated로 기동 시 fail-fast(빈 경로·0/음수 한도 거부) — application.yml 기본값이 있어 정상 배포는 통과한다.
 */
@Validated
@ConfigurationProperties(prefix = "file")
public record FileProperties(
        @NotBlank String uploadDir,
        @NotBlank String baseUrl,
        @Positive long maxSize) {}
