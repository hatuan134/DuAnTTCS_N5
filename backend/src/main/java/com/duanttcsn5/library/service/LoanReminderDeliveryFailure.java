package com.duanttcsn5.library.service;

import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import org.springframework.mail.*;
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.*;

/** Conservative transport classification. A read/write timeout can follow SMTP acceptance. */
public record LoanReminderDeliveryFailure(String state, String code) {
    public static LoanReminderDeliveryFailure classify(Throwable failure) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<Throwable> chain = new ArrayList<>();
        collect(failure, seen, chain);
        // Partial delivery must never be retried, even if another address was rejected.
        if (chain.stream().anyMatch(e -> e instanceof SendFailedException s
                && s.getValidSentAddresses() != null && s.getValidSentAddresses().length > 0))
            return new LoanReminderDeliveryFailure("REVIEW_REQUIRED", "SMTP_UNKNOWN");
        if (chain.stream().anyMatch(e -> e instanceof MailAuthenticationException
                || e instanceof jakarta.mail.AuthenticationFailedException))
            return new LoanReminderDeliveryFailure("FAILED", "SMTP_AUTH");
        if (chain.stream().anyMatch(e -> e instanceof MailPreparationException))
            return new LoanReminderDeliveryFailure("FAILED", "SMTP_CONFIG");
        for (Throwable e : chain) {
            int code = e instanceof SMTPAddressFailedException a ? a.getReturnCode()
                    : e instanceof SMTPSendFailedException s ? s.getReturnCode() : 0;
            if (code >= 500 && code <= 599) return new LoanReminderDeliveryFailure("FAILED", "SMTP_REJECTED");
            if (code >= 400 && code <= 499) return new LoanReminderDeliveryFailure("PENDING_RETRY", "SMTP_TEMPORARY_REJECTION");
        }
        if (chain.stream().anyMatch(e -> e instanceof ConnectException || e instanceof UnknownHostException))
            return new LoanReminderDeliveryFailure("PENDING_RETRY", "SMTP_CONNECT");
        if (chain.stream().anyMatch(e -> e instanceof SendFailedException s
                && s.getInvalidAddresses() != null && s.getInvalidAddresses().length > 0))
            return new LoanReminderDeliveryFailure("FAILED", "SMTP_REJECTED");
        return new LoanReminderDeliveryFailure("REVIEW_REQUIRED", "SMTP_UNKNOWN");
    }
    private static void collect(Throwable e, Set<Throwable> seen, List<Throwable> result) {
        if (e == null || seen.size() >= 32 || !seen.add(e)) return;
        result.add(e);
        collect(e.getCause(), seen, result);
        if (e instanceof MessagingException m) collect(m.getNextException(), seen, result);
        if (e instanceof MailSendException m) m.getFailedMessages().values().forEach(x -> collect(x, seen, result));
    }
}
