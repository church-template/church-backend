package com.elipair.church.global.common;

/**
 * 콘텐츠 한 건을 가리키는 중립 참조(type=도메인 종류, id=대상 id, title=표시 제목).
 * 미디어 참조 추적(스펙 §5.10)에서 {@code MediaReferenceProvider} 반환·/references 응답·
 * MEDIA_IN_USE 에러 봉투가 공유한다. global에 둬야 ErrorResponse(global)가 domain에 의존하지 않는다.
 */
public record ContentRef(String type, Long id, String title) {}
