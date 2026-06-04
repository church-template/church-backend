package com.elipair.church.global.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * 업로드 파일 저장 추상화(스펙 §8). 1차 구현은 LocalFileStorage(로컬 디스크).
 * 향후 S3/OCI Object Storage로 옮길 때 구현체만 교체한다.
 */
public interface FileStorage {

    /**
     * 파일을 저장하고 저장 키(루트 기준 상대 경로, 예: "2026/06/{uuid}.jpg")를 반환한다.
     * 반환값은 media.stored_path에 그대로 보관된다.
     * @throws com.elipair.church.global.exception.BusinessException
     *         INVALID_INPUT_VALUE(빈 파일), FILE_SIZE_EXCEEDED(한도 초과), FILE_STORAGE_ERROR(I/O 실패)
     */
    String store(MultipartFile file);

    /**
     * 저장 키로 파일을 조회한다(서빙·다운로드용). 루트 내부의 일반 파일만 대상.
     * @throws com.elipair.church.global.exception.BusinessException
     *         RESOURCE_NOT_FOUND(없음·루트밖·디렉터리/특수파일), FILE_STORAGE_ERROR(I/O 실패)
     */
    Resource load(String storedPath);

    /**
     * 저장 키의 파일을 삭제한다. 관리 대상(루트 내부 일반 파일)만 제거하고,
     * 미존재·루트밖·디렉터리/특수파일은 모두 no-op(idempotent).
     * @throws com.elipair.church.global.exception.BusinessException FILE_STORAGE_ERROR(I/O 실패)
     */
    void delete(String storedPath);
}
