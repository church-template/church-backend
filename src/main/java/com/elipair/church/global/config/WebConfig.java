package com.elipair.church.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode;

/**
 * 목록 응답 표준화. VIA_DTO 모드로 컨트롤러의 Page&lt;T&gt; 반환을 Spring Data PagedModel JSON
 * ({content, page:{size,number,totalElements,totalPages}}, 스펙 §5)으로 직렬화한다.
 * 컨트롤러가 없는 본 이슈에선 규약만 고정하고, 실제 동작은 첫 도메인 컨트롤러(D 이슈)에서 발현된다.
 */
@Configuration(proxyBeanMethods = false)
@EnableSpringDataWebSupport(pageSerializationMode = PageSerializationMode.VIA_DTO)
public class WebConfig {}
