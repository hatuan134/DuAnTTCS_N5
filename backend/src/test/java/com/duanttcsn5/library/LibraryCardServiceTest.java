package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.librarycard.ApproveLibraryCardRequest;
import com.duanttcsn5.library.dto.librarycard.RejectReaderApplicationRequest;
import com.duanttcsn5.library.entity.CardType;
import com.duanttcsn5.library.entity.LibraryCard;
import com.duanttcsn5.library.entity.ReaderProfile;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.AuditLogRepository;
import com.duanttcsn5.library.repository.CardTypeRepository;
import com.duanttcsn5.library.repository.LibraryCardRepository;
import com.duanttcsn5.library.repository.ReaderProfileRepository;
import com.duanttcsn5.library.service.AuditLogService;
import com.duanttcsn5.library.service.LibraryCardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LibraryCardServiceTest {

    @Mock ReaderProfileRepository readerProfileRepository;
    @Mock LibraryCardRepository libraryCardRepository;
    @Mock CardTypeRepository cardTypeRepository;
    @Mock AuditLogService auditLogService;
    @Mock AuditLogRepository auditLogRepository;

    private LibraryCardService service;

    @BeforeEach
    void setUp() {
        service = new LibraryCardService(
                readerProfileRepository,
                libraryCardRepository,
                cardTypeRepository,
                auditLogService,
                auditLogRepository);
    }

    @Test
    @DisplayName("Duyệt hồ sơ tạo thẻ ACTIVE, sinh mã duy nhất và chuyển hồ sơ sang APPROVED")
    void approve_Success() {
        ReaderProfile profile = pendingProfile(10L, "Nguyễn Văn A", "DTC001");
        CardType type = cardType(2L, "Thẻ sinh viên", true);

        when(readerProfileRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(profile));
        when(libraryCardRepository.existsByUser_Id(10L)).thenReturn(false);
        when(cardTypeRepository.findById(2L)).thenReturn(Optional.of(type));
        when(libraryCardRepository.existsByCardNumber(anyString())).thenReturn(false);
        when(libraryCardRepository.saveAndFlush(any(LibraryCard.class))).thenAnswer(invocation -> {
            LibraryCard card = invocation.getArgument(0);
            card.setId(99L);
            return card;
        });

        LocalDate expiry = LocalDate.now().plusYears(1);
        var response = service.approve(
                10L,
                new ApproveLibraryCardRequest(2L, expiry),
                5L,
                "127.0.0.1");

        assertEquals("APPROVED", profile.getRegistrationStatus());
        assertEquals("ACTIVE", response.status());
        assertTrue(response.cardNumber().startsWith("LIB-"));
        assertEquals(expiry, response.expiresAt());
        verify(auditLogService).logLibraryCardIssued(
                eq(5L), eq(99L), eq(response.cardNumber()), eq(10L),
                eq("Nguyễn Văn A"), eq("Thẻ sinh viên"), eq(expiry.toString()), eq("127.0.0.1"));
    }

    @Test
    @DisplayName("Không cho duyệt lại hồ sơ đã xử lý")
    void approve_AlreadyReviewed_ThrowsConflict() {
        ReaderProfile profile = pendingProfile(10L, "Nguyễn Văn A", "DTC001");
        profile.setRegistrationStatus("APPROVED");
        when(readerProfileRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(profile));

        ApiException ex = assertThrows(ApiException.class, () -> service.approve(
                10L,
                new ApproveLibraryCardRequest(2L, LocalDate.now().plusYears(1)),
                5L,
                "127.0.0.1"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("READER_APPLICATION_ALREADY_REVIEWED", ex.getCode());
        verify(libraryCardRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Không cho cấp loại thẻ đang ngừng áp dụng")
    void approve_InactiveCardType_ThrowsBadRequest() {
        ReaderProfile profile = pendingProfile(10L, "Nguyễn Văn A", "DTC001");
        when(readerProfileRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(profile));
        when(libraryCardRepository.existsByUser_Id(10L)).thenReturn(false);
        when(cardTypeRepository.findById(2L)).thenReturn(Optional.of(cardType(2L, "Thẻ cũ", false)));

        ApiException ex = assertThrows(ApiException.class, () -> service.approve(
                10L,
                new ApproveLibraryCardRequest(2L, LocalDate.now().plusYears(1)),
                5L,
                "127.0.0.1"));

        assertEquals("CARD_TYPE_INACTIVE", ex.getCode());
    }

    @Test
    @DisplayName("Từ chối hồ sơ bắt buộc có lý do")
    void reject_BlankReason_ThrowsBadRequest() {
        ReaderProfile profile = pendingProfile(10L, "Nguyễn Văn A", "DTC001");
        when(readerProfileRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(profile));

        ApiException ex = assertThrows(ApiException.class, () -> service.reject(
                10L,
                new RejectReaderApplicationRequest("   "),
                5L,
                "127.0.0.1"));

        assertEquals("REJECTION_REASON_REQUIRED", ex.getCode());
        assertEquals("PENDING", profile.getRegistrationStatus());
    }

    @Test
    @DisplayName("Từ chối hồ sơ lưu lý do để bạn đọc xem ở trang cá nhân")
    void reject_Success() {
        ReaderProfile profile = pendingProfile(10L, "Nguyễn Văn A", "DTC001");
        when(readerProfileRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(profile));

        service.reject(
                10L,
                new RejectReaderApplicationRequest("Thiếu giấy tờ đối chiếu"),
                5L,
                "127.0.0.1");

        assertEquals("REJECTED", profile.getRegistrationStatus());
        assertEquals("Thiếu giấy tờ đối chiếu", profile.getRejectionReason());
        assertNotNull(profile.getReviewedAt());
        assertEquals(5L, profile.getReviewedBy());
        verify(auditLogRepository).insert(
                eq(5L), eq("READER_APPLICATION_REJECTED"), eq("READER_PROFILE"), eq("10"), anyString(), eq("127.0.0.1"));
    }

    @Test
    @DisplayName("getIssuedCards trả về EXPIRED nếu thẻ quá ngày hết hạn")
    void getIssuedCards_ExpiredCard_ResolvesExpiredStatus() {
        User user = new User();
        user.setId(20L);
        user.setFullName("Bạn Đọc Hết Hạn");
        user.setStatus("ACTIVE");

        CardType type = cardType(1L, "Thẻ cán bộ", true);

        LibraryCard card = new LibraryCard();
        card.setId(101L);
        card.setCardNumber("LIB-2026-EXPIRED");
        card.setUser(user);
        card.setCardType(type);
        card.setIssuedAt(LocalDate.now().minusMonths(2));
        card.setExpiresAt(LocalDate.now().minusDays(1)); // Quá hạn hôm qua
        card.setStatus("ACTIVE"); // Trong DB vẫn lưu ACTIVE

        when(libraryCardRepository.findAllWithDetails()).thenReturn(List.of(card));
        when(readerProfileRepository.findById(20L)).thenReturn(Optional.of(pendingProfile(20L, "Bạn Đọc Hết Hạn", "DTC020")));

        var results = service.getIssuedCards();

        assertEquals(1, results.size());
        assertEquals("EXPIRED", results.get(0).status());
    }

    @Test
    @DisplayName("getIssuedCards trả về LOCKED nếu tài khoản hoặc thẻ bị khóa")
    void getIssuedCards_LockedUserOrCard_ResolvesLockedStatus() {
        User user = new User();
        user.setId(21L);
        user.setFullName("Bạn Đọc Bị Khóa");
        user.setStatus("LOCKED"); // Tài khoản bị khóa

        CardType type = cardType(1L, "Thẻ cán bộ", true);

        LibraryCard card = new LibraryCard();
        card.setId(102L);
        card.setCardNumber("LIB-2026-LOCKED");
        card.setUser(user);
        card.setCardType(type);
        card.setIssuedAt(LocalDate.now());
        card.setExpiresAt(LocalDate.now().plusMonths(6));
        card.setStatus("ACTIVE");

        when(libraryCardRepository.findAllWithDetails()).thenReturn(List.of(card));
        when(readerProfileRepository.findById(21L)).thenReturn(Optional.of(pendingProfile(21L, "Bạn Đọc Bị Khóa", "DTC021")));

        var results = service.getIssuedCards();

        assertEquals(1, results.size());
        assertEquals("LOCKED", results.get(0).status());
    }

    @Test
    @DisplayName("getIssuedCards trả về ACTIVE nếu thẻ còn hạn và tài khoản hoạt động bình thường")
    void getIssuedCards_ValidCard_ResolvesActiveStatus() {
        User user = new User();
        user.setId(22L);
        user.setFullName("Bạn Đọc Bình Thường");
        user.setStatus("ACTIVE");

        CardType type = cardType(1L, "Thẻ sinh viên", true);

        LibraryCard card = new LibraryCard();
        card.setId(103L);
        card.setCardNumber("LIB-2026-ACTIVE");
        card.setUser(user);
        card.setCardType(type);
        card.setIssuedAt(LocalDate.now());
        card.setExpiresAt(LocalDate.now().plusMonths(12));
        card.setStatus("ACTIVE");

        when(libraryCardRepository.findAllWithDetails()).thenReturn(List.of(card));
        when(readerProfileRepository.findById(22L)).thenReturn(Optional.of(pendingProfile(22L, "Bạn Đọc Bình Thường", "DTC022")));

        var results = service.getIssuedCards();

        assertEquals(1, results.size());
        assertEquals("ACTIVE", results.get(0).status());
    }

    @Test
    @DisplayName("getMyCard trả về EXPIRED nếu thẻ của người dùng đã quá hạn")
    void getMyCard_ExpiredCard_ResolvesExpiredStatus() {
        User user = new User();
        user.setId(23L);
        user.setFullName("Tôi Hết Hạn");
        user.setStatus("ACTIVE");

        ReaderProfile profile = pendingProfile(23L, "Tôi Hết Hạn", "DTC023");
        profile.setRegistrationStatus("APPROVED");

        CardType type = cardType(1L, "Thẻ sinh viên", true);

        LibraryCard card = new LibraryCard();
        card.setId(104L);
        card.setCardNumber("LIB-2026-MY-EXPIRED");
        card.setUser(user);
        card.setCardType(type);
        card.setIssuedAt(LocalDate.now().minusMonths(6));
        card.setExpiresAt(LocalDate.now().minusDays(5));
        card.setStatus("ACTIVE");

        when(readerProfileRepository.findById(23L)).thenReturn(Optional.of(profile));
        when(libraryCardRepository.findByUserIdWithDetails(23L)).thenReturn(Optional.of(card));

        var response = service.getMyCard(23L);

        assertEquals("EXPIRED", response.cardStatus());
    }

    private ReaderProfile pendingProfile(Long id, String fullName, String memberCode) {
        User user = new User();
        user.setId(id);
        user.setFullName(fullName);
        user.setEmail("reader@example.com");

        ReaderProfile profile = new ReaderProfile();
        profile.setUserId(id);
        profile.setUser(user);
        profile.setMemberCode(memberCode);
        profile.setDateOfBirth(LocalDate.of(2005, 1, 1));
        profile.setRegistrationStatus("PENDING");
        return profile;
    }

    private CardType cardType(Long id, String name, boolean active) {
        CardType type = new CardType();
        type.setId(id);
        type.setName(name);
        type.setActive(active);
        return type;
    }
}
