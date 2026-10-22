package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.permission.PermissionReviewDtos.*;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.PermissionReviewRepository;
import com.duanttcsn5.library.service.*;
import com.duanttcsn5.library.service.PermissionReviewGitHubCallbackService.*;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PermissionReviewAutomaticCallbackTest {
    final String secret = "only-for-unit-tests-never-production-secret";
    final PermissionReviewRepository repository = mock(PermissionReviewRepository.class);
    final PermissionReviewService reviews = mock(PermissionReviewService.class);
    final Validator validator = mock(Validator.class);
    final ObjectMapper mapper = new ObjectMapper();
    final PermissionReviewGitHubCallbackService service = new PermissionReviewGitHubCallbackService(
            new GitHubPermissionReviewGateway("owner","repo","",secret),repository,reviews,mapper,validator);
    final UUID key = UUID.randomUUID();
    final OffsetDateTime start = OffsetDateTime.now().minusMinutes(2), end = start.plusMinutes(1);

    Callback callback(int skipped, String repo) {
        var check = new EvidenceCase("endpoint", "READER", "STANDARD",403,null,403,true,
                start,end,"verified unchanged",null,"POST",true);
        var report = new ImportReport(2,key,"version","a".repeat(64),"REGRESSION","ISOLATED_POSTGRES",
                "source","CI",start,end,List.of(check));
        return new Callback(key,report.fingerprint(),true,report,new CiRun(repo,123,1,"b".repeat(40),"main","push",start,end,1,0,skipped));
    }
    void accept(Callback value) throws Exception {
        String body = mapper.writeValueAsString(value), timestamp = String.valueOf(Instant.now().getEpochSecond());
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        service.accept(timestamp,"sha256=" + HexFormat.of().formatHex(mac.doFinal((timestamp+"."+body).getBytes(StandardCharsets.UTF_8))),body);
    }
    void reportSaved() {
        when(validator.validate(any(ImportReport.class))).thenReturn(Set.of());
        var summary = mock(ReportSummary.class); when(summary.id()).thenReturn(5L);
        var detail = mock(ReportDetail.class); when(detail.summary()).thenReturn(summary);
        when(reviews.importAutomatedReport(any(),isNull())).thenReturn(detail);
    }
    @Test void automatedRunDoesNotNeedAnAdminAccountOrPendingJob() throws Exception {
        reportSaved(); accept(callback(0,"owner/repo"));
        verify(repository,never()).lockPendingActor(any(),any());
        verify(repository).finishJob(eq(key),eq("SUCCEEDED"),anyString(),eq(5L));
        verify(repository).saveCiReceipt(eq(key),any(),anyString());
    }
    @Test void skippedTestsNeverProduceSuccessEvenIfCallerClaimsPassed() throws Exception {
        reportSaved(); accept(callback(1,"owner/repo"));
        verify(repository).finishJob(eq(key),eq("FAILED"),anyString(),eq(5L));
    }
    @Test void identicalRetryIsNoOpAndModifiedRetryIsRejected() throws Exception {
        var input = callback(0,"owner/repo");
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsString(input).getBytes(StandardCharsets.UTF_8)));
        when(repository.lockCiReceipt(key)).thenReturn(Optional.of(hash));
        accept(input); verifyNoInteractions(reviews);
        assertEquals(409,assertThrows(ApiException.class,()->accept(callback(1,"owner/repo"))).getStatus().value());
        verify(repository,never()).finishJob(any(),any(),any(),any());
    }
    @Test void differentRepositoryIsRejectedBeforeDatabaseAccess() {
        assertEquals(400,assertThrows(ApiException.class,()->accept(callback(0,"other/repo"))).getStatus().value());
        verifyNoInteractions(repository,reviews);
    }
}
