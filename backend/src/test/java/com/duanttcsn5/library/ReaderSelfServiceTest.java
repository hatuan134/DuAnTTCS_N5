package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.profile.ChangeReaderPasswordRequest;
import com.duanttcsn5.library.dto.profile.UpdateReaderContactRequest;
import com.duanttcsn5.library.entity.CardType;
import com.duanttcsn5.library.entity.LibraryCard;
import com.duanttcsn5.library.entity.PasswordHistory;
import com.duanttcsn5.library.entity.ReaderProfile;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.LibraryCardRepository;
import com.duanttcsn5.library.repository.PasswordHistoryRepository;
import com.duanttcsn5.library.repository.ReaderProfileRepository;
import com.duanttcsn5.library.repository.UserRepository;
import com.duanttcsn5.library.service.AuditLogService;
import com.duanttcsn5.library.service.ReaderSelfService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReaderSelfServiceTest {

    @Mock UserRepository userRepository;
    @Mock ReaderProfileRepository readerProfileRepository;
    @Mock LibraryCardRepository libraryCardRepository;
    @Mock PasswordHistoryRepository passwordHistoryRepository;
    @Mock AuditLogService auditLogService;

    private PasswordEncoder passwordEncoder;
    private ReaderSelfService service;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        service = new ReaderSelfService(
                userRepository,
                readerProfileRepository,
                libraryCardRepository,
                passwordHistoryRepository,
                passwordEncoder,
                auditLogService);
    }

    @Test
    @DisplayName("S1-06 T1: Trang cá nhân trả đúng hồ sơ và thông tin thẻ")
    void getProfile_ReturnsReaderAndCardData() {
        User user = user(7L, "reader@ictu.edu.vn", "Current123");
        ReaderProfile profile = profile(user);
        LibraryCard card = card(user);

        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(readerProfileRepository.findById(7L)).thenReturn(Optional.of(profile));
        when(libraryCardRepository.findByUserIdWithDetails(7L)).thenReturn(Optional.of(card));

        var response = service.getProfile(7L);

        assertEquals("Nguyễn Văn Bạn Đọc", response.fullName());
        assertEquals(LocalDate.of(2004, 5, 20), response.dateOfBirth());
        assertEquals("LIB-2026-ABC123", response.cardNumber());
        assertEquals("Thẻ sinh viên", response.cardTypeName());
        assertEquals("ACTIVE", response.cardStatus());
    }

    @Test
    @DisplayName("S1-06 T1: Cập nhật số điện thoại và địa chỉ không bắt nhập mật khẩu khi email không đổi")
    void updateContact_WithoutEmailChange_Succeeds() {
        User user = user(7L, "reader@ictu.edu.vn", "Current123");
        ReaderProfile profile = profile(user);

        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(readerProfileRepository.findById(7L)).thenReturn(Optional.of(profile));
        when(libraryCardRepository.findByUserIdWithDetails(7L)).thenReturn(Optional.empty());

        var response = service.updateContact(
                7L,
                new UpdateReaderContactRequest(
                        "0987654321",
                        "Ký túc xá Đại học Thái Nguyên",
                        "reader@ictu.edu.vn",
                        null),
                "127.0.0.1");

        assertEquals("0987654321", user.getPhone());
        assertEquals("Ký túc xá Đại học Thái Nguyên", user.getAddress());
        assertEquals("reader@ictu.edu.vn", response.email());
        verify(auditLogService).logReaderContactUpdated(
                eq(7L), eq("reader@ictu.edu.vn"), eq("reader@ictu.edu.vn"),
                eq(false), eq(true), eq(true), eq("127.0.0.1"));
    }

    @Test
    @DisplayName("S1-06 T2: Đổi email bị từ chối khi mật khẩu hiện tại sai")
    void updateEmail_WrongPassword_IsRejected() {
        User user = user(7L, "old@ictu.edu.vn", "Current123");
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(readerProfileRepository.findById(7L)).thenReturn(Optional.of(profile(user)));

        ApiException exception = assertThrows(ApiException.class, () -> service.updateContact(
                7L,
                new UpdateReaderContactRequest(
                        "0912345678",
                        "Thái Nguyên",
                        "new@ictu.edu.vn",
                        "Wrong123"),
                "127.0.0.1"));

        assertEquals("INVALID_CURRENT_PASSWORD", exception.getCode());
        assertEquals("old@ictu.edu.vn", user.getEmail());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("S1-06 T2: Không cho đổi sang email đã thuộc tài khoản khác")
    void updateEmail_DuplicateEmail_IsRejected() {
        User user = user(7L, "old@ictu.edu.vn", "Current123");
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(readerProfileRepository.findById(7L)).thenReturn(Optional.of(profile(user)));
        when(userRepository.existsByEmailIgnoreCaseAndIdNot("used@ictu.edu.vn", 7L)).thenReturn(true);

        ApiException exception = assertThrows(ApiException.class, () -> service.updateContact(
                7L,
                new UpdateReaderContactRequest(
                        "0912345678",
                        "Thái Nguyên",
                        "used@ictu.edu.vn",
                        "Current123"),
                "127.0.0.1"));

        assertEquals("EMAIL_ALREADY_EXISTS", exception.getCode());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("S1-06 T3: Đổi mật khẩu bị từ chối khi mật khẩu cũ sai")
    void changePassword_WrongCurrentPassword_IsRejected() {
        User user = user(7L, "reader@ictu.edu.vn", "Current123");
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));

        ApiException exception = assertThrows(ApiException.class, () -> service.changePassword(
                7L,
                new ChangeReaderPasswordRequest("Wrong123", "NewPass456", "NewPass456"),
                "127.0.0.1"));

        assertEquals("INVALID_CURRENT_PASSWORD", exception.getCode());
        verify(passwordHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("S1-06 T3: Không cho dùng lại một trong 3 mật khẩu gần nhất")
    void changePassword_ReusedRecentPassword_IsRejected() {
        User user = user(7L, "reader@ictu.edu.vn", "Current123");
        PasswordHistory previous = history(7L, passwordEncoder.encode("Previous456"));
        PasswordHistory older = history(7L, passwordEncoder.encode("Older789"));

        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(passwordHistoryRepository.findTop5ByUserIdOrderByCreatedAtDesc(7L))
                .thenReturn(List.of(previous, older));

        ApiException exception = assertThrows(ApiException.class, () -> service.changePassword(
                7L,
                new ChangeReaderPasswordRequest("Current123", "Previous456", "Previous456"),
                "127.0.0.1"));

        assertEquals("PASSWORD_RECENTLY_USED", exception.getCode());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("S1-06 T3: Đổi mật khẩu hợp lệ lưu mật khẩu mới và lịch sử")
    void changePassword_ValidPassword_Succeeds() {
        User user = user(7L, "reader@ictu.edu.vn", "Current123");
        String oldHash = user.getPasswordHash();

        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(passwordHistoryRepository.findTop5ByUserIdOrderByCreatedAtDesc(7L))
                .thenReturn(List.of());
        when(passwordHistoryRepository.existsByUserIdAndPasswordHash(7L, oldHash))
                .thenReturn(false);

        var response = service.changePassword(
                7L,
                new ChangeReaderPasswordRequest("Current123", "BrandNew456", "BrandNew456"),
                "127.0.0.1");

        assertEquals("Đổi mật khẩu thành công.", response.message());
        assertTrue(passwordEncoder.matches("BrandNew456", user.getPasswordHash()));
        assertFalse(passwordEncoder.matches("Current123", user.getPasswordHash()));
        verify(passwordHistoryRepository, times(2)).save(any(PasswordHistory.class));
        verify(auditLogService).logReaderPasswordChanged(
                7L,
                "reader@ictu.edu.vn",
                "127.0.0.1");
    }

    private User user(Long id, String email, String rawPassword) {
        User user = new User();
        user.setId(id);
        user.setFullName("Nguyễn Văn Bạn Đọc");
        user.setEmail(email);
        user.setPhone("0912345678");
        user.setAddress("Thái Nguyên");
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setStatus("ACTIVE");
        return user;
    }

    private ReaderProfile profile(User user) {
        ReaderProfile profile = new ReaderProfile();
        profile.setUserId(user.getId());
        profile.setUser(user);
        profile.setDateOfBirth(LocalDate.of(2004, 5, 20));
        profile.setMemberCode("SV2026001");
        profile.setRegistrationStatus("APPROVED");
        return profile;
    }

    private LibraryCard card(User user) {
        CardType cardType = new CardType();
        cardType.setId(2L);
        cardType.setName("Thẻ sinh viên");

        LibraryCard card = new LibraryCard();
        card.setId(11L);
        card.setUser(user);
        card.setCardType(cardType);
        card.setCardNumber("LIB-2026-ABC123");
        card.setIssuedAt(LocalDate.of(2026, 9, 1));
        card.setExpiresAt(LocalDate.of(2027, 9, 1));
        card.setStatus("ACTIVE");
        return card;
    }

    private PasswordHistory history(Long userId, String hash) {
        PasswordHistory history = new PasswordHistory();
        history.setUserId(userId);
        history.setPasswordHash(hash);
        return history;
    }
}
