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
                || !(manager?Set.of("LIBRARY_MANAGER","ADMIN"):Set.of("LIBRARIAN")).contains(user.getRole().getCode()))
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
        OffsetDateTime at=OffsetDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
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
        actor(actorId,false); return incidents.incidentIds(query(query)).stream().map(this::detailInternal).toList();
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Incident detail(Long id,Long actorId) { actor(actorId,false); return detailInternal(id); }
    private Incident detailInternal(Long id) {
        var row=incidents.row(id).orElseThrow(()->error(HttpStatus.NOT_FOUND,"INCIDENT_NOT_FOUND","Không tìm thấy nhật ký sự cố."));
        Item item=incidents.item(row.itemId()).orElseThrow(()->error(HttpStatus.NOT_FOUND,"ITEM_NOT_FOUND","Không tìm thấy bản sách."));
        return new Incident(row.id(),item,row.type(),row.description(),row.price(),row.factor(),row.amount(),row.feeId(),
                configuration.getRecordedReturnFees(item.loanId()).get(item.itemId()),row.actor(),row.at(),row.before(),row.after(),
                incidents.loanStatus(item.loanId()),incidents.photos(id),row.automatic(),row.reason(),row.actorId());
    }
    @Transactional(readOnly=true)
    public Image image(Long id,Long imageId,Long actorId) {
        actor(actorId,false); return incidents.image(id,imageId).orElseThrow(()->error(HttpStatus.NOT_FOUND,"IMAGE_NOT_FOUND","Không tìm thấy ảnh sự cố."));
    }
    private ApiException error(HttpStatus status,String code,String message) { return new ApiException(status,code,message); }
}
