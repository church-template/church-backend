package com.elipair.church.domain.tag;

/**
 * content_tags가 가리키는 콘텐츠 도메인 종류. 코드-facing 키라 영어 대문자(스펙 §4.4).
 * content_tags.resource_type에 enum 이름 그대로 저장된다(@Enumerated STRING).
 */
public enum ContentResourceType {
    SERMON,
    NOTICE,
    EVENT,
    GALLERY_ALBUM
}
