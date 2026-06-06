package com.elipair.church.domain.member;

import java.time.LocalDateTime;

/** 작성자 표시 해석용 인터페이스 프로젝션 — (id, name, deletedAt). soft-deleted도 조회해 마스킹 판정. */
public interface AuthorView {
    Long getId();

    String getName();

    LocalDateTime getDeletedAt();
}
