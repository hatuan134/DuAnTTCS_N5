package com.duanttcsn5.library;

import com.duanttcsn5.library.service.*;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.*;
import org.springframework.mail.javamail.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoanReminderTransportTest {
    @Test void missingSenderFromHostOrCredentialsNeverSucceeds() {
        @SuppressWarnings("unchecked") ObjectProvider<JavaMailSender> provider=mock(ObjectProvider.class);
        var service=new EmailService(provider,Runnable::run,"http://localhost","from@example.test",1,0);
        assertThrows(MailPreparationException.class,()->service.sendRawHtmlEmail("to@example.test","Test","HTML"));
        var sender=new JavaMailSenderImpl();
        when(provider.getIfAvailable()).thenReturn(sender);
        assertThrows(MailPreparationException.class,()->service.sendRawHtmlEmail("to@example.test","Test","HTML"));
        sender.setUsername("fake");sender.setPassword("fake");
        assertThrows(MailPreparationException.class,()->service.sendRawHtmlEmail("to@example.test","Test","HTML"));
        var blankFrom=new EmailService(provider,Runnable::run,"http://localhost","",1,0);
        assertThrows(MailPreparationException.class,()->blankFrom.sendRawHtmlEmail("to@example.test","Test","HTML"));
    }
    @Test void fakeSenderSupportsAutomatedTestsWithoutNetwork() throws Exception {
        @SuppressWarnings("unchecked") ObjectProvider<JavaMailSender> provider=mock(ObjectProvider.class);
        var sender=mock(JavaMailSender.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        var message=new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(message);
        new EmailService(provider,Runnable::run,"http://localhost","from@example.test",1,0)
                .sendRawHtmlEmail("to@example.test","Tiếng Việt","<p>Nội dung</p>");
        verify(sender).send(message);
    }
    @Test void authenticationAndRejectionAreNotRetriedButPartialDeliveryNeedsReview() throws Exception {
        assertEquals("SMTP_AUTH",LoanReminderDeliveryFailure.classify(new MailAuthenticationException("secret")).code());
        Address[] addresses={new InternetAddress("to@example.test")};
        var rejected=new SendFailedException("private diagnostic",null,null,null,addresses);
        assertEquals("SMTP_REJECTED",LoanReminderDeliveryFailure.classify(new MailSendException(Map.of(new Object(),rejected))).code());
        var partial=new SendFailedException("private diagnostic",null,addresses,null,addresses);
        assertEquals("REVIEW_REQUIRED",LoanReminderDeliveryFailure.classify(partial).state());
        assertEquals("SMTP_UNKNOWN",LoanReminderDeliveryFailure.classify(new RuntimeException("unknown")).code());
        assertFalse(LoanReminderFailureMessages.message("password=secret token=123").contains("secret"));
    }
    @Test void typedSmtpRepliesDistinguishTemporaryAndPermanentRejection() throws Exception {
        var address=new InternetAddress("reader@example.test");
        assertEquals("FAILED",LoanReminderDeliveryFailure.classify(
                new org.eclipse.angus.mail.smtp.SMTPAddressFailedException(address,"RCPT TO",550,"rejected")).state());
        assertEquals("PENDING_RETRY",LoanReminderDeliveryFailure.classify(
                new org.eclipse.angus.mail.smtp.SMTPAddressFailedException(address,"RCPT TO",450,"temporary")).state());
    }
}
