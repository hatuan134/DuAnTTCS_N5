package com.duanttcsn5.library.service;

import com.duanttcsn5.library.dto.report.OverdueDebtReportDtos.*;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.OverdueDebtReportRepository;
import com.duanttcsn5.library.repository.OverdueDebtReportRepository.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OverdueDebtFilterTest {
    private final OffsetDateTime at=OffsetDateTime.parse("2026-10-15T00:01:00+07:00");
    @Test void blankDefaultsZeroAndInvalidInput() {
        assertThat(Filter.parse(null,null)).isEqualTo(Filter.ALL);
        assertThat(Filter.parse("", "")).isEqualTo(Filter.ALL);
        assertThat(Filter.parse("0","2")).isEqualTo(new Filter(0,2L));
        for(String value:List.of("-1","1.5","abc","1e3","+1","2147483648"))
            assertThatThrownBy(()->Filter.parse(value,null)).isInstanceOf(ApiException.class);
        for(String value:List.of("0","-1","abc","1.5","9223372036854775808"))
            assertThatThrownBy(()->Filter.parse(null,value)).isInstanceOf(ApiException.class);
    }
    @Test void exactThirtyDayBoundaryUsesMaximumRatherThanSum() {
        var repo=mock(OverdueDebtReportRepository.class);var calendar=mock(LibraryConfigurationService.class);
        var d1=at.minusDays(40);var d2=at.minusDays(39);var d3=at.minusDays(38);
        when(repo.overdueItems(any())).thenReturn(List.of(new Item(1L,d1),new Item(1L,d1),new Item(2L,d2),new Item(3L,d3)));
        when(calendar.calculateOverdueOpenDays(anySet(),any())).thenReturn(Map.of(d1.toLocalDate(),29L,d2.toLocalDate(),30L,d3.toLocalDate(),31L));
        when(repo.readers(any())).thenReturn(List.of(
            new ReaderFacts(1L,"1","Dưới ngưỡng",null,BigDecimal.ZERO),
            new ReaderFacts(2L,"2","Đúng ngưỡng",null,BigDecimal.ZERO),
            new ReaderFacts(3L,"3","Trên ngưỡng",null,BigDecimal.ZERO)));
        var result=new OverdueDebtSnapshotService(repo,calendar).snapshot(at,new Filter(30,null));
        assertThat(result.readers()).extracting(Reader::readerId).containsExactly(2L,3L);
    }
    @Test void thresholdBoundariesAndDebtOnlyReadersAndCardTypeIntersection() {
        var repo=mock(OverdueDebtReportRepository.class);var calendar=mock(LibraryConfigurationService.class);
        var older=at.minusDays(10);var recent=at.minusDays(2);
        when(repo.overdueItems(any())).thenReturn(List.of(new Item(1L,older),new Item(1L,recent),new Item(2L,recent),new Item(4L,older)));
        when(calendar.calculateOverdueOpenDays(anySet(),any())).thenReturn(Map.of(older.toLocalDate(),7L,recent.toLocalDate(),1L));
        when(repo.readers(any())).thenReturn(List.of(
            new ReaderFacts(1L,"001","Sinh viên A","0901",BigDecimal.ZERO,1L),
            new ReaderFacts(2L,"002","Sinh viên B","0902",BigDecimal.ZERO,1L),
            new ReaderFacts(3L,"003","Chỉ nợ","0903",BigDecimal.TEN,2L),
            new ReaderFacts(4L,"004","Loại cũ","0904",BigDecimal.ZERO,2L)));
        when(repo.cardTypes()).thenReturn(List.of(new CardTypeOption(1L,"Sinh viên",true),new CardTypeOption(2L,"Loại cũ",false)));
        var service=new OverdueDebtSnapshotService(repo,calendar);
        for(Integer minimum:Arrays.asList(null,0,1,7,8,30)) {
            var result=service.snapshot(at,new Filter(minimum,null));
            var expected=minimum==null || minimum==0?List.of(1L,2L,3L,4L):minimum==1?List.of(1L,2L,4L):minimum==7?List.of(1L,4L):List.<Long>of();
            assertThat(result.readers()).extracting(Reader::readerId).containsExactlyElementsOf(expected);
            assertThat(result.totalReaders()).isEqualTo(expected.size());
        }
        assertThat(service.snapshot(at,new Filter(7,1L)).readers()).extracting(Reader::readerId).containsExactly(1L);
        assertThat(service.snapshot(at,new Filter(0,2L)).readers()).extracting(Reader::readerId).containsExactly(3L,4L);
        assertThat(service.snapshot(at,new Filter(1,2L)).readers()).extracting(Reader::readerId).containsExactly(4L);
        assertThat(service.snapshot(at,new Filter(7,1L)).readers().get(0).maxOverdueDays()).isEqualTo(7);
        assertThat(service.snapshot(at,new Filter(7,1L)).defaultFilter()).contains("Số ngày trễ tối thiểu: 7","Loại thẻ: Sinh viên");
        assertThatThrownBy(()->service.snapshot(at,new Filter(0,99L))).isInstanceOf(ApiException.class);
    }
}
