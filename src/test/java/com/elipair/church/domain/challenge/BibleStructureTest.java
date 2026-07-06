package com.elipair.church.domain.challenge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BibleStructureTest {

    // ---- 구간 장 수 (스펙 §2 검증 데이터) ----

    @Test
    void full_bible_is_1189_chapters() {
        assertThat(BibleStructure.chapterCount(1, 66)).isEqualTo(1189);
    }

    @Test
    void old_testament_is_929_chapters() {
        assertThat(BibleStructure.chapterCount(1, 39)).isEqualTo(929);
    }

    @Test
    void new_testament_is_260_chapters() {
        assertThat(BibleStructure.chapterCount(40, 66)).isEqualTo(260);
    }

    @Test
    void single_book_psalms_is_150() {
        assertThat(BibleStructure.chapterCount(19, 19)).isEqualTo(150);
    }

    @Test
    void gospels_matthew_to_john_is_89() {
        assertThat(BibleStructure.chapterCount(40, 43)).isEqualTo(89); // 28+16+24+21
    }

    // ---- 위치 역산 (권 경계 = 누적합 산술) ----

    @Test
    void locate_first_chapter_is_genesis_1() {
        assertThat(BibleStructure.locate(1, 1)).isEqualTo(new BibleStructure.BiblePosition("창세기", 1));
    }

    @Test
    void locate_50th_is_genesis_50_and_51st_crosses_into_exodus_1() {
        assertThat(BibleStructure.locate(1, 50)).isEqualTo(new BibleStructure.BiblePosition("창세기", 50));
        assertThat(BibleStructure.locate(1, 51)).isEqualTo(new BibleStructure.BiblePosition("출애굽기", 1));
    }

    @Test
    void locate_last_chapter_is_revelation_22() {
        assertThat(BibleStructure.locate(1, 1189)).isEqualTo(new BibleStructure.BiblePosition("요한계시록", 22));
    }

    @Test
    void locate_respects_scope_start_book() {
        assertThat(BibleStructure.locate(40, 1)).isEqualTo(new BibleStructure.BiblePosition("마태복음", 1));
        assertThat(BibleStructure.locate(40, 260)).isEqualTo(new BibleStructure.BiblePosition("요한계시록", 22));
        assertThat(BibleStructure.locate(19, 151)).isEqualTo(new BibleStructure.BiblePosition("잠언", 1)); // 시편 150 다음
    }

    // ---- 검증 ----

    @Test
    void invalid_range_throws() {
        assertThatThrownBy(() -> BibleStructure.chapterCount(0, 5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BibleStructure.chapterCount(5, 4)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BibleStructure.chapterCount(1, 67)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void locate_out_of_bounds_throws() {
        assertThatThrownBy(() -> BibleStructure.locate(1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BibleStructure.locate(1, 1190)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BibleStructure.locate(67, 1)).isInstanceOf(IllegalArgumentException.class);
    }
}
