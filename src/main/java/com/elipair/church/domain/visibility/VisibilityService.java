package com.elipair.church.domain.visibility;

import com.elipair.church.domain.visibility.dto.VisibilityResponse;
import com.elipair.church.domain.visibility.dto.VisibilityUpdateRequest;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 교회별 메뉴 공개 범위(#95). 5개 메뉴의 읽기 요청마다 읽히므로 교회별로 캐시한다
 * (키 접두사의 교회 id는 CacheConfig가 붙인다 — 교회 A의 저장이 B의 캐시에 닿지 않는다).
 */
@Service
@Transactional(readOnly = true)
public class VisibilityService {

    /** get()과 update()가 같은 키를 쓰게 고정한다(인자 기반 기본 키는 둘이 다르다). */
    private static final String CACHE_KEY = "'all'";

    private final ContentVisibilityRepository repository;
    private final VisibilityService self;

    // levelOf가 캐시된 get()을 거치려면 프록시를 통해 불러야 한다(내부 호출은 @Cacheable을 건너뛴다).
    public VisibilityService(ContentVisibilityRepository repository, @Lazy VisibilityService self) {
        this.repository = repository;
        this.self = self;
    }

    @Cacheable(value = "visibility", key = CACHE_KEY)
    public VisibilityResponse get() {
        Map<ContentDomain, VisibilityLevel> stored = repository.findAll().stream()
                .collect(Collectors.toMap(
                        ContentVisibility::getDomain,
                        ContentVisibility::getLevel,
                        (a, b) -> a,
                        () -> new EnumMap<>(ContentDomain.class)));
        return VisibilityResponse.from(stored);
    }

    /** 행이 없으면 domain.defaultLevel(). */
    public VisibilityLevel levelOf(ContentDomain domain) {
        return self.get().levelOf(domain);
    }

    public boolean isPublic(ContentDomain domain) {
        return levelOf(domain) == VisibilityLevel.PUBLIC;
    }

    /**
     * 5개 값을 upsert한다. 설정 캐시는 새 값으로 덮어쓴다(allEntries 삭제는 Lettuce에서 비동기라 직후 GET이 옛 값을
     * 볼 수 있다). 홈(/api/main)은 공지·일정의 공개 여부에 따라 달라지므로 함께 지운다.
     */
    @Transactional
    @Caching(
            put = @CachePut(value = "visibility", key = CACHE_KEY),
            evict = @CacheEvict(value = "main", allEntries = true))
    public VisibilityResponse update(VisibilityUpdateRequest request) {
        Map<ContentDomain, VisibilityLevel> wanted = new EnumMap<>(ContentDomain.class);
        wanted.put(ContentDomain.NOTICE, request.notices());
        wanted.put(ContentDomain.EVENT, request.events());
        wanted.put(ContentDomain.BULLETIN, request.bulletins());
        wanted.put(ContentDomain.SERMON, request.sermons());
        wanted.put(ContentDomain.GALLERY, request.gallery());

        Map<ContentDomain, ContentVisibility> existing = repository.findAll().stream()
                .collect(Collectors.toMap(ContentVisibility::getDomain, Function.identity()));
        wanted.forEach((domain, level) -> {
            ContentVisibility row = existing.get(domain);
            if (row == null) {
                repository.save(ContentVisibility.create(domain, level));
            } else {
                row.change(level);
            }
        });
        return VisibilityResponse.from(wanted);
    }
}
