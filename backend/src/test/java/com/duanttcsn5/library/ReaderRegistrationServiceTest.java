package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.reader.DuplicateCheckResponse;
import com.duanttcsn5.library.dto.reader.ReaderRegistrationRequest;
import com.duanttcsn5.library.dto.reader.ReaderRegistrationResponse;
import com.duanttcsn5.library.entity.ReaderProfile;
import com.duanttcsn5.library.entity.Role;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.AuditLogRepository;
import com.duanttcsn5.library.repository.LibraryCardRepository;
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

    @Mock
    private LibraryCardRepository libraryCardRepository;

    private ReaderRegistrationService service;

    @BeforeEach
    void setUp() {
        service = new ReaderRegistrationService(
                userRepository,
                readerProfileRepository,
                roleRepository,
                passwordEncoder,
                auditLogRepository,
                libraryCardRepository
        );
    }

    @Test
    @DisplayName("S3-00.3 - Đăng ký với Email đã có vẫn bị từ chối")
    void testRegisterReader_DuplicateEmail_ThrowsConflictWithForgotPasswordHint() {
        String existingEmail = "sv01@ictu.edu.vn";
        ReaderRegistrationRequest request = request(existingEmail, "MÃ-CŨ-KHÔNG-CÒN-DÙNG");

        when(userRepository.existsByEmailIgnoreCase(existingEmail)).thenReturn(true);

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

        verify(userRepository, never()).save(any(User.class));
        verify(readerProfileRepository, never()).save(any(ReaderProfile.class));
        verify(readerProfileRepository, never()).nextMemberCodeNumber();
    }

    @Test
    @DisplayName("S3-00.3 - Mã do client gửi lên bị bỏ qua, hệ thống tự sinh mã bạn đọc")
    void testRegisterReader_LegacyMemberCodeIsIgnoredAndGeneratedBySystem() {
        ReaderRegistrationRequest request = request("new.email@ictu.edu.vn", "B21DCCN999");
        stubSuccessfulRegistration("new.email@ictu.edu.vn", 25L);

        ReaderRegistrationResponse response = service.registerReader(request, "127.0.0.1");

        assertThat(response.memberCode()).isEqualTo("BD000025");
        ArgumentCaptor<ReaderProfile> profileCaptor = ArgumentCaptor.forClass(ReaderProfile.class);
        verify(readerProfileRepository).save(profileCaptor.capture());
        assertThat(profileCaptor.getValue().getMemberCode()).isEqualTo("BD000025");
        assertThat(profileCaptor.getValue().getMemberCode()).isNotEqualTo("B21DCCN999");
    }

    @Test
    @DisplayName("S3-00.3 - Đăng ký thành công tự cấp mã BD theo sequence")
    void testRegisterReader_Success() {
        ReaderRegistrationRequest request = new ReaderRegistrationRequest(
                "Le Van C",
                "levanc@ictu.edu.vn",
                null,
                LocalDate.of(2003, 1, 10),
                "0933333333",
                "Ha Noi",
                "SecurePass123"
        );

        when(userRepository.existsByEmailIgnoreCase("levanc@ictu.edu.vn")).thenReturn(false);

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

        when(readerProfileRepository.nextMemberCodeNumber()).thenReturn(1L);
        when(readerProfileRepository.existsByMemberCodeIgnoreCase("BD000001")).thenReturn(false);
        when(readerProfileRepository.save(any(ReaderProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReaderRegistrationResponse response = service.registerReader(request, "127.0.0.1");

        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(10L);
        assertThat(response.email()).isEqualTo("levanc@ictu.edu.vn");
        assertThat(response.memberCode()).isEqualTo("BD000001");
        assertThat(response.registrationStatus()).isEqualTo("PENDING");
        assertThat(response.message()).contains("thành công");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("levanc@ictu.edu.vn");
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("$2a$10$hashedPassword");

        ArgumentCaptor<ReaderProfile> profileCaptor = ArgumentCaptor.forClass(ReaderProfile.class);
        verify(readerProfileRepository).save(profileCaptor.capture());
        assertThat(profileCaptor.getValue().getMemberCode()).isEqualTo("BD000001");
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
    @DisplayName("S1-03 - API checkDuplicate vẫn tương thích với Email")
    void testCheckDuplicate_EmailExists() {
        when(userRepository.existsByEmailIgnoreCase("test@example.com")).thenReturn(true);

        DuplicateCheckResponse result = service.checkDuplicate("test@example.com", null);

        assertThat(result.emailExists()).isTrue();
        assertThat(result.memberCodeExists()).isFalse();
        assertThat(result.suggestForgotPassword()).isTrue();
        assertThat(result.forgotPasswordUrl()).contains("/forgot-password?email=test%40example.com");
    }

    @Test
    @DisplayName("S1-03 - API checkDuplicate cũ vẫn tương thích với MemberCode")
    void testCheckDuplicate_MemberCodeExists() {
        when(readerProfileRepository.existsByMemberCodeIgnoreCase("CB001")).thenReturn(true);

        DuplicateCheckResponse result = service.checkDuplicate(null, "CB001");

        assertThat(result.emailExists()).isFalse();
        assertThat(result.memberCodeExists()).isTrue();
        assertThat(result.suggestForgotPassword()).isTrue();
        assertThat(result.forgotPasswordUrl()).isEqualTo("/forgot-password");
    }

    @Test
    @DisplayName("S1-03 - Từ chối ngày sinh vượt quá ngày hiện tại")
    void testRegisterReader_FutureDateOfBirth_ThrowsBadRequest() {
        ReaderRegistrationRequest request = new ReaderRegistrationRequest(
                "Nguyen Van D",
                "future@ictu.edu.vn",
                null,
                LocalDate.now().plusMonths(1),
                "0987654321",
                "Thai Nguyen",
                "Password123"
        );

        assertThatThrownBy(() -> service.registerReader(request, "127.0.0.1"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiException = (ApiException) ex;
                    assertThat(apiException.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(apiException.getCode()).isEqualTo("INVALID_DATE_OF_BIRTH");
                });

        verify(userRepository, never()).save(any(User.class));
        verify(readerProfileRepository, never()).save(any(ReaderProfile.class));
    }

    private ReaderRegistrationRequest request(String email, String legacyMemberCode) {
        return new ReaderRegistrationRequest(
                "Tran Thi B",
                email,
                legacyMemberCode,
                LocalDate.of(2002, 8, 20),
                "0912345678",
                "Thai Nguyen",
                "Password123"
        );
    }

    private void stubSuccessfulRegistration(String email, long memberNumber) {
        when(userRepository.existsByEmailIgnoreCase(email)).thenReturn(false);

        Role readerRole = new Role();
        readerRole.setId(1L);
        readerRole.setCode("READER");
        readerRole.setName("Bạn đọc");
        when(roleRepository.findByCode("READER")).thenReturn(Optional.of(readerRole));
        when(passwordEncoder.encode("Password123")).thenReturn("hash");

        User savedUser = new User();
        savedUser.setId(11L);
        savedUser.setFullName("Tran Thi B");
        savedUser.setEmail(email);
        savedUser.setRole(readerRole);
        savedUser.setStatus("ACTIVE");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        String generatedCode = String.format("BD%06d", memberNumber);
        when(readerProfileRepository.nextMemberCodeNumber()).thenReturn(memberNumber);
        when(readerProfileRepository.existsByMemberCodeIgnoreCase(generatedCode)).thenReturn(false);
        when(readerProfileRepository.save(any(ReaderProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }
}
