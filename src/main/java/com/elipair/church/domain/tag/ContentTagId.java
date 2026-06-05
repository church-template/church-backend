package com.elipair.church.domain.tag;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.io.Serializable;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** content_tags 복합키 (tag_id, resource_type, resource_id). */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentTagId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "tag_id", nullable = false)
    private Long tagId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 30)
    private ContentResourceType resourceType;

    @Column(name = "resource_id", nullable = false)
    private Long resourceId;

    public ContentTagId(Long tagId, ContentResourceType resourceType, Long resourceId) {
        this.tagId = tagId;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ContentTagId that)) {
            return false;
        }
        return Objects.equals(tagId, that.tagId)
                && resourceType == that.resourceType
                && Objects.equals(resourceId, that.resourceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tagId, resourceType, resourceId);
    }
}
