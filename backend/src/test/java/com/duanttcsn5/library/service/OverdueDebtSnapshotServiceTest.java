package com.duanttcsn5.library.service;

import com.duanttcsn5.library.repository.OverdueDebtReportRepository;
import com.duanttcsn5.library.repository.OverdueDebtReportRepository.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OverdueDebtSnapshotServiceTest {
    @Test void groupsAllLoanItemsOnceAndRetainsDebtOnlyAndZeroOpenDaysReaders() {
        var repo=mock(OverdueDebtReportRepository.class);var calendar=mock(LibraryConfigurationService.class);
        var at=OffsetDateTime.parse("2026-10-15T00:01:00+07:00");
        var older=OffsetDateTime.parse("2026-10-01T12:00:00+07:00");
        var recent=OffsetDateTime.parse("2026-10-14T12:00:00+07:00");
        when(repo.overdueItems(any())).thenReturn(List.of(new Item(1L,older),new Item(1L,older),new Item(1L,recent),new Item(3L,recent)));
        when(calendar.calculateOverdueOpenDays(anySet(),eq(at.toLocalDate())))
            .thenReturn(Map.of(older.toLocalDate(),10L,recent.toLocalDate(),0L));
        when(repo.readers(any())).thenReturn(List.of(
            new ReaderFacts(1L,"001","Nguyễn Văn A","0901",new BigDecimal("75000")),
            new ReaderFacts(2L,"002","Nợ phí","0902",new BigDecimal("30000")),
            new ReaderFacts(3L,"003","Chỉ quá hạn","0903",BigDecimal.ZERO)));
        var result=new OverdueDebtSnapshotService(repo,calendar).snapshot(at);
        assertThat(result.totalReaders()).isEqualTo(3);assertThat(result.readers()).hasSize(3);
        assertThat(result.readers().get(0).overdueBooks()).isEqualTo(3);
        assertThat(result.readers().get(0).maxOverdueDays()).isEqualTo(10);
        assertThat(result.readers().get(0).outstandingVnd()).isEqualByComparingTo("75000");
        assertThat(result.readers().get(1).overdueBooks()).isZero();
        assertThat(result.readers().get(1).maxOverdueDays()).isZero();
        assertThat(result.readers().get(2).overdueBooks()).isEqualTo(1);
        assertThat(result.readers().get(2).maxOverdueDays()).isZero();
        verify(calendar).calculateOverdueOpenDays(Set.of(older.toLocalDate(),recent.toLocalDate()),at.toLocalDate());
        verify(repo).overdueItems(OffsetDateTime.parse("2026-10-15T00:00:00+07:00"));
    }
    @Test void emptySnapshot() {
        var repo=mock(OverdueDebtReportRepository.class);var calendar=mock(LibraryConfigurationService.class);
        when(repo.overdueItems(any())).thenReturn(List.of());when(repo.readers(any())).thenReturn(List.of());
        when(calendar.calculateOverdueOpenDays(any(),any())).thenReturn(Map.of());
        assertThat(new OverdueDebtSnapshotService(repo,calendar).snapshot(OffsetDateTime.now()).readers()).isEmpty();
    }
}
