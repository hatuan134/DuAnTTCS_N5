package com.duanttcsn5.library;
import com.duanttcsn5.library.service.LoanReminderFailureMessages;
import jakarta.mail.MessagingException;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
class LoanReminderFailureMessagesTest {
    @Test void unknownRecipientIsVietnameseEvenInsideMailSendFailureMap() {
        var nested=new MessagingException("550 5.1.1 The email account that you tried to reach does not exist");
        String message=LoanReminderFailureMessages.from(new MailSendException(Map.of(new Object(),nested)));
        assertThat(message).contains("email người nhận không tồn tại").doesNotContain("550","The email account");
    }
    @Test void genericMailboxRejectionDoesNotClaimAddressDefinitelyDoesNotExist() {
        assertThat(LoanReminderFailureMessages.message("550 mailbox unavailable")).contains("từ chối").doesNotContain("không tồn tại");
    }
    @Test void connectionAuthenticationFullMailboxAndUnknownFailuresHaveGuidance() {
        assertThat(LoanReminderFailureMessages.message("Connection timed out smtp.host")).contains("kiểm tra mạng");
        assertThat(LoanReminderFailureMessages.message("535 AuthenticationFailedException")).contains("xác thực");
        assertThat(LoanReminderFailureMessages.message("552 5.2.2 mailbox full")).contains("đã đầy");
        assertThat(LoanReminderFailureMessages.message("unexpected internal diagnostic")).contains("Chưa gửi được");
    }
    @Test void nullAndSimulatedFailuresRetainTheirMeaning() {
        assertThat(LoanReminderFailureMessages.message(null)).isNull();
        assertThat(LoanReminderFailureMessages.message("Mô phỏng lỗi thử lại lần 1")).isEqualTo("Mô phỏng lỗi thử lại lần 1");
    }
}
