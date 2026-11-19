package com.elipair.church.domain.media;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MediaRefsTest {

    @Test
    void extracts_ids_in_order_without_duplicates() {
        assertThat(MediaRefs.ids("![](media:42) 글 ![](media:7) 다시 ![](media:42)"))
                .containsExactly(42L, 7L);
    }

    @Test
    void does_not_match_inside_longer_number_or_word() {
        // GalleryAlbumService의 기존 규칙과 같다: 숫자 경계만 본다(앞 글자는 보지 않는다 — DB 정규식과 같다)
        assertThat(MediaRefs.ids("media:420")).containsExactly(420L);
        assertThat(MediaRefs.ids("xmedia:42")).containsExactly(42L);
        assertThat(MediaRefs.ids("media:4a")).containsExactly(4L);
        assertThat(MediaRefs.ids("media:abc media:")).isEmpty();
        // long 범위를 넘는 숫자는 id가 될 수 없어 건너뛴다(예외로 상세 조회가 깨지지 않게)
        assertThat(MediaRefs.ids("media:99999999999999999999999 media:5")).containsExactly(5L);
    }

    @Test
    void null_and_blank_give_empty_list() {
        assertThat(MediaRefs.ids(null)).isEmpty();
        assertThat(MediaRefs.ids("")).isEmpty();
        assertThat(MediaRefs.ids("   ")).isEmpty();
    }
}
