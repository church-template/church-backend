package com.elipair.church.domain.member;

import java.security.SecureRandom;

/** reset-password용 임시 비밀번호 생성(스펙 §5.2). SecureRandom, 최소 12자, 평문은 1회만 반환·로그 금지. */
final class TemporaryPasswords {

    // 0/O, 1/l/I 등 혼동 문자를 뺀 안전 집합(고령 사용자 대면 전달 고려).
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".toCharArray();
    private static final int LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private TemporaryPasswords() {}

    static String generate() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
