package com.elipair.church.domain.tag.dto;

import com.elipair.church.domain.tag.Tag;

/** 태그 응답(id, name). GET 목록·관리자 응답·콘텐츠 tags:[] 임베드 공용. */
public record TagResponse(Long id, String name) {

    public static TagResponse from(Tag tag) {
        return new TagResponse(tag.getId(), tag.getName());
    }
}
