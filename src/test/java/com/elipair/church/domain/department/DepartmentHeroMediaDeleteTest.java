package com.elipair.church.domain.department;

import static org.assertj.core.api.Assertions.assertThat;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.domain.media.MediaRepository;
import com.elipair.church.domain.media.MediaService;
import com.elipair.church.domain.member.Member;
import com.elipair.church.domain.member.MemberRepository;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** 실제 Flyway 스키마(V20 ON DELETE SET NULL)로 검증 — 삭제된 부서의 대표 사진 미디어는 FK 위반 없이 지워진다. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DepartmentHeroMediaDeleteTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 1, 2, 3, 4, 5, 6, 7};

    @TempDir
    static Path uploadDir;

    @DynamicPropertySource
    static void fileProps(DynamicPropertyRegistry registry) {
        registry.add("file.upload-dir", () -> uploadDir.toString());
    }

    @Autowired
    private MediaService mediaService;

    @Autowired
    private MediaRepository mediaRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanup() {
        departmentRepository.deleteAll();
        mediaRepository.deleteAll();
        memberRepository.deleteAll(memberRepository.findAll());
    }

    @Test
    void 삭제된_부서의_대표_사진_미디어는_지울_수_있다() {
        Member uploader =
                memberRepository.saveAndFlush(Member.create("01000000001", "업로더", "{enc}", null, null, true, true));
        long mediaId = mediaService
                .upload(new MockMultipartFile("file", "a.jpg", "image/jpeg", JPEG), uploader.getId())
                .id();
        Department dept = Department.create("삭제부서", "본문", null, null, 10);
        dept.changeHeroMedia(mediaId);
        dept.softDelete();
        Long deptId = departmentRepository.saveAndFlush(dept).getId();

        mediaService.delete(mediaId);

        assertThat(mediaRepository.findById(mediaId)).isEmpty();
        assertThat(jdbc.queryForObject("select hero_media_id from departments where id = ?", Long.class, deptId))
                .isNull();
    }
}
