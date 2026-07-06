package com.elipair.church.domain.challenge;

/**
 * 개신교 정경 66권 1189장 — 불변 상수(설계 §2, 테이블 없음).
 * 성경을 한 줄로 펼친 누적합으로 권 경계를 산술 처리한다: 구간 장 수·포인터→(권,장) 역산.
 */
public final class BibleStructure {

    /** 구간 내 위치 — 한글 권 이름 + 장 번호(사용자 표시용). */
    public record BiblePosition(String book, int chapter) {}

    public static final int BOOK_COUNT = 66;

    private static final String[] NAMES = {
        "창세기", "출애굽기", "레위기", "민수기", "신명기", "여호수아", "사사기", "룻기", "사무엘상", "사무엘하", "열왕기상", "열왕기하", "역대상", "역대하", "에스라",
        "느헤미야", "에스더", "욥기", "시편", "잠언", "전도서", "아가", "이사야", "예레미야", "예레미야애가", "에스겔", "다니엘", "호세아", "요엘", "아모스", "오바댜",
        "요나", "미가", "나훔", "하박국", "스바냐", "학개", "스가랴", "말라기", "마태복음", "마가복음", "누가복음", "요한복음", "사도행전", "로마서", "고린도전서",
        "고린도후서", "갈라디아서", "에베소서", "빌립보서", "골로새서", "데살로니가전서", "데살로니가후서", "디모데전서", "디모데후서", "디도서", "빌레몬서", "히브리서", "야고보서",
        "베드로전서", "베드로후서", "요한일서", "요한이서", "요한삼서", "유다서", "요한계시록"
    };

    private static final int[] CHAPTERS = {
        50, 40, 27, 36, 34, 24, 21, 4, 31, 24, 22, 25, 29, 36, 10, 13, 10, 42, 150, 31, 12, 8, 66, 52, 5, 48, 12, 14, 3,
        9, 1, 4, 7, 3, 3, 3, 2, 14, 4, 28, 16, 24, 21, 28, 16, 16, 13, 6, 6, 4, 4, 5, 3, 6, 4, 3, 1, 13, 5, 5, 3, 5, 1,
        1, 1, 22
    };

    /** CUMULATIVE[b] = 1~b권 장 수 합 (CUMULATIVE[0]=0, CUMULATIVE[66]=1189). */
    private static final int[] CUMULATIVE = new int[BOOK_COUNT + 1];

    static {
        for (int i = 0; i < BOOK_COUNT; i++) {
            CUMULATIVE[i + 1] = CUMULATIVE[i] + CHAPTERS[i];
        }
    }

    private BibleStructure() {}

    /** 구간 [startBook, endBook]의 총 장 수. */
    public static int chapterCount(int startBook, int endBook) {
        validateRange(startBook, endBook);
        return CUMULATIVE[endBook] - CUMULATIVE[startBook - 1];
    }

    /**
     * 구간 시작권 기준 ordinal(1-based)번째 장의 (권 이름, 장 번호).
     * 호출자는 ordinal ≤ 구간 장 수 불변식을 유지한다(구간 초과·성경 범위 내는 검증하지 않음).
     */
    public static BiblePosition locate(int startBook, int ordinal) {
        validateRange(startBook, startBook);
        int global = CUMULATIVE[startBook - 1] + ordinal; // 전역 1-based 장 번호
        if (ordinal < 1 || global > CUMULATIVE[BOOK_COUNT]) {
            throw new IllegalArgumentException("성경 범위를 벗어난 위치: startBook=" + startBook + ", ordinal=" + ordinal);
        }
        int book = 1;
        while (CUMULATIVE[book] < global) { // 최대 66회 선형 탐색 — 충분히 싸다
            book++;
        }
        return new BiblePosition(NAMES[book - 1], global - CUMULATIVE[book - 1]);
    }

    public static void validateRange(int startBook, int endBook) {
        if (startBook < 1 || endBook > BOOK_COUNT || startBook > endBook) {
            throw new IllegalArgumentException("잘못된 성경 구간: " + startBook + "~" + endBook);
        }
    }
}
