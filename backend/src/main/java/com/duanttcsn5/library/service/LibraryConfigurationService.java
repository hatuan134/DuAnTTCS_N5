package com.duanttcsn5.library.service;

import com.duanttcsn5.library.dto.loan.LoanDatePreviewResponse;
import com.duanttcsn5.library.dto.libraryconfig.BulkClosedDatesRequest;
import com.duanttcsn5.library.dto.libraryconfig.ClosedDateRequest;
import com.duanttcsn5.library.dto.libraryconfig.ClosedDateResponse;
import com.duanttcsn5.library.dto.libraryconfig.DueDateAdjustmentResponse;
import com.duanttcsn5.library.dto.libraryconfig.ShelfRequest;
import com.duanttcsn5.library.dto.libraryconfig.ShelfResponse;
import com.duanttcsn5.library.dto.libraryconfig.WarehouseRequest;
import com.duanttcsn5.library.dto.libraryconfig.WarehouseResponse;
import com.duanttcsn5.library.dto.libraryconfig.WeeklyScheduleItemRequest;
import com.duanttcsn5.library.dto.libraryconfig.WeeklyScheduleResponse;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class LibraryConfigurationService {

    private final WarehouseRepository warehouseRepository;
    private final ShelfRepository shelfRepository;
    private final LibraryWeeklyScheduleRepository weeklyScheduleRepository;
    private final LibraryClosedDateRepository closedDateRepository;
    private final AuditLogRepository auditLogRepository;

    public LibraryConfigurationService(
            WarehouseRepository warehouseRepository,
            ShelfRepository shelfRepository,
            LibraryWeeklyScheduleRepository weeklyScheduleRepository,
            LibraryClosedDateRepository closedDateRepository,
            AuditLogRepository auditLogRepository) {
        this.warehouseRepository = warehouseRepository;
        this.shelfRepository = shelfRepository;
        this.weeklyScheduleRepository = weeklyScheduleRepository;
        this.closedDateRepository = closedDateRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public List<WarehouseResponse> getWarehouses() {
        return warehouseRepository.findAllByOrderByNameAsc().stream()
                .map(warehouse -> WarehouseResponse.fromEntity(
                        warehouse,
                        shelfRepository.countByWarehouse_Id(warehouse.getId()),
                        shelfRepository.countBookCopiesInWarehouse(warehouse.getId())))
                .toList();
    }

    @Transactional
    public WarehouseResponse createWarehouse(
            WarehouseRequest request,
            Long actorUserId,
            String ipAddress) {
        String code = normalizeCode(request.code());
        String name = request.name().trim();

        validateWarehouseUnique(code, name, null);

        Warehouse warehouse = new Warehouse();
        warehouse.setCode(code);
        warehouse.setName(name);
        warehouse.setDescription(normalizeNullable(request.description()));
        warehouse.setActive(true);

        Warehouse saved = warehouseRepository.save(warehouse);

        auditLogRepository.insert(
                actorUserId,
                "WAREHOUSE_CREATED",
                "WAREHOUSE",
                saved.getId().toString(),
                "{\"code\":\"" + escapeJson(saved.getCode()) + "\",\"name\":\"" + escapeJson(saved.getName()) + "\"}",
                ipAddress);

        return WarehouseResponse.fromEntity(saved, 0, 0);
    }

    @Transactional
    public WarehouseResponse updateWarehouse(
            Long id,
            WarehouseRequest request,
            Long actorUserId,
            String ipAddress) {
        Warehouse warehouse = getWarehouseEntity(id);
        String code = normalizeCode(request.code());
        String name = request.name().trim();

        validateWarehouseUnique(code, name, id);

        String beforeCode = warehouse.getCode();
        String beforeName = warehouse.getName();

        warehouse.setCode(code);
        warehouse.setName(name);
        warehouse.setDescription(normalizeNullable(request.description()));

        Warehouse saved = warehouseRepository.save(warehouse);

        auditLogRepository.insert(
                actorUserId,
                "WAREHOUSE_UPDATED",
                "WAREHOUSE",
                saved.getId().toString(),
                "{\"beforeCode\":\"" + escapeJson(beforeCode) + "\",\"afterCode\":\"" + escapeJson(saved.getCode()) +
                        "\",\"beforeName\":\"" + escapeJson(beforeName) + "\",\"afterName\":\"" + escapeJson(saved.getName()) + "\"}",
                ipAddress);

        return WarehouseResponse.fromEntity(
                saved,
                shelfRepository.countByWarehouse_Id(saved.getId()),
                shelfRepository.countBookCopiesInWarehouse(saved.getId()));
    }

    @Transactional(readOnly = true)
    public List<ShelfResponse> getShelves(Long warehouseId) {
        List<Shelf> shelves = warehouseId == null
                ? shelfRepository.findAllByOrderByWarehouse_NameAscCodeAsc()
                : shelfRepository.findAllByWarehouse_IdOrderByCodeAsc(warehouseId);

        return shelves.stream()
                .map(shelf -> ShelfResponse.fromEntity(
                        shelf,
                        shelfRepository.countBookCopiesOnShelf(shelf.getId())))
                .toList();
    }

    @Transactional
    public ShelfResponse createShelf(
            ShelfRequest request,
            Long actorUserId,
            String ipAddress) {
        Warehouse warehouse = getWarehouseEntity(request.warehouseId());
        String code = normalizeCode(request.code());

        if (shelfRepository.existsByWarehouse_IdAndCodeIgnoreCase(warehouse.getId(), code)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "SHELF_CODE_EXISTS",
                    "Mã kệ '" + code + "' đã tồn tại trong kho '" + warehouse.getName() + "'.");
        }

        Shelf shelf = new Shelf();
        shelf.setWarehouse(warehouse);
        shelf.setCode(code);
        shelf.setName(request.name().trim());
        shelf.setDescription(normalizeNullable(request.description()));
        shelf.setActive(true);

        Shelf saved = shelfRepository.save(shelf);

        auditLogRepository.insert(
                actorUserId,
                "SHELF_CREATED",
                "SHELF",
                saved.getId().toString(),
                "{\"warehouseId\":" + warehouse.getId() + ",\"code\":\"" + escapeJson(code) + "\",\"name\":\"" + escapeJson(saved.getName()) + "\"}",
                ipAddress);

        return ShelfResponse.fromEntity(saved, 0);
    }

    @Transactional
    public ShelfResponse updateShelf(
            Long id,
            ShelfRequest request,
            Long actorUserId,
            String ipAddress) {
        Shelf shelf = shelfRepository.findById(id)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "SHELF_NOT_FOUND",
                        "Không tìm thấy kệ ID: " + id));
        Warehouse warehouse = getWarehouseEntity(request.warehouseId());
        String code = normalizeCode(request.code());

        if (shelfRepository.existsByWarehouse_IdAndCodeIgnoreCaseAndIdNot(warehouse.getId(), code, id)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "SHELF_CODE_EXISTS",
                    "Mã kệ '" + code + "' đã tồn tại trong kho '" + warehouse.getName() + "'.");
        }

        Long oldWarehouseId = shelf.getWarehouse().getId();
        String oldCode = shelf.getCode();

        shelf.setWarehouse(warehouse);
        shelf.setCode(code);
        shelf.setName(request.name().trim());
        shelf.setDescription(normalizeNullable(request.description()));

        Shelf saved = shelfRepository.save(shelf);
        long copyCount = shelfRepository.countBookCopiesOnShelf(saved.getId());

        auditLogRepository.insert(
                actorUserId,
                "SHELF_UPDATED",
                "SHELF",
                saved.getId().toString(),
                "{\"beforeWarehouseId\":" + oldWarehouseId + ",\"afterWarehouseId\":" + warehouse.getId() +
                        ",\"beforeCode\":\"" + escapeJson(oldCode) + "\",\"afterCode\":\"" + escapeJson(saved.getCode()) + "\"}",
                ipAddress);

        return ShelfResponse.fromEntity(saved, copyCount);
    }

    @Transactional
    public void deleteShelf(
            Long id,
            Long actorUserId,
            String ipAddress) {
        Shelf shelf = shelfRepository.findById(id)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "SHELF_NOT_FOUND",
                        "Không tìm thấy kệ ID: " + id));

        long copyCount = shelfRepository.countBookCopiesOnShelf(id);
        if (copyCount > 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "SHELF_IN_USE",
                    "Không thể xoá kệ '" + shelf.getCode() + "' vì đang có " + copyCount + " bản sao sách.");
        }

        String code = shelf.getCode();
        Long warehouseId = shelf.getWarehouse().getId();
        shelfRepository.delete(shelf);

        auditLogRepository.insert(
                actorUserId,
                "SHELF_DELETED",
                "SHELF",
                id.toString(),
                "{\"warehouseId\":" + warehouseId + ",\"code\":\"" + escapeJson(code) + "\"}",
                ipAddress);
    }

    @Transactional(readOnly = true)
    public List<WeeklyScheduleResponse> getWeeklySchedule() {
        return weeklyScheduleRepository.findAllByOrderByDayOfWeekAsc().stream()
                .map(WeeklyScheduleResponse::fromEntity)
                .toList();
    }

    @Transactional
    public List<WeeklyScheduleResponse> updateWeeklySchedule(
            WeeklyScheduleUpdateRequest request,
            Long actorUserId,
            String ipAddress) {
        validateCompleteWeeklySchedule(request.days());

        Map<Integer, LibraryWeeklySchedule> existing = new HashMap<>();
        for (LibraryWeeklySchedule item : weeklyScheduleRepository.findAll()) {
            existing.put(item.getDayOfWeek(), item);
        }

        OffsetDateTime now = OffsetDateTime.now();
        List<LibraryWeeklySchedule> toSave = new ArrayList<>();

        for (WeeklyScheduleItemRequest item : request.days()) {
            validateScheduleItem(item);

            LibraryWeeklySchedule schedule = existing.getOrDefault(item.dayOfWeek(), new LibraryWeeklySchedule());
            schedule.setDayOfWeek(item.dayOfWeek());
            schedule.setOpen(item.open());
            schedule.setOpenTime(item.open() ? item.openTime() : null);
            schedule.setCloseTime(item.open() ? item.closeTime() : null);
            schedule.setUpdatedAt(now);
            schedule.setUpdatedBy(actorUserId);
            toSave.add(schedule);
        }

        List<LibraryWeeklySchedule> saved = weeklyScheduleRepository.saveAll(toSave);

        auditLogRepository.insert(
                actorUserId,
                "WEEKLY_SCHEDULE_UPDATED",
                "LIBRARY_WEEKLY_SCHEDULE",
                "WEEKLY",
                "{\"daysUpdated\":7}",
                ipAddress);

        return saved.stream()
                .sorted((a, b) -> Integer.compare(a.getDayOfWeek(), b.getDayOfWeek()))
                .map(WeeklyScheduleResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ClosedDateResponse> getClosedDates(Integer year) {
        List<LibraryClosedDate> values;
        if (year == null) {
            values = closedDateRepository.findAllByOrderByClosedDateAsc();
        } else {
            values = closedDateRepository.findAllByClosedDateBetweenOrderByClosedDateAsc(
                    LocalDate.of(year, 1, 1),
                    LocalDate.of(year, 12, 31));
        }

        return values.stream().map(ClosedDateResponse::fromEntity).toList();
    }

    @Transactional
    public ClosedDateResponse createClosedDate(
            ClosedDateRequest request,
            Long actorUserId,
            String ipAddress) {
        if (closedDateRepository.existsByClosedDate(request.closedDate())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "CLOSED_DATE_EXISTS",
                    "Ngày " + request.closedDate() + " đã được khai báo là ngày đóng cửa.");
        }

        LibraryClosedDate closedDate = new LibraryClosedDate();
        closedDate.setClosedDate(request.closedDate());
        closedDate.setReason(request.reason().trim());
        closedDate.setCreatedBy(actorUserId);

        LibraryClosedDate saved = closedDateRepository.save(closedDate);

        auditLogRepository.insert(
                actorUserId,
                "CLOSED_DATE_CREATED",
                "LIBRARY_CLOSED_DATE",
                saved.getId().toString(),
                "{\"closedDate\":\"" + saved.getClosedDate() + "\",\"reason\":\"" + escapeJson(saved.getReason()) + "\"}",
                ipAddress);

        return ClosedDateResponse.fromEntity(saved);
    }

    @Transactional
    public ClosedDateResponse updateClosedDate(
            Long id,
            ClosedDateRequest request,
            Long actorUserId,
            String ipAddress) {
        LibraryClosedDate closedDate = closedDateRepository.findById(id)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "CLOSED_DATE_NOT_FOUND",
                        "Không tìm thấy ngày đóng cửa ID: " + id));

        if (closedDateRepository.existsByClosedDateAndIdNot(request.closedDate(), id)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "CLOSED_DATE_EXISTS",
                    "Ngày " + request.closedDate() + " đã được khai báo là ngày đóng cửa.");
        }

        LocalDate before = closedDate.getClosedDate();
        closedDate.setClosedDate(request.closedDate());
        closedDate.setReason(request.reason().trim());
        LibraryClosedDate saved = closedDateRepository.save(closedDate);

        auditLogRepository.insert(
                actorUserId,
                "CLOSED_DATE_UPDATED",
                "LIBRARY_CLOSED_DATE",
                saved.getId().toString(),
                "{\"beforeDate\":\"" + before + "\",\"afterDate\":\"" + saved.getClosedDate() +
                        "\",\"reason\":\"" + escapeJson(saved.getReason()) + "\"}",
                ipAddress);

        return ClosedDateResponse.fromEntity(saved);
    }

    @Transactional
    public void deleteClosedDate(Long id, Long actorUserId, String ipAddress) {
        LibraryClosedDate closedDate = closedDateRepository.findById(id)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "CLOSED_DATE_NOT_FOUND",
                        "Không tìm thấy ngày đóng cửa ID: " + id));

        LocalDate date = closedDate.getClosedDate();
        String reason = closedDate.getReason();
        closedDateRepository.delete(closedDate);

        auditLogRepository.insert(
                actorUserId,
                "CLOSED_DATE_DELETED",
                "LIBRARY_CLOSED_DATE",
                id.toString(),
                "{\"closedDate\":\"" + date + "\",\"reason\":\"" + escapeJson(reason) + "\"}",
                ipAddress);
    }

    @Transactional
    public List<ClosedDateResponse> createClosedDatesBulk(
            BulkClosedDatesRequest request,
            Long actorUserId,
            String ipAddress) {
        Set<LocalDate> uniqueDates = new HashSet<>();
        for (ClosedDateRequest item : request.dates()) {
            if (!uniqueDates.add(item.closedDate())) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "DUPLICATE_CLOSED_DATE_IN_REQUEST",
                        "Ngày " + item.closedDate() + " bị lặp trong danh sách khai báo.");
            }
            if (closedDateRepository.existsByClosedDate(item.closedDate())) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "CLOSED_DATE_EXISTS",
                        "Ngày " + item.closedDate() + " đã được khai báo trước đó.");
            }
        }

        List<LibraryClosedDate> entities = request.dates().stream().map(item -> {
            LibraryClosedDate value = new LibraryClosedDate();
            value.setClosedDate(item.closedDate());
            value.setReason(item.reason().trim());
            value.setCreatedBy(actorUserId);
            return value;
        }).toList();

        List<LibraryClosedDate> saved = closedDateRepository.saveAll(entities);

        auditLogRepository.insert(
                actorUserId,
                "CLOSED_DATES_BULK_CREATED",
                "LIBRARY_CLOSED_DATE",
                "BULK",
                "{\"count\":" + saved.size() + "}",
                ipAddress);

        return saved.stream()
                .sorted((a, b) -> a.getClosedDate().compareTo(b.getClosedDate()))
                .map(ClosedDateResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public DueDateAdjustmentResponse adjustDueDate(LocalDate originalDate) {
        return moveToOpenDate(originalDate, loadWeeklySchedule(), loadClosedDates());
    }

    /** S3-01.2: count calendar days, then move only the final date to an open day. */
    @Transactional(readOnly = true, noRollbackFor = ApiException.class)
    public LoanDatePreviewResponse calculateLoanDates(OffsetDateTime borrowedAt, int loanDays, String cardTypeName) {
        // Preview catches expected configuration errors to show them in the detail view.
        // This read-only calculation must not mark that outer transaction rollback-only.
        if (borrowedAt == null || loanDays < 1 || loanDays > 60) {
            throw new ApiException(HttpStatus.CONFLICT, "LOAN_POLICY_NOT_CONFIGURED",
                    "Loại thẻ chưa có số ngày mượn hợp lệ từ 1 đến 60. Vui lòng cấu hình chính sách mượn.");
        }
        Map<Integer, LibraryWeeklySchedule> schedule = loadWeeklySchedule();
        for (LibraryWeeklySchedule day : schedule.values()) {
            if (day.isOpen() && (day.getOpenTime() == null || day.getCloseTime() == null
                    || !day.getCloseTime().isAfter(day.getOpenTime()))) {
                throw new ApiException(HttpStatus.CONFLICT, "WEEKLY_SCHEDULE_INVALID",
                        "Ngày mở cửa chưa có giờ mở và đóng cửa hợp lệ. Vui lòng kiểm tra lịch thư viện.");
            }
        }
        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
        LocalDate borrowDate = borrowedAt.atZoneSameInstant(zone).toLocalDate();
        LocalDate originalDueDate = borrowDate.plusDays(loanDays);
        DueDateAdjustmentResponse adjusted = moveToOpenDate(originalDueDate, schedule, loadClosedDates());
        OffsetDateTime dueAt = adjusted.adjustedDate()
                .atTime(schedule.get(adjusted.adjustedDate().getDayOfWeek().getValue()).getCloseTime())
                .atZone(zone).toOffsetDateTime();
        return new LoanDatePreviewResponse(borrowDate, cardTypeName, loanDays, originalDueDate,
                adjusted.adjustedDate(), dueAt, adjusted.adjusted(), adjusted.skippedClosedDates());
    }

    private Map<Integer, LibraryWeeklySchedule> loadWeeklySchedule() {
        Map<Integer, LibraryWeeklySchedule> byDay = new HashMap<>();
        for (LibraryWeeklySchedule day : weeklyScheduleRepository.findAllByOrderByDayOfWeekAsc()) {
            int number = day.getDayOfWeek();
            if (number < 1 || number > 7 || byDay.put(number, day) != null) {
                throw new ApiException(HttpStatus.CONFLICT, "WEEKLY_SCHEDULE_INCOMPLETE",
                        "Lịch làm việc theo tuần chưa được cấu hình đúng 7 ngày.");
            }
        }
        if (byDay.size() != 7) throw new ApiException(HttpStatus.CONFLICT, "WEEKLY_SCHEDULE_INCOMPLETE",
                "Lịch làm việc theo tuần chưa được cấu hình đủ 7 ngày.");
        if (byDay.values().stream().noneMatch(LibraryWeeklySchedule::isOpen)) {
            throw new ApiException(HttpStatus.CONFLICT, "NEXT_OPEN_DATE_NOT_FOUND",
                    "Thư viện chưa có ngày mở cửa. Không thể tính hạn trả; vui lòng cấu hình lịch thư viện.");
        }
        return byDay;
    }

    private Set<LocalDate> loadClosedDates() {
        return closedDateRepository.findAllByOrderByClosedDateAsc().stream()
                .map(LibraryClosedDate::getClosedDate).collect(java.util.stream.Collectors.toSet());
    }

    private DueDateAdjustmentResponse moveToOpenDate(LocalDate originalDate,
            Map<Integer, LibraryWeeklySchedule> schedule, Set<LocalDate> explicitlyClosed) {
        LocalDate candidate = originalDate;
        List<LocalDate> skipped = new ArrayList<>();
        // At least one weekday is open and the special closure set is finite.
        // Continue through every closed date instead of imposing an arbitrary day limit.
        while (!schedule.get(candidate.getDayOfWeek().getValue()).isOpen() || explicitlyClosed.contains(candidate)) {
            skipped.add(candidate);
            candidate = candidate.plusDays(1);
        }
        return new DueDateAdjustmentResponse(originalDate, candidate,
                !candidate.equals(originalDate), List.copyOf(skipped));
    }

    /**
     * S2-07.2 policy: exclude the creation date, count three subsequent open
     * dates, and use the third date's closing time in the library timezone.
     * A specific closed date always overrides the weekly opening schedule.
     */
    @Transactional(readOnly = true)
    public OffsetDateTime calculateReservationPickupDeadline(OffsetDateTime createdAt) {
        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
        Map<Integer, LibraryWeeklySchedule> byDay = new HashMap<>();
        List<LibraryWeeklySchedule> schedule = weeklyScheduleRepository.findAllByOrderByDayOfWeekAsc();
        for (LibraryWeeklySchedule day : schedule) {
            int number = day.getDayOfWeek();
            if (number < 1 || number > 7 || byDay.put(number, day) != null) {
                throw new ApiException(HttpStatus.CONFLICT, "WEEKLY_SCHEDULE_INCOMPLETE",
                        "Lịch làm việc theo tuần chưa được cấu hình đúng 7 ngày.");
            }
            if (day.isOpen() && (day.getOpenTime() == null || day.getCloseTime() == null
                    || !day.getCloseTime().isAfter(day.getOpenTime()))) {
                throw new ApiException(HttpStatus.CONFLICT, "WEEKLY_SCHEDULE_INVALID",
                        "Ngày mở cửa chưa có giờ mở và đóng cửa hợp lệ. Vui lòng kiểm tra lịch thư viện.");
            }
        }
        if (byDay.size() != 7) {
            throw new ApiException(HttpStatus.CONFLICT, "WEEKLY_SCHEDULE_INCOMPLETE",
                    "Lịch làm việc theo tuần chưa được cấu hình đủ 7 ngày.");
        }
        if (schedule.stream().noneMatch(LibraryWeeklySchedule::isOpen)) {
            throw new ApiException(HttpStatus.CONFLICT, "PICKUP_DEADLINE_NOT_FOUND",
                    "Thư viện chưa có ngày mở cửa. Không thể xác định hạn đến nhận sách.");
        }
        Set<LocalDate> closedDates = closedDateRepository.findAllByOrderByClosedDateAsc().stream()
                .map(LibraryClosedDate::getClosedDate)
                .collect(java.util.stream.Collectors.toSet());
        LocalDate candidate = createdAt.atZoneSameInstant(zone).toLocalDate();
        int openDays = 0;
        // With finite special closures and at least one open weekday this loop terminates.
        while (openDays < 3) {
            candidate = candidate.plusDays(1);
            LibraryWeeklySchedule day = byDay.get(candidate.getDayOfWeek().getValue());
            if (day.isOpen() && !closedDates.contains(candidate)) {
                openDays++;
                if (openDays == 3) {
                    return candidate.atTime(day.getCloseTime()).atZone(zone).toOffsetDateTime();
                }
            }
        }
        throw new IllegalStateException("Không thể xác định hạn đến nhận sách.");
    }

    private Warehouse getWarehouseEntity(Long id) {
        return warehouseRepository.findById(id)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "WAREHOUSE_NOT_FOUND",
                        "Không tìm thấy kho ID: " + id));
    }

    private void validateWarehouseUnique(String code, String name, Long currentId) {
        boolean codeExists = currentId == null
                ? warehouseRepository.existsByCodeIgnoreCase(code)
                : warehouseRepository.existsByCodeIgnoreCaseAndIdNot(code, currentId);
        if (codeExists) {
            throw new ApiException(HttpStatus.CONFLICT, "WAREHOUSE_CODE_EXISTS", "Mã kho '" + code + "' đã tồn tại.");
        }

        boolean nameExists = currentId == null
                ? warehouseRepository.existsByNameIgnoreCase(name)
                : warehouseRepository.existsByNameIgnoreCaseAndIdNot(name, currentId);
        if (nameExists) {
            throw new ApiException(HttpStatus.CONFLICT, "WAREHOUSE_NAME_EXISTS", "Tên kho '" + name + "' đã tồn tại.");
        }
    }

    private void validateCompleteWeeklySchedule(List<WeeklyScheduleItemRequest> days) {
        if (days == null || days.size() != 7) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "WEEKLY_SCHEDULE_REQUIRES_7_DAYS",
                    "Lịch làm việc phải khai báo đủ 7 ngày trong tuần.");
        }

        Set<Integer> seen = new HashSet<>();
        for (WeeklyScheduleItemRequest item : days) {
            if (item.dayOfWeek() < 1 || item.dayOfWeek() > 7 || !seen.add(item.dayOfWeek())) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "WEEKLY_SCHEDULE_INVALID_DAYS",
                        "Mỗi thứ từ 1 đến 7 chỉ được xuất hiện đúng một lần.");
            }
        }
    }

    private void validateScheduleItem(WeeklyScheduleItemRequest item) {
        if (!item.open()) {
            return;
        }

        LocalTime openTime = item.openTime();
        LocalTime closeTime = item.closeTime();
        if (openTime == null || closeTime == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "OPENING_TIME_REQUIRED",
                    "Ngày mở cửa phải có đầy đủ giờ mở cửa và giờ đóng cửa.");
        }
        if (!closeTime.isAfter(openTime)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_OPENING_HOURS",
                    "Giờ đóng cửa phải sau giờ mở cửa.");
        }
    }

    private String normalizeCode(String value) {
        return value.trim().toUpperCase();
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
