package com.duanttcsn5.library.service;

import com.duanttcsn5.library.dto.librarycard.ApproveLibraryCardRequest;
import com.duanttcsn5.library.dto.librarycard.LibraryCardResponse;
import com.duanttcsn5.library.dto.librarycard.MyLibraryCardResponse;
import com.duanttcsn5.library.dto.librarycard.PendingReaderApplicationResponse;
import com.duanttcsn5.library.dto.librarycard.RejectReaderApplicationRequest;
import com.duanttcsn5.library.entity.CardType;
import com.duanttcsn5.library.entity.LibraryCard;
import com.duanttcsn5.library.entity.ReaderProfile;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.AuditLogRepository;
import com.duanttcsn5.library.repository.CardTypeRepository;
import com.duanttcsn5.library.repository.LibraryCardRepository;
import com.duanttcsn5.library.repository.ReaderProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class LibraryCardService {

    private static final ZoneId LIBRARY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final ReaderProfileRepository readerProfileRepository;
    private final LibraryCardRepository libraryCardRepository;
    private final CardTypeRepository cardTypeRepository;
    private final AuditLogService auditLogService;
    private final AuditLogRepository auditLogRepository;

    public LibraryCardService(ReaderProfileRepository readerProfileRepository,
                              LibraryCardRepository libraryCardRepository,
                              CardTypeRepository cardTypeRepository,
                              AuditLogService auditLogService,
                              AuditLogRepository auditLogRepository) {
        this.readerProfileRepository = readerProfileRepository;
        this.libraryCardRepository = libraryCardRepository;
        this.cardTypeRepository = cardTypeRepository;
        this.auditLogService = auditLogService;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public List<PendingReaderApplicationResponse> getPendingApplications(
            String rawSearch,
            LocalDate fromDate,
            LocalDate toDate) {

        if (fromDate != null && toDate != null && toDate.isBefore(fromDate)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_DATE_RANGE",
                    "Ngày kết thúc không được trước ngày bắt đầu."
            );
        }

        String search = rawSearch == null
                ? ""
                : rawSearch.trim().toLowerCase(Locale.ROOT);

        return readerProfileRepository.findPendingWithUser().stream()
                .filter(profile -> matchesSearch(profile, search))
                .filter(profile -> isWithinSubmittedDateRange(profile, fromDate, toDate))
                .map(PendingReaderApplicationResponse::fromEntity)
                .toList();
    }

    private boolean matchesSearch(ReaderProfile profile, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }

        return containsIgnoreCase(profile.getUser().getFullName(), search)
                || containsIgnoreCase(profile.getMemberCode(), search)
                || containsIgnoreCase(profile.getUser().getEmail(), search);
    }

    private boolean containsIgnoreCase(String value, String normalizedSearch) {
        return value != null
                && value.toLowerCase(Locale.ROOT).contains(normalizedSearch);
    }

    private boolean isWithinSubmittedDateRange(
            ReaderProfile profile,
            LocalDate fromDate,
            LocalDate toDate) {

        if (profile.getSubmittedAt() == null) {
            return false;
        }

        LocalDate submittedDate = profile.getSubmittedAt()
                .atZoneSameInstant(LIBRARY_ZONE)
                .toLocalDate();

        if (fromDate != null && submittedDate.isBefore(fromDate)) {
            return false;
        }

        if (toDate != null && submittedDate.isAfter(toDate)) {
            return false;
        }

        return true;
    }

    @Transactional(readOnly = true)
    public List<LibraryCardResponse> getIssuedCards() {
        return libraryCardRepository.findAllWithDetails().stream()
                .map(card -> LibraryCardResponse.fromEntity(
                        card,
                        readerProfileRepository.findById(card.getUser().getId())
                                .map(ReaderProfile::getMemberCode)
                                .orElse("-")))
                .toList();
    }

    @Transactional
    public LibraryCardResponse approve(
            Long readerUserId,
            ApproveLibraryCardRequest request,
            Long librarianUserId,
            String ipAddress) {

        ReaderProfile profile = getPendingProfileForUpdate(readerUserId);

        if (libraryCardRepository.existsByUser_Id(readerUserId)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "LIBRARY_CARD_ALREADY_EXISTS",
                    "Bạn đọc này đã được cấp thẻ thư viện."
            );
        }

        CardType cardType = cardTypeRepository.findById(request.cardTypeId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "CARD_TYPE_NOT_FOUND",
                        "Không tìm thấy loại thẻ đã chọn."
                ));

        if (!cardType.isActive()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "CARD_TYPE_INACTIVE",
                    "Loại thẻ đã chọn đang ngừng áp dụng."
            );
        }

        LocalDate issuedAt = LocalDate.now(LIBRARY_ZONE);
        if (request.expiresAt().isBefore(issuedAt)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_CARD_EXPIRY",
                    "Ngày hết hạn phải bằng hoặc sau ngày cấp thẻ."
            );
        }

        LibraryCard card = new LibraryCard();
        card.setCardNumber(generateUniqueCardNumber(issuedAt));
        card.setUser(profile.getUser());
        card.setCardType(cardType);
        card.setIssuedAt(issuedAt);
        card.setExpiresAt(request.expiresAt());
        card.setStatus("ACTIVE");
        card.setCreatedBy(librarianUserId);

        LibraryCard savedCard = libraryCardRepository.saveAndFlush(card);

        profile.setRegistrationStatus("APPROVED");
        profile.setRejectionReason(null);
        profile.setReviewedAt(OffsetDateTime.now(LIBRARY_ZONE));
        profile.setReviewedBy(librarianUserId);
        readerProfileRepository.save(profile);

        auditLogService.logLibraryCardIssued(
                librarianUserId,
                savedCard.getId(),
                savedCard.getCardNumber(),
                readerUserId,
                profile.getUser().getFullName(),
                cardType.getName(),
                savedCard.getExpiresAt().toString(),
                ipAddress
        );

        return LibraryCardResponse.fromEntity(savedCard, profile.getMemberCode());
    }

    @Transactional
    public void reject(
            Long readerUserId,
            RejectReaderApplicationRequest request,
            Long librarianUserId,
            String ipAddress) {

        ReaderProfile profile = getPendingProfileForUpdate(readerUserId);
        String reason = request.reason() == null ? "" : request.reason().trim();
        if (reason.isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "REJECTION_REASON_REQUIRED",
                    "Lý do từ chối là bắt buộc."
            );
        }

        profile.setRegistrationStatus("REJECTED");
        profile.setRejectionReason(reason);
        profile.setReviewedAt(OffsetDateTime.now(LIBRARY_ZONE));
        profile.setReviewedBy(librarianUserId);
        readerProfileRepository.save(profile);

        auditLogRepository.insert(
                librarianUserId,
                "READER_APPLICATION_REJECTED",
                "READER_PROFILE",
                readerUserId.toString(),
                "{\"readerName\":\"" + escapeJson(profile.getUser().getFullName()) +
                        "\",\"memberCode\":\"" + escapeJson(profile.getMemberCode()) +
                        "\",\"reason\":\"" + escapeJson(reason) + "\"}",
                ipAddress
        );
    }

    @Transactional(readOnly = true)
    public MyLibraryCardResponse getMyCard(Long userId) {
        ReaderProfile profile = readerProfileRepository.findById(userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "READER_PROFILE_NOT_FOUND",
                        "Không tìm thấy hồ sơ bạn đọc của tài khoản hiện tại."
                ));

        return libraryCardRepository.findByUserIdWithDetails(userId)
                .map(card -> new MyLibraryCardResponse(
                        profile.getUserId(),
                        profile.getUser().getFullName(),
                        profile.getMemberCode(),
                        profile.getDateOfBirth(),
                        profile.getRegistrationStatus(),
                        profile.getRejectionReason(),
                        card.getCardNumber(),
                        card.getCardType().getName(),
                        card.getIssuedAt(),
                        card.getExpiresAt(),
                        card.getStatus()))
                .orElseGet(() -> new MyLibraryCardResponse(
                        profile.getUserId(),
                        profile.getUser().getFullName(),
                        profile.getMemberCode(),
                        profile.getDateOfBirth(),
                        profile.getRegistrationStatus(),
                        profile.getRejectionReason(),
                        null,
                        null,
                        null,
                        null,
                        null));
    }

    private ReaderProfile getPendingProfileForUpdate(Long readerUserId) {
        ReaderProfile profile = readerProfileRepository.findByIdForUpdate(readerUserId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "READER_PROFILE_NOT_FOUND",
                        "Không tìm thấy hồ sơ bạn đọc."
                ));

        if (!"PENDING".equals(profile.getRegistrationStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "READER_APPLICATION_ALREADY_REVIEWED",
                    "Hồ sơ này đã được xử lý và không thể duyệt hoặc từ chối lại."
            );
        }
        return profile;
    }

    private String generateUniqueCardNumber(LocalDate issuedAt) {
        for (int attempt = 0; attempt < 10; attempt++) {
            String suffix = UUID.randomUUID()
                    .toString()
                    .replace("-", "")
                    .substring(0, 8)
                    .toUpperCase(Locale.ROOT);
            String cardNumber = "LIB-" + issuedAt.getYear() + "-" + suffix;
            if (!libraryCardRepository.existsByCardNumber(cardNumber)) {
                return cardNumber;
            }
        }

        throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "CARD_NUMBER_GENERATION_FAILED",
                "Không thể sinh mã thẻ duy nhất. Vui lòng thử lại."
        );
    }

    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
