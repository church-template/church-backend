package com.elipair.church.domain.challenge;

import com.elipair.church.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 챌린지 참여(설계 §2). chaptersRead = 현재 회독의 포인터(0~구간 장 수), 회독 완료 시 초과분 이월.
 * member는 BaseEntity 관례대로 Long id 참조(연관 아님). 동시 클릭 방어는 상속받은 @Version.
 * 참여 취소 = 소프트삭제, (challenge_id, member_id) 부분 유니크로 재참여 허용.
 */
@Entity
@Table(name = "challenge_participations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChallengeParticipation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "challenge_id", nullable = false)
    private Long challengeId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "chapters_read", nullable = false)
    private int chaptersRead;

    @Column(name = "rounds_completed", nullable = false)
    private int roundsCompleted;

    private ChallengeParticipation(Long challengeId, Long memberId) {
        this.challengeId = challengeId;
        this.memberId = memberId;
        this.chaptersRead = 0;
        this.roundsCompleted = 0;
    }

    public static ChallengeParticipation create(Long challengeId, Long memberId) {
        return new ChallengeParticipation(challengeId, memberId);
    }

    /** 포인터 전진 — 구간 끝 도달 시 회독 +1·초과분 이월(여러 회독 한 번에 가능, 설계 §3). */
    public void advance(int chapters, int scopeChapters) {
        chaptersRead += chapters;
        while (chaptersRead >= scopeChapters) {
            chaptersRead -= scopeChapters;
            roundsCompleted++;
        }
    }

    /** 기록 취소 롤백 — 회독 경계 역이월(설계 §3). 로그 합계 불변식상 음수 회독은 불가능. */
    public void rollback(int chapters, int scopeChapters) {
        chaptersRead -= chapters;
        while (chaptersRead < 0) {
            chaptersRead += scopeChapters;
            roundsCompleted--;
        }
        if (roundsCompleted < 0) {
            throw new IllegalStateException("롤백이 기록 합계를 초과했습니다: participation=" + id);
        }
    }

    /** 누적 총 읽은 장 수(회독 포함) — 페이스 계산용. */
    public int totalChaptersRead(int scopeChapters) {
        return roundsCompleted * scopeChapters + chaptersRead;
    }
}
