package com.elipair.church.domain.tag;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 콘텐츠 ↔ 태그 다형 연결(순수 조인, 복합키 외 컬럼 없음). */
@Entity
@Table(name = "content_tags")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentTag {

    @EmbeddedId
    private ContentTagId id;

    public ContentTag(ContentTagId id) {
        this.id = id;
    }
}
