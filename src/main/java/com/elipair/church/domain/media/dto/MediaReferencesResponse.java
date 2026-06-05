package com.elipair.church.domain.media.dto;

import com.elipair.church.global.common.ContentRef;
import java.util.List;

/** GET /api/admin/media/{id}/references 응답(스펙 §5.10): 이 미디어를 참조하는 콘텐츠 목록. */
public record MediaReferencesResponse(long mediaId, boolean inUse, List<ContentRef> references) {}
