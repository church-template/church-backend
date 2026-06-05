package com.elipair.church.domain.media;

import com.elipair.church.global.common.ContentRef;
import java.util.List;

/**
 * 특정 미디어를 참조하는 콘텐츠를 도메인별로 찾아 반환하는 SPI(스펙 §5.10 참조 추적).
 * 콘텐츠 도메인(설교·공지·이벤트·부서·갤러리·주보)이 각자 구현해 빈으로 등록하면,
 * MediaService가 모든 구현을 주입받아 결과를 합친다 — MediaService는 도메인을 몰라도 된다(content → media 단방향).
 * 현재 구현체는 0개(후속 D7~D11에서 추가). 차단형 삭제 로직은 stub 구현으로 테스트한다.
 */
public interface MediaReferenceProvider {

    List<ContentRef> findReferences(long mediaId);
}
