package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.reader.DuplicateCheckResponse;
import com.duanttcsn5.library.dto.reader.ReaderRegistrationRequest;
import com.duanttcsn5.library.dto.reader.ReaderRegistrationResponse;
import com.duanttcsn5.library.entity.ReaderProfile;
import com.duanttcsn5.library.entity.Role;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.AuditLogRepository;
import com.duanttcsn5.library.repository.ReaderProfileRepository;
import com.duanttcsn5.library.repository.RoleRepository;
import com.duanttcsn5.library.repository.UserRepository;
import com.duanttcsn5.library.service.ReaderRegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReaderRegistrationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ReaderProfileRepository readerProfileRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditLogRepository auditLogRepository;

    private ReaderRegistrationService service;

    @BeforeEach
    void setUp() {
        service = new ReaderRegistrationService(
                userRepository,
                readerProfileRepository,
                roleRepository,
                passwordEncoder,
                auditLogRepository
        );
    }

    @Test
    @DisplayName("S1-03: Đăng ký với Email đã có trong hệ thống - Từ chối lưu hồ sơ và hiện gợi ý quên mật khẩu")
    void testRegisterReader_DuplicateEmail_ThrowsConflictWithForgotPasswordHint() {
        // Arrange
        String existingEmail = "sv01@ictu.edu.vn";
        ReaderRegistrationRequest request = new ReaderRegistrationRequest(
                "Nguyen Van A",
                existingEmail,
                "B21DCCN001",
                LocalDate.of(2003, 5, 15),
                "0987654321",
                "Ha Noi",
                "Password123"
        );

        when(userRepository.existsByEmailIgnoreCase(existingEmail.toLowerCase()))
                .thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> service.registerReader(request, "127.0.0.1"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiException = (ApiException) ex;
                    assertThat(apiException.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(apiException.getCode()).isEqualTo("DUPLICATE_EMAIL");
                    assertThat(apiException.getMessage())
                            .contains(existingEmail)
                            .contains("Quên mật khẩu")
                            .contains("/forgot-password");
                });

        // Đảm bảo TUYỆT ĐỐI KHÔNG lưu hồ sơ hay người dùng vào DB
        verify(userRepository, never()).save(any(User.class));
        verify(readerProfileRepository, never()).save(any(ReaderProfile.class));
    }

    @Test
    @DisplayName("S1-03: Đăng ký với Mã sinh viên/cán bộ đã có trong hệ thống - Từ chối lưu hồ sơ và hiện gợi ý quên mật khẩu")
    void testRegisterReader_DuplicateMemberCode_ThrowsConflictWithForgotPasswordHint() {
        // Arrange
        String existingMemberCode = "B21DCCN999";
        ReaderRegistrationRequest request = new ReaderRegistrationRequest(
                "Tran Thi B",
                "new.email@ictu.edu.vn",
                existingMemberCode,
                LocalDate.of(2002, 8, 20),
                "0912345678",
                "Thai Nguyen",
                "Password123"
        );

        when(userRepository.existsByEmailIgnoreCase("new.email@ictu.edu.vn"))
                .thenReturn(false);
        when(readerProfileRepository.existsByMemberCodeIgnoreCase(existingMemberCode.toUpperCase()))
                .thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> service.registerReader(request, "127.0.0.1"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiException = (ApiException) ex;
                    assertThat(apiException.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(apiException.getCode()).isEqualTo("DUPLICATE_MEMBER_CODE");
                    assertThat(apiException.getMessage())
                            .contains(existingMemberCode)
                            .contains("Quên mật khẩu");
                });

        // Đảm bảo TUYỆT ĐỐI KHÔNG lưu hồ sơ hay người dùng vào DB
        verify(userRepository, never()).save(any(User.class));
        verify(readerProfileRepository, never()).save(any(ReaderProfile.class));
    }

    @Test
    @DisplayName("S1-03: Đăng ký với Email và Mã sinh viên mới - Thành công lưu User và ReaderProfile")
    void testRegisterReader_Success() {
        // Arrange
        ReaderRegistrationRequest request = new ReaderRegistrationRequest(
                "Le Van C",
                "levanc@ictu.edu.vn",
                "B21DCCN123",
                LocalDate.of(2003, 1, 10),
                "0933333333",
                "Ha Noi",
                "SecurePass123"
        );

        when(userRepository.existsByEmailIgnoreCase("levanc@ictu.edu.vn")).thenReturn(false);
        when(readerProfileRepository.existsByMemberCodeIgnoreCase("B21DCCN123")).thenReturn(false);

        Role readerRole = new Role();
        readerRole.setId(1L);
        readerRole.setCode("READER");
        readerRole.setName("Bạn đọc");
        when(roleRepository.findByCode("READER")).thenReturn(Optional.of(readerRole));

        when(passwordEncoder.encode("SecurePass123")).thenReturn("$2a$10$hashedPassword");

        User mockSavedUser = new User();
        mockSavedUser.setId(10L);
        mockSavedUser.setFullName("Le Van C");
        mockSavedUser.setEmail("levanc@ictu.edu.vn");
        mockSavedUser.setRole(readerRole);
        mockSavedUser.setStatus("ACTIVE");

        when(userRepository.save(any(User.class))).thenReturn(mockSavedUser);

        ReaderProfile mockSavedProfile = new ReaderProfile();
        mockSavedProfile.setUser(mockSavedUser);
        mockSavedProfile.setMemberCode("B21DCCN123");
        mockSavedProfile.setRegistrationStatus("PENDING");
        mockSavedProfile.setDateOfBirth(LocalDate.of(2003, 1, 10));

        when(readerProfileRepository.save(any(ReaderProfile.class))).thenReturn(mockSavedProfile);

        // Act
        ReaderRegistrationResponse response = service.registerReader(request, "127.0.0.1");

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(10L);
        assertThat(response.email()).isEqualTo("levanc@ictu.edu.vn");
        assertThat(response.memberCode()).isEqualTo("B21DCCN123");
        assertThat(response.registrationStatus()).isEqualTo("PENDING");
        assertThat(response.message()).contains("thành công");

        // Verify entities were saved
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("levanc@ictu.edu.vn");
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("$2a$10$hashedPassword");

        ArgumentCaptor<ReaderProfile> profileCaptor = ArgumentCaptor.forClass(ReaderProfile.class);
        verify(readerProfileRepository).save(profileCaptor.capture());
        assertThat(profileCaptor.getValue().getMemberCode()).isEqualTo("B21DCCN123");
        assertThat(profileCaptor.getValue().getRegistrationStatus()).isEqualTo("PENDING");

        verify(auditLogRepository).insert(
                eq(10L),
                eq("READER_REGISTERED"),
                eq("READER_PROFILE"),
                eq("10"),
                anyString(),
                eq("127.0.0.1")
        );
    }

    @Test
    @DisplayName("S1-03: Kiểm tra API checkDuplicate khi trùng Email")
    void testCheckDuplicate_EmailExists() {
        when(userRepository.existsByEmailIgnoreCase("test@example.com")).thenReturn(true);

        DuplicateCheckResponse result = service.checkDuplicate("test@example.com", null);

        assertThat(result.emailExists()).isTrue();
        assertThat(result.memberCodeExists()).isFalse();
        assertThat(result.suggestForgotPassword()).isTrue();
        assertThat(result.forgotPasswordUrl()).contains("/forgot-password?email=test%40example.com");
    }

    @Test
    @DisplayName("S1-03: Kiểm tra API checkDuplicate khi trùng MemberCode")
    void testCheckDuplicate_MemberCodeExists() {
        when(readerProfileRepository.existsByMemberCodeIgnoreCase("CB001")).thenReturn(true);

        DuplicateCheckResponse result = service.checkDuplicate(null, "CB001");

        assertThat(result.emailExists()).isFalse();
        assertThat(result.memberCodeExists()).isTrue();
        assertThat(result.suggestForgotPassword()).isTrue();
        assertThat(result.forgotPasswordUrl()).isEqualTo("/forgot-password");
    }
}
