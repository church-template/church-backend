package com.elipair.church.domain.media;

import com.elipair.church.global.common.ContentRef;
import java.util.List;

/**
 * 특정 미디어를 참조하는 콘텐츠를 도메인별로 찾아 반환하는 SPI(스펙 §5.10 참조 추적).
 * 콘텐츠 도메인(설교·공지·이벤트·부서·갤러리·주보)이 각자 구현해 빈으로 등록하면,
 * MediaService가 모든 구현을 주입받아 결과를 합친다 — MediaService는 도메인을 몰라도 된다(content → media 단방향).
 * 두 질문을 나눠 묻는다(#95, 설계 6.6-2): "누가 쓰는가"(삭제 차단·참조 목록)와 "지금 누구나 볼 수 있는 글이 쓰는가"(공개 판단).
 * 하나로 합쳐 공개 조건을 넣으면 교인 전용 글이 쓰는 파일이 삭제 가능해지고, 안 넣으면 교인 전용 사진이 공개된다.
 */
public interface MediaReferenceProvider {

    /** 삭제 차단·참조 목록용 — 공개 범위 설정이나 글의 "교인만"으로 거르지 않고 모든 참조를 돌려준다. */
    List<ContentRef> findReferences(long mediaId);

    /** 지금 누구나 볼 수 있는 글(메뉴 설정·글의 "교인만" 반영)이 이 미디어를 쓰는가. 새 provider는 기본 false(비공개 쪽으로 안전). */
    default boolean hasPublicReference(long mediaId) {
        return false;
    }
}
