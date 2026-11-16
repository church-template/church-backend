package com.elipair.church.domain.media;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** accessEnforced=true면 공개 근거 없는 미디어를 서명 없이 요청할 때 404로 막는다(기본 false = 전환 기간 동작 유지). */
@ConfigurationProperties("media")
public record MediaProperties(boolean accessEnforced) {}
