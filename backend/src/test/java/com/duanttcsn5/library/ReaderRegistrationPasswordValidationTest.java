package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.reader.ReaderRegistrationRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ReaderRegistrationPasswordValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private ReaderRegistrationRequest payload(String password) {
        return new ReaderRegistrationRequest("Nguyen Van A", "new@example.com", null,
                LocalDate.of(2002, 1, 1), null, null, password);
    }

    @Test
    void rejectsTooShortAndWithoutLettersOrDigits() {
        assertThat(validator.validate(payload("abc1234"))).isNotEmpty();
        assertThat(validator.validate(payload("12345678"))).isNotEmpty();
        assertThat(validator.validate(payload("abcdefgh"))).isNotEmpty();
        assertThat(validator.validate(payload("Abc12345"))).isEmpty();
    }

    @Test
    void acceptsUnicodeLettersAndLongPassword() {
        assertThat(validator.validate(payload("Mậtkhẩu123"))).isEmpty();
        assertThat(validator.validate(payload("A1" + "x".repeat(150)))).isEmpty();
    }
}
