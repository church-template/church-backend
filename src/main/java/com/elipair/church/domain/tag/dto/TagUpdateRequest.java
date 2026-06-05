package com.elipair.church.domain.tag.dto;

import jakarta.validation.constraints.Size;

/** 태그 수정 요청. name null이면 미변경. */
public record TagUpdateRequest(@Size(max = 50) String name) {}
