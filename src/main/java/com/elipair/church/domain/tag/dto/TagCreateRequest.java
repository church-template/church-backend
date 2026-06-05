package com.elipair.church.domain.tag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 태그 생성 요청. @Size(max=50)이 1차 방어선 — varchar(50) 초과가 409로 오분류되는 걸 막는다(RoleCreateRequest 패턴). */
public record TagCreateRequest(@NotBlank @Size(max = 50) String name) {}
