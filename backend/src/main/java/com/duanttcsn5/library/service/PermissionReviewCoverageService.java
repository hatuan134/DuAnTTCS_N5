package com.duanttcsn5.library.service;

import com.duanttcsn5.library.dto.permission.PermissionReviewDtos.*;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.PermissionReviewRepository;
import com.duanttcsn5.library.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@PreAuthorize("hasRole('ADMIN')")
public class PermissionReviewCoverageService {
    private final PermissionReviewService reviews;
    private final PermissionReviewRepository repository;
    private final UserRepository users;
    public PermissionReviewCoverageService(PermissionReviewService reviews, PermissionReviewRepository repository, UserRepository users) {
        this.reviews=reviews; this.repository=repository; this.users=users;
    }
    @Transactional(readOnly=true)
    public Coverage coverage(Long id) {
        ReportDetail detail=reviews.detail(id); Inspection inspection=reviews.inspect();
        Map<String,CaseResult> results=new HashMap<>();
        for(var result:detail.results()) {
            var test=result.test();
            results.put(key(test.endpointId(),test.httpMethod(),test.role(),test.scenario()),result);
        }
        List<MissingRoleCase> missing=new ArrayList<>(); int covered=0,coveredApis=0;
        for(var row:inspection.entries()) for(String method:row.policy().httpMethods()) {
            boolean apiCovered=true;
            for(var role:inspection.roles()) {
                String scenario="STANDARD";
                if(!results.containsKey(key(row.policy().id(),method,role.code(),scenario))) {
                    missing.add(new MissingRoleCase(row.policy().id(),method,row.policy().path(),role.code(),scenario));apiCovered=false;
                } else covered++;
                if("OVERRIDE".equals(row.policy().rules().get(role.code())) && !results.containsKey(key(row.policy().id(),method,role.code(),"OVERRIDE")))
                    missing.add(new MissingRoleCase(row.policy().id(),method,row.policy().path(),role.code(),"OVERRIDE"));
                if("OWN".equals(row.policy().rules().get(role.code()))) {
                    String scope=Set.of("LoanController.loanDetail","LoanController.checkMyRenewal","BookReservationController.cancelMine","DepositController.accounts","DepositController.history").contains(row.policy().id())?"OTHER_OWNER":"SPOOFED_OWNER";
                    if(!results.containsKey(key(row.policy().id(),method,role.code(),scope))) missing.add(new MissingRoleCase(row.policy().id(),method,row.policy().path(),role.code(),scope));
                }
            }
            if(apiCovered)coveredApis++;
            if(!results.containsKey(key(row.policy().id(),method,"ANONYMOUS","STANDARD")))missing.add(new MissingRoleCase(row.policy().id(),method,row.policy().path(),"ANONYMOUS","STANDARD"));
        }
        int failed=(int)detail.results().stream().filter(result->!result.passed()).count();
        boolean current=detail.currentFingerprint() && detail.evidence().policyVersion().equals(inspection.policyVersion());
        boolean executed=repository.serverExecuted(id,detail.evidence().runKey(),inspection.fingerprint())
                && detail.evidence().schemaVersion()==2 && "REGRESSION".equals(detail.evidence().kind())
                && "ISOLATED_POSTGRES".equals(detail.evidence().environment());
        List<String> reasons=new ArrayList<>();
        if(!current)reasons.add("Bảng quyền hoặc bản code đã thay đổi; cần chạy lại toàn bộ.");
        if(!executed)reasons.add("Biên bản chưa được xác thực là kết quả bộ chạy toàn hệ thống trên database riêng.");
        if(!missing.isEmpty())reasons.add("Còn "+missing.size()+" trường hợp thiếu kiểm thử vai trò, chưa đăng nhập hoặc phạm vi dữ liệu.");
        if(failed>0)reasons.add("Còn "+failed+" trường hợp kiểm thử chưa đạt.");
        if(inspection.differentFunctions()>0)reasons.add("Ma trận còn "+inspection.differentFunctions()+" chức năng sai lệch hoặc chưa xác định.");
        if(inspection.totalApis()==0)reasons.add("Chưa phát hiện API nghiệp vụ.");
        return new Coverage(inspection.totalApis(),coveredApis,inspection.totalApis()*4,covered,
                detail.results().size()-failed,failed,failed,inspection.differentFunctions(),inspection.unresolvedFunctions(),
                current,executed,reasons.isEmpty(),List.copyOf(missing),List.copyOf(reasons),repository.completion(id).orElse(null));
    }
    @Transactional
    public Coverage complete(Long id, Long actorId) {
        repository.lockReport(id);
        var actor=actorId==null?null:users.findById(actorId).orElse(null);
        if(actor==null || !"ACTIVE".equals(actor.getStatus()) || actor.getRole()==null || !"ADMIN".equals(actor.getRole().getCode()))
            throw new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","Chỉ Quản trị hệ thống được xác nhận rà soát.");
        Coverage coverage=coverage(id);
        if(!coverage.eligible())throw new ApiException(HttpStatus.CONFLICT,"PERMISSION_REVIEW_INCOMPLETE",String.join(" ",coverage.reasons()));
        if(coverage.completion()==null)repository.complete(id,actorId,actor.getFullName(),reviews.inspect().fingerprint(),coverage.coveredRoleCases());
        return coverage(id);
    }
    @Transactional(readOnly=true)
    public ExportReport export(Long id) {
        var detail=reviews.detail(id);
        return new ExportReport(detail,coverage(id),repository.sprintReports(detail.evidence().policyVersion()).stream().map(summary -> reviews.detail(summary.id())).toList());
    }
    private String key(String id,String method,String role,String scenario){return id+":"+method+":"+role+":"+scenario;}
}
