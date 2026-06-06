package com.elipair.church.domain.department;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.elipair.church.domain.department.dto.DepartmentCreateRequest;
import com.elipair.church.domain.department.dto.DepartmentDetailResponse;
import com.elipair.church.domain.department.dto.DepartmentPatchRequest;
import com.elipair.church.domain.department.dto.DepartmentUpdateRequest;
import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DepartmentServiceTest {

    private DepartmentRepository repository;
    private DepartmentService service;

    @BeforeEach
    void init() {
        repository = mock(DepartmentRepository.class);
        service = new DepartmentService(repository);
    }

    private Department mockDept(long id, long version) {
        Department d = mock(Department.class);
        when(d.getId()).thenReturn(id);
        when(d.getVersion()).thenReturn(version);
        return d;
    }

    @Test
    void create_root_appends_first_sort_order_and_skips_lock() {
        when(repository.findMaxSortOrder()).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DepartmentDetailResponse res = service.create(new DepartmentCreateRequest("예배부", "본문", "김목사", null, null));

        assertThat(res.sortOrder()).isEqualTo(10); // 빈 테이블 → 10
        assertThat(res.parentId()).isNull();
        verify(repository, never()).lockHierarchy(anyLong()); // 루트 생성은 구조 위험 없음 → 락 안 잡음
    }

    @Test
    void create_with_parent_appends_max_plus_10_and_locks() {
        Department parent = Department.create("상위", "본문", "목사", null, 10); // 루트(parentId null)
        when(repository.findByIdAndDeletedAtIsNull(5L)).thenReturn(Optional.of(parent));
        when(repository.findMaxSortOrder()).thenReturn(Optional.of(20));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DepartmentDetailResponse res = service.create(new DepartmentCreateRequest("성가대", "본문", "이집사", 5L, null));

        assertThat(res.sortOrder()).isEqualTo(30); // 20 + 10
        assertThat(res.parentId()).isEqualTo(5L);
        verify(repository).lockHierarchy(anyLong()); // 부모 지정 → 락
    }

    @Test
    void create_with_explicit_sort_order_skips_append() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DepartmentDetailResponse res = service.create(new DepartmentCreateRequest("부서", "본문", "목사", null, 99));

        assertThat(res.sortOrder()).isEqualTo(99);
        verify(repository, never()).findMaxSortOrder(); // 명시값이라 append 조회 안 함
    }

    @Test
    void create_with_nonexistent_parent_is_400() {
        when(repository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new DepartmentCreateRequest("x", null, null, 99L, null)))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verify(repository, never()).save(any());
    }

    @Test
    void update_with_matching_version_updates_locks_and_flushes() {
        Department d = mockDept(10L, 3L);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(d));
        DepartmentUpdateRequest req = new DepartmentUpdateRequest("새이름", "새본문", "새교역자", null, 5, 3L);

        service.update(10L, req);

        verify(repository).lockHierarchy(anyLong());
        verify(d).update("새이름", "새본문", "새교역자", null, 5);
        verify(repository).flush();
    }

    @Test
    void update_with_stale_version_throws_409_and_skips_changes() {
        Department d = mockDept(10L, 3L);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(d));
        DepartmentUpdateRequest req = new DepartmentUpdateRequest("새이름", "새본문", "새교역자", null, 5, 2L);

        assertThatThrownBy(() -> service.update(10L, req))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getErrorCode())
                        .isEqualTo(ErrorCode.OPTIMISTIC_LOCK_CONFLICT));
        verify(d, never()).update(any(), any(), any(), any(), any());
    }

    @Test
    void update_self_reference_is_400() {
        Department d = mockDept(10L, 0L);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(d));
        DepartmentUpdateRequest req = new DepartmentUpdateRequest("x", null, null, 10L, null, 0L);

        assertThatThrownBy(() -> service.update(10L, req))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verify(d, never()).update(any(), any(), any(), any(), any());
    }

    @Test
    void update_descendant_as_parent_is_cycle_400() {
        // self=10(예배부). 후보 부모 20(성가대)의 parentId=10 → 10의 후손 → 사이클.
        Department self = mockDept(10L, 0L);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(self));
        Department twenty = mock(Department.class);
        when(twenty.getParentId()).thenReturn(10L);
        when(repository.findByIdAndDeletedAtIsNull(20L)).thenReturn(Optional.of(twenty));
        DepartmentUpdateRequest req = new DepartmentUpdateRequest("x", null, null, 20L, null, 0L);

        assertThatThrownBy(() -> service.update(10L, req))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verify(self, never()).update(any(), any(), any(), any(), any());
    }

    @Test
    void patch_with_null_parent_keeps_parent_and_skips_lock() {
        Department d = mockDept(10L, 0L);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(d));
        DepartmentPatchRequest req = new DepartmentPatchRequest("새이름", null, null, null, null, 0L);

        service.patch(10L, req);

        verify(repository, never()).lockHierarchy(anyLong()); // parentId null → 락 안 잡음
        verify(d).applyPatch("새이름", null, null, null, null);
        verify(repository).flush();
    }

    @Test
    void patch_with_parent_locks_and_validates() {
        Department d = mockDept(10L, 0L);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(d));
        Department parent = Department.create("상위", "본문", "목사", null, 10); // 루트
        when(repository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(parent));
        DepartmentPatchRequest req = new DepartmentPatchRequest(null, null, null, 7L, null, 0L);

        service.patch(10L, req);

        verify(repository).lockHierarchy(anyLong());
        verify(d).applyPatch(null, null, null, 7L, null);
        verify(repository).flush();
    }

    @Test
    void patch_with_stale_version_throws_409() {
        Department d = mockDept(10L, 3L);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(d));
        DepartmentPatchRequest req = new DepartmentPatchRequest("x", null, null, null, null, 2L);

        assertThatThrownBy(() -> service.patch(10L, req))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getErrorCode())
                        .isEqualTo(ErrorCode.OPTIMISTIC_LOCK_CONFLICT));
    }

    @Test
    void delete_with_live_children_throws_409_and_skips_soft_delete() {
        Department d = mockDept(10L, 0L);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(d));
        when(repository.existsByParentIdAndDeletedAtIsNull(10L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(10L))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getErrorCode())
                        .isEqualTo(ErrorCode.DEPARTMENT_HAS_CHILDREN));
        verify(d, never()).softDelete();
        verify(repository).lockHierarchy(anyLong());
    }

    @Test
    void delete_without_children_soft_deletes() {
        Department d = mockDept(10L, 0L);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(d));
        when(repository.existsByParentIdAndDeletedAtIsNull(10L)).thenReturn(false);

        service.delete(10L);

        verify(d).softDelete();
    }

    @Test
    void get_unknown_throws_404() {
        when(repository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(99L))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void get_returns_detail_for_existing() {
        Department d = mockDept(10L, 0L);
        when(d.getName()).thenReturn("예배부");
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(d));

        assertThat(service.get(10L).name()).isEqualTo("예배부");
    }

    @Test
    void update_transitive_descendant_as_parent_is_cycle_400() {
        // self=10. 후보 부모 30 → 30.parent=20 → 20.parent=10(self) → 다단계(depth>1) 사이클.
        Department self = mockDept(10L, 0L);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(self));
        Department thirty = mock(Department.class);
        when(thirty.getParentId()).thenReturn(20L);
        when(repository.findByIdAndDeletedAtIsNull(30L)).thenReturn(Optional.of(thirty));
        Department twenty = mock(Department.class);
        when(twenty.getParentId()).thenReturn(10L);
        when(repository.findByIdAndDeletedAtIsNull(20L)).thenReturn(Optional.of(twenty));
        DepartmentUpdateRequest req = new DepartmentUpdateRequest("x", null, null, 30L, null, 0L);

        assertThatThrownBy(() -> service.update(10L, req))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verify(self, never()).update(any(), any(), any(), any(), any());
    }

    @Test
    void patch_self_reference_is_400() {
        Department d = mockDept(10L, 0L);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(d));
        DepartmentPatchRequest req = new DepartmentPatchRequest(null, null, null, 10L, null, 0L);

        assertThatThrownBy(() -> service.patch(10L, req))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verify(d, never()).applyPatch(any(), any(), any(), any(), any());
    }

    @Test
    void delete_unknown_throws_404() {
        when(repository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void list_returns_cards_without_description() {
        Department d = mock(Department.class);
        when(d.getId()).thenReturn(1L);
        when(d.getName()).thenReturn("예배부");
        when(d.getLeader()).thenReturn("김목사");
        when(d.getParentId()).thenReturn(null);
        when(d.getSortOrder()).thenReturn(10);
        when(repository.findByDeletedAtIsNullOrderBySortOrderAscIdAsc()).thenReturn(List.of(d));

        assertThat(service.list()).singleElement().satisfies(card -> {
            assertThat(card.name()).isEqualTo("예배부");
            assertThat(card.parentId()).isNull();
            assertThat(card.sortOrder()).isEqualTo(10);
        });
    }
}
