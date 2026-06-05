package com.elipair.church.domain.tag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.elipair.church.domain.tag.dto.TagCreateRequest;
import com.elipair.church.domain.tag.dto.TagResponse;
import com.elipair.church.domain.tag.dto.TagUpdateRequest;
import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TagServiceTest {

    @Mock
    private TagRepository tagRepository;

    @Mock
    private ContentTagRepository contentTagRepository;

    @InjectMocks
    private TagService service;

    @Test
    void list_maps_repository_result() {
        when(tagRepository.findAllByOrderByNameAsc()).thenReturn(List.of(Tag.create("봉사"), Tag.create("예배")));

        List<TagResponse> result = service.list();

        assertThat(result).extracting(TagResponse::name).containsExactly("봉사", "예배");
    }

    @Test
    void create_trims_and_persists() {
        when(tagRepository.existsByName("예배")).thenReturn(false);
        when(tagRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        service.create(new TagCreateRequest("  예배  "));

        ArgumentCaptor<Tag> captor = ArgumentCaptor.forClass(Tag.class);
        verify(tagRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("예배");
    }

    @Test
    void create_duplicate_name_throws_409() {
        when(tagRepository.existsByName("예배")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new TagCreateRequest("예배")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_RESOURCE);
    }

    @Test
    void create_blank_name_throws_400() {
        assertThatThrownBy(() -> service.create(new TagCreateRequest("   ")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void update_renames() {
        Tag tag = Tag.create("예배");
        when(tagRepository.findById(1L)).thenReturn(Optional.of(tag));
        when(tagRepository.existsByName("주일예배")).thenReturn(false);
        when(tagRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        TagResponse result = service.update(1L, new TagUpdateRequest("주일예배"));

        assertThat(result.name()).isEqualTo("주일예배");
    }

    @Test
    void update_duplicate_name_throws_409() {
        when(tagRepository.findById(1L)).thenReturn(Optional.of(Tag.create("예배")));
        when(tagRepository.existsByName("선교")).thenReturn(true);

        assertThatThrownBy(() -> service.update(1L, new TagUpdateRequest("선교")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_RESOURCE);
    }

    @Test
    void update_unknown_id_throws_404() {
        when(tagRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(999L, new TagUpdateRequest("선교")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void delete_cleans_links_then_removes_tag() {
        when(tagRepository.findById(1L)).thenReturn(Optional.of(Tag.create("예배")));

        service.delete(1L);

        verify(contentTagRepository).deleteByTag(1L);
        verify(tagRepository).deleteById(1L);
    }

    @Test
    void delete_unknown_id_throws_404_without_touching_links() {
        when(tagRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        verify(contentTagRepository, never()).deleteByTag(any());
    }
}
