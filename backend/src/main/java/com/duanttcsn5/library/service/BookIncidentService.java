package com.duanttcsn5.library.service;

import com.duanttcsn5.library.dto.incident.IncidentDtos.*;
import com.duanttcsn5.library.dto.loan.ReturnFeeAssessment;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.web.multipart.MultipartFile;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class BookIncidentService {
    private final BookIncidentRepository incidents;
    private final LoanRepository loans;
    private final UserRepository users;
    private final LibraryConfigurationService configuration;
    public BookIncidentService(BookIncidentRepository incidents, LoanRepository loans, UserRepository users,
                               LibraryConfigurationService configuration) {
        this.incidents=incidents; this.loans=loans; this.users=users; this.configuration=configuration;
    }
    private User actor(Long id, boolean manager) {
        User user=id==null?null:users.findById(id).orElse(null);
        if(user==null || !"ACTIVE".equals(user.getStatus()) || user.getRole()==null
                || !(manager?Set.of("LIBRARY_MANAGER","ADMIN"):Set.of("LIBRARIAN","ADMIN")).contains(user.getRole().getCode()))
            throw error(HttpStatus.FORBIDDEN,"FORBIDDEN",manager?"Chỉ Quản lý hoặc Quản trị được thay đổi hệ số.":"Chỉ Thủ thư được ghi nhận sự cố.");
        return user;
    }
    @Transactional(readOnly=true)
    public Factors factors() { return incidents.factors(false); }
    @Transactional(rollbackFor=Exception.class)
    public Factors updateFactors(Factors factors,Long actorId) {
        actor(actorId,true);
        if(factors==null) throw error(HttpStatus.BAD_REQUEST,"INVALID_FACTORS","Vui lòng nhập hai hệ số bồi thường.");
        IncidentCalculator.validateFactor(factors.lost()); IncidentCalculator.validateFactor(factors.damaged());
        incidents.updateFactors(factors,actorId); return incidents.factors(false);
    }
    private User adjustmentActor(Long id,boolean confirm) {
        User user=id==null?null:users.findById(id).orElse(null);
        Set<String> roles=confirm?Set.of("LIBRARY_MANAGER","ADMIN"):Set.of("LIBRARIAN","LIBRARY_MANAGER","ADMIN");
        if(user==null || !"ACTIVE".equals(user.getStatus()) || user.getRole()==null || !roles.contains(user.getRole().getCode()))
            throw error(HttpStatus.FORBIDDEN,"FORBIDDEN",confirm?"Chỉ Quản lý hoặc Admin được xác nhận bút toán.":"Bạn không có quyền xem hoặc lập bút toán.");
        return user;
    }
    private String query(String value) {
        String q=value==null?"":value.trim();
        if(q.length()>255) throw error(HttpStatus.BAD_REQUEST,"INVALID_SEARCH","Nội dung tìm kiếm tối đa 255 ký tự.");
        return q;
    }
    @Transactional(readOnly=true)
    public List<Item> search(String query,Long actorId) { actor(actorId,false); return incidents.search(query(query)); }
    private String type(String type) {
        if(!Set.of("LOST","DAMAGED").contains(type==null?"":type))
            throw error(HttpStatus.BAD_REQUEST,"INVALID_INCIDENT_TYPE","Chọn Mất hoặc Hỏng nặng.");
        return type;
    }
    private Item openItem(Long id) {
        if(id==null || id<1) throw error(HttpStatus.BAD_REQUEST,"INVALID_ITEM","Chọn bản sách trong phiếu mượn đang mở.");
        Item item=incidents.item(id).orElseThrow(()->error(HttpStatus.NOT_FOUND,"LOAN_ITEM_NOT_FOUND","Không tìm thấy phiếu hoặc bản sách."));
        if(item.closedAt()!=null) throw error(HttpStatus.CONFLICT,"ITEM_ALREADY_CLOSED","Bản sách này đã được xử lý. Không thể ghi nhận lần thứ hai.");
        if(!"BORROWED".equals(item.copyStatus())) throw error(HttpStatus.CONFLICT,"COPY_STATUS_CHANGED","Trạng thái bản sao không khớp phiếu đang mở. Vui lòng tra cứu lại.");
        return item;
    }
    private ReturnFeeAssessment fee(Item item,OffsetDateTime at,boolean lock) {
        ReturnFeeAssessment saved=configuration.getRecordedReturnFees(item.loanId()).get(item.itemId());
        if(saved!=null) return saved; // Never mutate paid/waived/recorded principal.
        ReturnFeeAssessment fee=lock?configuration.calculateReturnFee(item.dueAt(),at,configuration.lockReturnFeeRate())
                :configuration.previewReturnFee(item.dueAt(),at);
        if(!"READY".equals(fee.status())) throw error(HttpStatus.CONFLICT,fee.status(),fee.message());
        return fee;
    }
    private String token(Item item,String type,BigDecimal factor,long amount,ReturnFeeAssessment fee) {
        // Price/rate/calendar/fee changes require a fresh review. The computed snapshot is always recalculated on the server.
        String value=item.itemId()+"|"+type+"|"+item.coverPrice().stripTrailingZeros().toPlainString()+"|"
                +factor.stripTrailingZeros().toPlainString()+"|"+amount+"|"+fee.amountVnd()+"|"+fee.overdueDays()+"|"
                +fee.dailyRateVnd()+"|"+fee.feeId()+"|"+fee.excludedDates()+"|"+fee.weeklySchedule()+"|"+fee.closedDates();
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Preview preview(Long itemId,String type,Long actorId) {
        actor(actorId,false); type=type(type); Item item=openItem(itemId);
        Factors factors=incidents.factors(false); BigDecimal factor="LOST".equals(type)?factors.lost():factors.damaged();
        long amount=IncidentCalculator.amount(item.coverPrice(),factor);
        ReturnFeeAssessment fee=fee(item,OffsetDateTime.now(),false);
        return new Preview(item,type,factor,amount,fee,token(item,type,factor,amount,fee));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED,rollbackFor=Exception.class)
    public Incident confirm(Long itemId,String type,String description,String previewToken,List<MultipartFile> files,Long actorId) {
        return confirm(itemId,type,description,previewToken,files,actorId,null,null);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED,rollbackFor=Exception.class)
    public Incident confirm(Long itemId,String type,String description,String previewToken,List<MultipartFile> files,
                            Long actorId,String proposedCompensationVnd,String adjustmentReason) {
        User actor=actor(actorId,false); type=type(type);
        description=description==null?"":description.trim();
        if(description.isEmpty() || description.length()>1900)
            throw error(HttpStatus.BAD_REQUEST,"INVALID_INCIDENT_DESCRIPTION","Mô tả tình trạng sách bắt buộc, tối đa 1.900 ký tự.");
        var images=IncidentImages.validate(files);
        Item initial=openItem(itemId);
        // Match lending/return order: title -> loan/item -> copy. Timestamp is sampled AFTER waiting.
        loans.lockTitleForReturn(itemId,initial.barcode()).orElseThrow(()->error(HttpStatus.CONFLICT,"ITEM_CHANGED","Phiếu đã thay đổi."));
        loans.lockReturnCandidate(itemId,initial.barcode()).orElseThrow(()->error(HttpStatus.CONFLICT,"ITEM_CHANGED","Phiếu đã thay đổi."));
        loans.lockCopyForReturn(initial.copyId());
        Item item=openItem(itemId);
        Factors factors=incidents.factors(true); BigDecimal factor="LOST".equals(type)?factors.lost():factors.damaged();
        long amount=IncidentCalculator.amount(item.coverPrice(),factor);
        // Stabilize reader debt before inserting the overdue/compensation fees, as the payment flow does.
        incidents.lockReader(item.readerId());
        OffsetDateTime at=incidents.now();
        ReturnFeeAssessment fee=fee(item,at,true);
        if(previewToken==null || !token(item,type,factor,amount,fee).equals(previewToken))
            throw error(HttpStatus.CONFLICT,"INCIDENT_PREVIEW_CHANGED","Giá, hệ số hoặc phí trễ hạn đã thay đổi. Vui lòng xem lại trước khi xác nhận.");
        if(!configuration.getRecordedReturnFees(item.loanId()).containsKey(item.itemId()))
            fee=configuration.recordReturnFee(item.itemId(),item.readerId(),fee);
        var adjustment=IncidentCalculator.adjustment(amount,proposedCompensationVnd,adjustmentReason);
        Long feeId=incidents.compensation(item,type,adjustment.amount());
        Long id=incidents.insert(item,type,description,factor,adjustment.amount(),feeId,actorId,actor.getFullName(),at,amount,adjustment.reason());
        if(adjustment.amount()!=amount) incidents.adjustmentAudit(id,item,factor,amount,adjustment.amount(),adjustment.reason(),actorId,actor.getFullName());
        for(var image:images) incidents.photo(id,image.filename(),image.contentType(),image.data());
        if(incidents.close(item,type,at,actorId,actor.getFullName())!=1)
            throw error(HttpStatus.CONFLICT,"INCIDENT_SAVE_FAILED","Không thể đóng lượt mượn; toàn bộ giao dịch được hủy.");
        incidents.history(item,type,description,actorId,actor.getFullName(),at);
        Incident result=detailInternal(id);
        if(!"REMOVED".equals(result.item().copyStatus())) throw error(HttpStatus.INTERNAL_SERVER_ERROR,"INCIDENT_SAVE_FAILED","Không thể loại bản sao khỏi kho; toàn bộ giao dịch được hủy.");
        return result;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public List<Incident> history(String query,Long actorId) {
        adjustmentActor(actorId,false); return incidents.incidentIds(query(query)).stream().map(this::detailInternal).toList();
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Incident detail(Long id,Long actorId) { adjustmentActor(actorId,false); return detailInternal(id); }
    private Incident detailInternal(Long id) {
        var row=incidents.row(id).orElseThrow(()->error(HttpStatus.NOT_FOUND,"INCIDENT_NOT_FOUND","Không tìm thấy nhật ký sự cố."));
        Item item=incidents.item(row.itemId()).orElseThrow(()->error(HttpStatus.NOT_FOUND,"ITEM_NOT_FOUND","Không tìm thấy bản sách."));
        return new Incident(row.id(),item,row.type(),row.description(),row.price(),row.factor(),row.amount(),row.feeId(),
                configuration.getRecordedReturnFees(item.loanId()).get(item.itemId()),row.actor(),row.at(),row.before(),row.after(),
                incidents.loanStatus(item.loanId()),incidents.photos(id),row.automatic(),row.reason(),row.actorId());
    }
    @Transactional(readOnly=true)
    public Image image(Long id,Long imageId,Long actorId) {
        adjustmentActor(actorId,false); return incidents.image(id,imageId).orElseThrow(()->error(HttpStatus.NOT_FOUND,"IMAGE_NOT_FOUND","Không tìm thấy ảnh sự cố."));
    }
    private AdjustmentRequest validate(AdjustmentRequest request) {
        if(request==null || request.reason()==null || request.reason().isBlank() || request.reason().trim().length()>1900)
            throw error(HttpStatus.BAD_REQUEST,"INVALID_ADJUSTMENT_REASON","Nhập lý do điều chỉnh từ 1 đến 1.900 ký tự.");
        if(!Set.of("MISTAKEN","FOUND","REASSESSED").contains(Objects.toString(request.caseType(),""))
            || !Set.of("BORROWED","AVAILABLE","REPAIR").contains(Objects.toString(request.targetStatus(),"")))
            throw error(HttpStatus.BAD_REQUEST,"INVALID_ADJUSTMENT","Chọn trường hợp và trạng thái sau điều chỉnh hợp lệ.");
        return new AdjustmentRequest(request.reason().trim(),request.caseType(),request.targetStatus());
    }
    private Incident lockAdjustmentIncident(Long id) {
        Incident value=detailInternal(id);
        Long bookId=loans.lockTitleForReturn(value.item().itemId(),value.item().barcode()).orElseThrow();
        loans.lockReturnCandidate(value.item().itemId(),value.item().barcode());
        loans.lockPendingQueueForReturn(bookId); loans.lockCopyForReturn(value.item().copyId()); incidents.lockReader(value.item().readerId()); incidents.lockIncident(id);
        return detailInternal(id);
    }
    private void reversible(Incident incident) {
        if(!"REMOVED".equals(incident.item().copyStatus()) || !incident.type().equals(incident.item().resolutionReason())
           || !incident.recordedAt().equals(incident.item().closedAt()))
            throw error(HttpStatus.CONFLICT,"INCIDENT_CHANGED","Sự cố đã đảo hoặc trạng thái không còn khớp giao dịch gốc.");
    }
    @Transactional(readOnly=true)
    public List<Adjustment> adjustments(Long id,Long actorId) {
        adjustmentActor(actorId,false); detailInternal(id); return incidents.adjustments(id);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public AdjustmentPreview adjustmentPreview(Long id,String target,Long actorId) {
        adjustmentActor(actorId,false); Incident i=detailInternal(id); reversible(i);
        if(!Set.of("BORROWED","AVAILABLE","REPAIR").contains(Objects.toString(target,"")))
            throw error(HttpStatus.BAD_REQUEST,"INVALID_ADJUSTMENT","Chọn trạng thái hợp lệ.");
        long paid=incidents.paid(i.compensationFeeId(),false);
        String projectedCopy=target;
        if("AVAILABLE".equals(target) && loans.findEligiblePendingForReturn(incidents.bookId(i.item().copyId()),
            incidents.now().atZoneSameInstant(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate()).isPresent()) projectedCopy="HELD";
        return new AdjustmentPreview(i.compensationVnd(),i.compensationVnd()-paid,paid,projectedCopy,incidents.projectedLoanStatus(i.item(),target));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED,rollbackFor=Exception.class)
    public Adjustment requestAdjustment(Long id,AdjustmentRequest request,Long actorId) {
        User actor=adjustmentActor(actorId,false); request=validate(request);
        Incident i=lockAdjustmentIncident(id); reversible(i);
        if("FOUND".equals(request.caseType()) && !"LOST".equals(i.type()))
            throw error(HttpStatus.BAD_REQUEST,"INVALID_ADJUSTMENT","Chỉ sự cố Mất được chọn Tìm lại sách đã báo mất.");
        if(!incidents.adjustments(id).isEmpty()) throw error(HttpStatus.CONFLICT,"ADJUSTMENT_EXISTS","Sự cố đã có bút toán điều chỉnh. Vui lòng xem lịch sử.");
        Long requestId=incidents.requestAdjustment(id,request,actorId,actor.getFullName());
        incidents.requestedAdjustmentAudit(requestId,actorId);
        return incidents.adjustments(id).get(0);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED,rollbackFor=Exception.class)
    public Adjustment confirmAdjustment(Long id,Long adjustmentId,Long actorId) {
        User actor=adjustmentActor(actorId,true); Incident i=lockAdjustmentIncident(id);
        Adjustment a=incidents.adjustments(id).stream().filter(x->x.id().equals(adjustmentId)).findFirst()
            .orElseThrow(()->error(HttpStatus.NOT_FOUND,"ADJUSTMENT_NOT_FOUND","Không tìm thấy bút toán của sự cố này."));
        if(!"PENDING".equals(a.status())) throw error(HttpStatus.CONFLICT,"ADJUSTMENT_CONFIRMED","Bút toán đã xác nhận; không được đảo lần nữa.");
        reversible(i); incidents.paid(i.compensationFeeId(),true);
        String before=incidents.snapshot(i.item());
        OffsetDateTime at=incidents.now();
        String copyStatus=a.targetStatus();
        if("AVAILABLE".equals(copyStatus)) {
            Long bookId=loans.lockTitleForReturn(i.item().itemId(),i.item().barcode()).orElseThrow();
            loans.lockPendingQueueForReturn(bookId);
            var next=loans.findEligiblePendingForReturn(bookId,at.atZoneSameInstant(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate());
            if(next.isPresent()) {
                if(loans.allocateReturnedCopy(next.get().id(),bookId,i.item().copyId(),at,configuration.calculateReservationPickupDeadline(at))!=1)
                    throw error(HttpStatus.CONFLICT,"QUEUE_CHANGED","Hàng đợi đã thay đổi; toàn bộ điều chỉnh được hủy.");
                copyStatus="HELD";
            }
        }
        String projected=incidents.projectedSnapshot(i.item(),a.targetStatus(),copyStatus,at,actorId,actor.getFullName());
        incidents.confirmAdjustment(a.id(),actorId,actor.getFullName(),before,projected,at);
        incidents.applyAdjustment(i.item(),a,at,actorId,actor.getFullName(),copyStatus);
        String after=incidents.snapshot(i.item()); incidents.adjustmentAudit(a,actorId,before,after);
        return incidents.adjustments(id).get(0);
    }
    private ApiException error(HttpStatus status,String code,String message) { return new ApiException(status,code,message); }
}
