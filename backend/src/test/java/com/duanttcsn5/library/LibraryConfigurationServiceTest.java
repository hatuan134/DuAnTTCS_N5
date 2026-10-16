package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.libraryconfig.DueDateAdjustmentResponse;
import com.duanttcsn5.library.dto.libraryconfig.ShelfRequest;
import com.duanttcsn5.library.dto.libraryconfig.WeeklyScheduleItemRequest;
import com.duanttcsn5.library.dto.libraryconfig.WeeklyScheduleUpdateRequest;
import com.duanttcsn5.library.entity.LibraryClosedDate;
import com.duanttcsn5.library.entity.LibraryWeeklySchedule;
import com.duanttcsn5.library.entity.Shelf;
import com.duanttcsn5.library.entity.Warehouse;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.AuditLogRepository;
import com.duanttcsn5.library.repository.LibraryClosedDateRepository;
import com.duanttcsn5.library.repository.LibraryWeeklyScheduleRepository;
import com.duanttcsn5.library.repository.ShelfRepository;
import com.duanttcsn5.library.repository.WarehouseRepository;
import com.duanttcsn5.library.service.LibraryConfigurationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LibraryConfigurationServiceTest {

    @Mock WarehouseRepository warehouseRepository;
    @Mock ShelfRepository shelfRepository;
    @Mock LibraryWeeklyScheduleRepository weeklyScheduleRepository;
    @Mock LibraryClosedDateRepository closedDateRepository;
    @Mock AuditLogRepository auditLogRepository;

    private LibraryConfigurationService service;

    @BeforeEach
    void setUp() {
        service = new LibraryConfigurationService(
                warehouseRepository,
                shelfRepository,
                weeklyScheduleRepository,
                closedDateRepository,
                auditLogRepository);
    }

    @Test
    @DisplayName("Từ chối thêm kệ có mã trùng trong cùng một kho")
    void createShelf_DuplicateCodeInWarehouse_ThrowsConflict() {
        Warehouse warehouse = warehouse(1L, "KHO-A", "Kho A");
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
        when(shelfRepository.existsByWarehouse_IdAndCodeIgnoreCase(1L, "A01")).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class, () ->
                service.createShelf(new ShelfRequest(1L, "a01", "Kệ Văn học", ""), 5L, "127.0.0.1"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("SHELF_CODE_EXISTS", ex.getCode());
        verify(shelfRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cho phép cùng mã kệ ở hai kho khác nhau")
    void createShelf_SameCodeDifferentWarehouse_Success() {
        Warehouse warehouse = warehouse(2L, "KHO-B", "Kho B");
        when(warehouseRepository.findById(2L)).thenReturn(Optional.of(warehouse));
        when(shelfRepository.existsByWarehouse_IdAndCodeIgnoreCase(2L, "A01")).thenReturn(false);
        when(shelfRepository.save(any(Shelf.class))).thenAnswer(invocation -> {
            Shelf shelf = invocation.getArgument(0);
            shelf.setId(20L);
            return shelf;
        });

        var response = service.createShelf(
                new ShelfRequest(2L, "A01", "Kệ tham khảo", ""),
                5L,
                "127.0.0.1");

        assertEquals("A01", response.code());
        assertEquals(2L, response.warehouseId());
        verify(auditLogRepository).insert(eq(5L), eq("SHELF_CREATED"), eq("SHELF"), eq("20"), anyString(), eq("127.0.0.1"));
    }

    @Test
    @DisplayName("Không cho xoá kệ đang có bản sao sách")
    void deleteShelf_InUse_ThrowsBadRequest() {
        Warehouse warehouse = warehouse(1L, "KHO-A", "Kho A");
        Shelf shelf = shelf(9L, warehouse, "A01");
        when(shelfRepository.findById(9L)).thenReturn(Optional.of(shelf));
        when(shelfRepository.countBookCopiesOnShelf(9L)).thenReturn(3L);

        ApiException ex = assertThrows(ApiException.class, () ->
                service.deleteShelf(9L, 5L, "127.0.0.1"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("SHELF_IN_USE", ex.getCode());
        verify(shelfRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Xoá kệ trống thành công")
    void deleteShelf_Empty_Success() {
        Warehouse warehouse = warehouse(1L, "KHO-A", "Kho A");
        Shelf shelf = shelf(9L, warehouse, "A02");
        when(shelfRepository.findById(9L)).thenReturn(Optional.of(shelf));
        when(shelfRepository.countBookCopiesOnShelf(9L)).thenReturn(0L);

        service.deleteShelf(9L, 5L, "127.0.0.1");

        verify(shelfRepository).delete(shelf);
        verify(auditLogRepository).insert(eq(5L), eq("SHELF_DELETED"), eq("SHELF"), eq("9"), anyString(), eq("127.0.0.1"));
    }

    @Test
    @DisplayName("Từ chối lịch tuần thiếu ngày")
    void updateSchedule_MissingDay_ThrowsBadRequest() {
        List<WeeklyScheduleItemRequest> sixDays = new ArrayList<>();
        for (int day = 1; day <= 6; day++) {
            sixDays.add(new WeeklyScheduleItemRequest(day, true, LocalTime.of(8, 0), LocalTime.of(17, 0)));
        }

        ApiException ex = assertThrows(ApiException.class, () ->
                service.updateWeeklySchedule(new WeeklyScheduleUpdateRequest(sixDays), 5L, "127.0.0.1"));

        assertEquals("WEEKLY_SCHEDULE_REQUIRES_7_DAYS", ex.getCode());
    }

    @Test
    @DisplayName("Hạn trả ở ngày mở cửa được giữ nguyên")
    void adjustDueDate_OpenDay_Unchanged() {
        when(weeklyScheduleRepository.findAllByOrderByDayOfWeekAsc()).thenReturn(defaultSchedule());
        when(closedDateRepository.findAllByOrderByClosedDateAsc()).thenReturn(List.of());

        DueDateAdjustmentResponse response = service.adjustDueDate(LocalDate.of(2026, 9, 28)); // Thứ Hai

        assertFalse(response.adjusted());
        assertEquals(LocalDate.of(2026, 9, 28), response.adjustedDate());
        assertTrue(response.skippedClosedDates().isEmpty());
    }

    @Test
    @DisplayName("Ngày nghỉ cụ thể ưu tiên hơn lịch tuần")
    void adjustDueDate_SpecialHoliday_WinsOverWeeklyOpen() {
        when(weeklyScheduleRepository.findAllByOrderByDayOfWeekAsc()).thenReturn(defaultSchedule());
        when(closedDateRepository.findAllByOrderByClosedDateAsc())
                .thenReturn(List.of(closedDate(LocalDate.of(2026, 9, 28), "Nghỉ riêng")));

        DueDateAdjustmentResponse response = service.adjustDueDate(LocalDate.of(2026, 9, 28));

        assertTrue(response.adjusted());
        assertEquals(LocalDate.of(2026, 9, 29), response.adjustedDate());
        assertEquals(List.of(LocalDate.of(2026, 9, 28)), response.skippedClosedDates());
    }

    @Test
    @DisplayName("Hạn trả bỏ qua nhiều ngày đóng cửa liên tiếp")
    void adjustDueDate_ConsecutiveClosedDays_MovesToNextOpenDay() {
        List<LibraryWeeklySchedule> schedule = defaultSchedule(); // Chủ Nhật đóng
        when(weeklyScheduleRepository.findAllByOrderByDayOfWeekAsc()).thenReturn(schedule);
        when(closedDateRepository.findAllByOrderByClosedDateAsc()).thenReturn(List.of(
                closedDate(LocalDate.of(2026, 9, 26), "Nghỉ đặc biệt Thứ Bảy"),
                closedDate(LocalDate.of(2026, 9, 28), "Nghỉ bù Thứ Hai")
        ));

        DueDateAdjustmentResponse response = service.adjustDueDate(LocalDate.of(2026, 9, 26));

        assertEquals(LocalDate.of(2026, 9, 29), response.adjustedDate());
        assertEquals(3, response.skippedClosedDates().size());
    }

    @Test
    @DisplayName("Hạn trả cuối năm chuyển đúng sang ngày mở cửa đầu năm sau")
    void adjustDueDate_EndOfYear_CrossesYearBoundary() {
        when(weeklyScheduleRepository.findAllByOrderByDayOfWeekAsc()).thenReturn(defaultSchedule());
        when(closedDateRepository.findAllByOrderByClosedDateAsc()).thenReturn(List.of(
                closedDate(LocalDate.of(2026, 12, 31), "Nghỉ cuối năm"),
                closedDate(LocalDate.of(2027, 1, 1), "Tết Dương lịch")
        ));

        DueDateAdjustmentResponse response = service.adjustDueDate(LocalDate.of(2026, 12, 31));

        // 02/01/2027 là Thứ Bảy và lịch mặc định vẫn mở.
        assertEquals(LocalDate.of(2027, 1, 2), response.adjustedDate());
        assertTrue(response.adjusted());
    }

    private List<LibraryWeeklySchedule> defaultSchedule() {
        List<LibraryWeeklySchedule> list = new ArrayList<>();
        for (int day = 1; day <= 7; day++) {
            LibraryWeeklySchedule item = new LibraryWeeklySchedule();
            item.setId((long) day);
            item.setDayOfWeek(day);
            item.setOpen(day != 7);
            item.setOpenTime(day != 7 ? LocalTime.of(8, 0) : null);
            item.setCloseTime(day != 7 ? LocalTime.of(17, 0) : null);
            list.add(item);
        }
        return list;
    }

    private Warehouse warehouse(Long id, String code, String name) {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(id);
        warehouse.setCode(code);
        warehouse.setName(name);
        return warehouse;
    }

    private Shelf shelf(Long id, Warehouse warehouse, String code) {
        Shelf shelf = new Shelf();
        shelf.setId(id);
        shelf.setWarehouse(warehouse);
        shelf.setCode(code);
        shelf.setName(code);
        return shelf;
    }

    private LibraryClosedDate closedDate(LocalDate date, String reason) {
        LibraryClosedDate closedDate = new LibraryClosedDate();
        closedDate.setClosedDate(date);
        closedDate.setReason(reason);
        return closedDate;
    }
}
