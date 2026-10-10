package com.duanttcsn5.library;

import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.service.ReaderRegistrationRateLimitService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReaderRegistrationRateLimitServiceTest {
    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void firstThreeValidSubmissionsFromOneIpAreRecordedFourthIsRejected() {
        ReaderRegistrationRateLimitService service = new ReaderRegistrationRateLimitService(jdbcTemplate);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq("127.0.0.1")))
                .thenReturn(0L, 1L, 2L, 3L);

        service.checkAndRecord("127.0.0.1");
        service.checkAndRecord("127.0.0.1");
        service.checkAndRecord("127.0.0.1");

        assertThatThrownBy(() -> service.checkAndRecord("127.0.0.1"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException api = (ApiException) e;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(api.getCode()).isEqualTo("REGISTRATION_RATE_LIMITED");
                });
        verify(jdbcTemplate, times(3)).update(
                eq("INSERT INTO reader_registration_attempts (ip_address) VALUES (?)"), eq("127.0.0.1"));
        verify(jdbcTemplate, times(4)).query(
                anyString(), any(org.springframework.jdbc.core.RowCallbackHandler.class), eq("reader-registration:127.0.0.1"));
    }

    @Test
    void newIpHasItsOwnLimit() {
        ReaderRegistrationRateLimitService service = new ReaderRegistrationRateLimitService(jdbcTemplate);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq("192.0.2.1"))).thenReturn(0L);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq("192.0.2.2"))).thenReturn(0L);
        service.checkAndRecord("192.0.2.1");
        service.checkAndRecord("192.0.2.2");
        verify(jdbcTemplate).update(anyString(), eq("192.0.2.1"));
        verify(jdbcTemplate).update(anyString(), eq("192.0.2.2"));
    }
}
