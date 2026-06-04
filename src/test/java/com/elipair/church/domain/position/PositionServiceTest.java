package com.elipair.church.domain.position;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.elipair.church.domain.position.dto.PositionCreateRequest;
import com.elipair.church.domain.position.dto.PositionResponse;
import com.elipair.church.domain.position.dto.PositionUpdateRequest;
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
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class PositionServiceTest {

    @Mock
    private PositionRepository repository;

    @InjectMocks
    private PositionService service;

    @Test
    void list_maps_repository_result() {
        when(repository.findAllByOrderBySortOrderAsc())
                .thenReturn(List.of(Position.of("목사", 10), Position.of("장로", 20)));

        List<PositionResponse> result = service.list();

        assertThat(result).extracting(PositionResponse::name).containsExactly("목사", "장로");
    }

    @Test
    void create_with_explicit_sort_order() {
        when(repository.existsByName("목사")).thenReturn(false);
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        service.create(new PositionCreateRequest("목사", 5));

        ArgumentCaptor<Position> captor = ArgumentCaptor.forClass(Position.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getSortOrder()).isEqualTo(5);
        verify(repository, never()).findMaxSortOrder();
    }

    @Test
    void create_without_sort_order_uses_max_plus_gap() {
        when(repository.existsByName("장로")).thenReturn(false);
        when(repository.findMaxSortOrder()).thenReturn(Optional.of(10));
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        service.create(new PositionCreateRequest("장로", null));

        ArgumentCaptor<Position> captor = ArgumentCaptor.forClass(Position.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getSortOrder()).isEqualTo(20);
    }

    @Test
    void create_without_sort_order_on_empty_table_uses_gap() {
        when(repository.existsByName("목사")).thenReturn(false);
        when(repository.findMaxSortOrder()).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        service.create(new PositionCreateRequest("목사", null));

        ArgumentCaptor<Position> captor = ArgumentCaptor.forClass(Position.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getSortOrder()).isEqualTo(10);
    }

    @Test
    void create_trims_name() {
        when(repository.existsByName("목사")).thenReturn(false);
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        service.create(new PositionCreateRequest("  목사  ", 10));

        ArgumentCaptor<Position> captor = ArgumentCaptor.forClass(Position.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("목사");
    }

    @Test
    void create_duplicate_name_precheck_throws() {
        when(repository.existsByName("목사")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new PositionCreateRequest("목사", 10)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.DUPLICATE_RESOURCE));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void create_unique_race_translates_to_duplicate() {
        when(repository.existsByName("목사")).thenReturn(false);
        when(repository.findMaxSortOrder()).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq_positions_name"));

        assertThatThrownBy(() -> service.create(new PositionCreateRequest("목사", null)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.DUPLICATE_RESOURCE));
    }

    @Test
    void create_blank_name_throws_invalid_input() {
        assertThatThrownBy(() -> service.create(new PositionCreateRequest("   ", 10)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verify(repository, never()).existsByName(any());
    }

    @Test
    void update_name_only_keeps_sort_order() {
        when(repository.findById(1L)).thenReturn(Optional.of(Position.of("목사", 10)));
        when(repository.existsByName("부목사")).thenReturn(false);
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        PositionResponse result = service.update(1L, new PositionUpdateRequest("부목사", null));

        assertThat(result.name()).isEqualTo("부목사");
        assertThat(result.sortOrder()).isEqualTo(10);
    }

    @Test
    void update_sort_order_only_keeps_name_and_skips_dup_check() {
        when(repository.findById(1L)).thenReturn(Optional.of(Position.of("목사", 10)));
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        PositionResponse result = service.update(1L, new PositionUpdateRequest(null, 99));

        assertThat(result.name()).isEqualTo("목사");
        assertThat(result.sortOrder()).isEqualTo(99);
        verify(repository, never()).existsByName(any());
    }

    @Test
    void update_blank_name_throws_invalid_input() {
        when(repository.findById(1L)).thenReturn(Optional.of(Position.of("목사", 10)));

        assertThatThrownBy(() -> service.update(1L, new PositionUpdateRequest("   ", null)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
    }

    @Test
    void update_unknown_id_throws_not_found() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(999L, new PositionUpdateRequest("목사", null)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void update_rename_to_existing_name_throws_duplicate() {
        when(repository.findById(1L)).thenReturn(Optional.of(Position.of("목사", 10)));
        when(repository.existsByName("장로")).thenReturn(true);

        assertThatThrownBy(() -> service.update(1L, new PositionUpdateRequest("장로", null)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.DUPLICATE_RESOURCE));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void update_unique_race_translates_to_duplicate() {
        when(repository.findById(1L)).thenReturn(Optional.of(Position.of("목사", 10)));
        when(repository.existsByName("부목사")).thenReturn(false);
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq_positions_name"));

        assertThatThrownBy(() -> service.update(1L, new PositionUpdateRequest("부목사", null)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.DUPLICATE_RESOURCE));
    }

    @Test
    void delete_existing_calls_delete_by_id() {
        when(repository.existsById(1L)).thenReturn(true);

        service.delete(1L);

        verify(repository).deleteById(1L);
    }

    @Test
    void delete_unknown_id_throws_not_found() {
        when(repository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(999L))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        verify(repository, never()).deleteById(any());
    }
}
