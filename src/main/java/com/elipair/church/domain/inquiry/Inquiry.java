package com.elipair.church.domain.inquiry;

import com.elipair.church.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 방문자 문의(이슈 #50). 소프트삭제·감사를 위해 BaseEntity를 상속한다.
 * 등록 후 내용은 수정되지 않는다 — 관리자가 바꿀 수 있는 건 completedAt(완료 체크)뿐.
 * created_by는 익명 제출이라 null, 완료 처리 시 updated_by에 처리한 관리자가 기록된다.
 */
@Entity
@Table(name = "inquiries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inquiry extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(length = 100)
    private String email;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "privacy_agreed_at", nullable = false)
    private LocalDateTime privacyAgreedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    private Inquiry(String name, String phone, String email, String content, LocalDateTime privacyAgreedAt) {
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.content = content;
        this.privacyAgreedAt = privacyAgreedAt;
    }

    /** 동의 없이는 생성 자체가 불가능하다 — 동의 검증은 DTO(@AssertTrue), 시각 기록은 여기서. */
    public static Inquiry create(String name, String phone, String email, String content, LocalDateTime agreedAt) {
        return new Inquiry(name, phone, email, content, agreedAt);
    }

    /** 완료 체크/해제 토글. 완료 시각은 서버 시각으로 갱신, 해제하면 null로 되돌린다. */
    public void markCompleted(boolean completed, LocalDateTime now) {
        this.completedAt = completed ? now : null;
    }

    public boolean isCompleted() {
        return completedAt != null;
    }
}
