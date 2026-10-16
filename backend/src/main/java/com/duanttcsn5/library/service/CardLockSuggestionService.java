package com.duanttcsn5.library.service;

import com.duanttcsn5.library.dto.librarycard.CardLockSuggestionDtos.*;
import com.duanttcsn5.library.dto.librarycard.LockLibraryCardRequest;
import com.duanttcsn5.library.entity.LibraryCard;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class CardLockSuggestionService {
    private static final ZoneId ZONE=ZoneId.of("Asia/Ho_Chi_Minh");
    private final CardLockSuggestionRepository suggestions;
    private final LibraryCardRepository cards;
    private final LibraryCardService cardService;
    private final LibraryConfigurationService configuration;
    private final UserRepository users;
    private final Clock clock;
    @Autowired
    public CardLockSuggestionService(CardLockSuggestionRepository suggestions,LibraryCardRepository cards,
            LibraryCardService cardService,LibraryConfigurationService configuration,UserRepository users) {
        this(suggestions,cards,cardService,configuration,users,Clock.system(ZONE));
    }
    public CardLockSuggestionService(CardLockSuggestionRepository suggestions,LibraryCardRepository cards,
            LibraryCardService cardService,LibraryConfigurationService configuration,UserRepository users,Clock clock) {
        this.suggestions=suggestions;this.cards=cards;this.cardService=cardService;
        this.configuration=configuration;this.users=users;this.clock=clock;
    }
    public static String reasonCode(long days,BigDecimal debt,long threshold) {
        boolean overdue=days>30, excessive=debt.compareTo(BigDecimal.valueOf(threshold))>0;
        return overdue ? (excessive ? "BOTH":"OVERDUE") : (excessive ? "DEBT":null);
    }
    @Transactional(readOnly=true)
    @PreAuthorize("hasAnyRole('LIBRARY_MANAGER', 'ADMIN')")
    public Settings settings() { return suggestions.settings(); }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    @PreAuthorize("hasAnyRole('LIBRARY_MANAGER', 'ADMIN')")
    public Settings updateSettings(UpdateSettings request,Long actor) {
        manager(actor);
        BigDecimal value=request==null?null:request.debtThresholdVnd();
        if(value==null || value.signum()<0 || value.stripTrailingZeros().scale()>0
                || value.compareTo(new BigDecimal("1000000000000"))>0)
            throw error(HttpStatus.BAD_REQUEST,"INVALID_DEBT_THRESHOLD","Ngưỡng nợ phải là số nguyên từ 0 đến 1.000.000.000.000 VND.");
        suggestions.lockScan(); suggestions.updateThreshold(value.longValueExact(),actor,now());
        refreshLocked(); return suggestions.settings();
    }
    /** Scheduled job calls this proxied transaction; it never invokes lockCard. */
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void refresh() { suggestions.lockScan(); refreshLocked(); }
    private void refreshLocked() {
        long threshold=suggestions.settings().debtThresholdVnd();
        for(Long id:suggestions.cardIds()) {
            LibraryCard card=lockedCard(id);
            reconcile(card,facts(card,threshold),now());
        }
        suggestions.checkedAt(now());
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    @PreAuthorize("hasAnyRole('LIBRARY_MANAGER', 'ADMIN')")
    public Page list(boolean history,int page,int size) {
        if(page<0 || size<1 || size>100) throw error(HttpStatus.BAD_REQUEST,"INVALID_PAGE","Trang phải từ 0, số dòng từ 1 đến 100.");
        suggestions.lockScan(); refreshLocked();
        long total=suggestions.count(history);
        return new Page(suggestions.list(history,size,(long)page*size),page,size,total,(total+size-1)/size,suggestions.settings());
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    @PreAuthorize("hasAnyRole('LIBRARY_MANAGER', 'ADMIN')")
    public Detail detail(Long id) {
        suggestions.lockScan(); LibraryCard card=lockedSuggestionCard(id);
        Facts facts=facts(card,suggestions.settings().debtThresholdVnd()); reconcile(card,facts,now());
        return new Detail(get(id),cardService.getCardDetails(card.getId()).card(),suggestions.evidence(id),
                facts.evidence(),facts.days(),facts.debt(),facts.threshold(),facts.reason());
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    @PreAuthorize("hasAnyRole('LIBRARY_MANAGER', 'ADMIN')")
    public Decision accept(Long id,LockLibraryCardRequest request,Long actor,String ip) {
        String actorName=manager(actor); String reason=validReason(request==null?null:request.reason());
        OffsetDateTime end=request==null?null:request.endsAt();
        if(end!=null && (end.getYear()<1 || end.getYear()>9999 || !end.isAfter(now())))
            throw error(HttpStatus.BAD_REQUEST,"INVALID_LOCK_EXPIRY","Thời điểm hết hạn khóa phải sau thời điểm hiện tại.");
        suggestions.lockScan(); LibraryCard card=lockedSuggestionCard(id); requirePending(id);
        Facts facts=facts(card,suggestions.settings().debtThresholdVnd()); reconcile(card,facts,now());
        if(!"PENDING".equals(get(id).status())) return obsolete(id,card);
        var locked=cardService.lockCard(card.getId(),new LockLibraryCardRequest(reason,end),actor,ip);
        OffsetDateTime decisionAt=locked.manualLock().startedAt();
        suggestions.decide(id,"ACCEPTED",actor,actorName,reason,locked.status(),locked.manualLock().endsAt(),decisionAt);
        return new Decision(get(id),locked,true,"Đã chấp nhận đề xuất và khóa thẻ. Trả sách và thu phí vẫn được phép.");
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    @PreAuthorize("hasAnyRole('LIBRARY_MANAGER', 'ADMIN')")
    public Decision ignore(Long id,Ignore request,Long actor) {
        String actorName=manager(actor);String reason=validReason(request==null?null:request.reason());
        suggestions.lockScan();LibraryCard card=lockedSuggestionCard(id);requirePending(id);
        Facts facts=facts(card,suggestions.settings().debtThresholdVnd());reconcile(card,facts,now());
        if(!"PENDING".equals(get(id).status())) return obsolete(id,card);
        var current=cardService.getCardDetails(card.getId()).card();
        suggestions.decide(id,"IGNORED",actor,actorName,reason,current.status(),null,now());
        return new Decision(get(id),current,false,"Đã bỏ qua đề xuất. Thẻ không bị khóa bởi quyết định này.");
    }
    private Decision obsolete(Long id,LibraryCard card) {
        return new Decision(get(id),cardService.getCardDetails(card.getId()).card(),false,
                "Đề xuất đã kết thúc vì bạn đọc không còn vi phạm hoặc thẻ đã bị khóa. Không tạo lệnh khóa mới.");
    }
    private void requirePending(Long id) {
        if(!"PENDING".equals(get(id).status())) throw error(HttpStatus.CONFLICT,"SUGGESTION_ALREADY_DECIDED","Đề xuất đã được xử lý. Vui lòng làm mới danh sách.");
    }
    private Suggestion get(Long id) {
        return suggestions.find(id).orElseThrow(()->error(HttpStatus.NOT_FOUND,"SUGGESTION_NOT_FOUND","Không tìm thấy đề xuất khóa thẻ."));
    }
    private LibraryCard lockedSuggestionCard(Long id) {
        if(id==null || id<1) throw error(HttpStatus.BAD_REQUEST,"INVALID_SUGGESTION_ID","Mã đề xuất không hợp lệ.");
        Long card=suggestions.cardId(id);
        if(card==null) throw error(HttpStatus.NOT_FOUND,"SUGGESTION_NOT_FOUND","Không tìm thấy đề xuất khóa thẻ.");
        return lockedCard(card);
    }
    private LibraryCard lockedCard(Long id) {
        Long reader=cards.findReaderId(id).orElseThrow(()->error(HttpStatus.NOT_FOUND,"LIBRARY_CARD_NOT_FOUND","Không tìm thấy thẻ."));
        cards.lockReaderForTransactions(reader);
        return cards.findByIdForUpdate(id).orElseThrow(()->error(HttpStatus.NOT_FOUND,"LIBRARY_CARD_NOT_FOUND","Không tìm thấy thẻ."));
    }
    record Facts(long days,BigDecimal debt,long threshold,String reason,Evidence evidence) {}
    private Facts facts(LibraryCard card,long threshold) {
        LocalDate today=LocalDate.ofInstant(clock.instant(),ZONE);
        var raw=suggestions.overdue(card.getUser().getId(),today.atStartOfDay(ZONE).toOffsetDateTime());
        Set<LocalDate> dates=new HashSet<>();for(var row:raw) dates.add(row.dueAt().atZoneSameInstant(ZONE).toLocalDate());
        var counted=configuration.calculateOverdueOpenDays(dates,today);
        var overdue=raw.stream().map(row->new OverdueItem(row.loanId(),row.loanNumber(),row.itemId(),row.bookTitle(),
            row.barcode(),row.dueAt(),counted.getOrDefault(row.dueAt().atZoneSameInstant(ZONE).toLocalDate(),0L))).toList();
        long days=overdue.stream().mapToLong(OverdueItem::overdueDays).max().orElse(0);
        var fees=suggestions.fees(card.getUser().getId());
        BigDecimal debt=fees.stream().map(OutstandingFee::outstandingVnd).reduce(BigDecimal.ZERO,BigDecimal::add);
        return new Facts(days,debt,threshold,reasonCode(days,debt,threshold),new Evidence(overdue,fees));
    }
    private void reconcile(LibraryCard card,Facts facts,OffsetDateTime now) {
        var open=suggestions.openEpisode(card.getId());
        if(facts.reason()==null) { open.ifPresent(row->suggestions.endEpisode(row.id(),now));return; }
        boolean locked=card.hasActiveManualLock(now) || "LOCKED".equalsIgnoreCase(card.getStatus());
        if(locked) { open.filter(row->"PENDING".equals(row.status())).ifPresent(row->suggestions.cancelLocked(row.id(),now));return; }
        if(open.isPresent() && ("ACCEPTED".equals(open.get().status()) || "CANCELLED_LOCKED".equals(open.get().status()))) {
            // The previous lock expired/was released; ongoing violations require a new manager review.
            suggestions.closeLockCycle(open.get().id(),now);open=Optional.empty();
        }
        if(open.isPresent()) {
            var row=open.get();if("PENDING".equals(row.status())) suggestions.updatePending(row.id(),facts.days(),facts.debt(),
                    facts.threshold(),facts.reason(),facts.evidence(),now);
        } else suggestions.create(card.getId(),card.getCardNumber(),card.getUser().getFullName(),facts.days(),facts.debt(),
                facts.threshold(),facts.reason(),facts.evidence(),now);
    }
    private String manager(Long id) {
        var actor=id==null?null:users.findById(id).orElse(null);
        if(actor==null || actor.getRole()==null || !Set.of("LIBRARY_MANAGER", "ADMIN").contains(actor.getRole().getCode())
                || !"ACTIVE".equals(actor.getStatus()) || (actor.getLockedUntil()!=null && actor.getLockedUntil().isAfter(now())))
            throw error(HttpStatus.FORBIDDEN,"LIBRARY_MANAGER_REQUIRED","Chỉ Quản lý thư viện đang hoạt động được thực hiện thao tác này.");
        return actor.getFullName();
    }
    private String validReason(String value) {
        String reason=value==null?"":value.strip();
        if(reason.isBlank() || reason.codePoints().allMatch(c->Character.isWhitespace(c)||Character.isSpaceChar(c)) || reason.length()>1000)
            throw error(HttpStatus.BAD_REQUEST,"INVALID_DECISION_REASON","Bắt buộc nhập lý do, tối đa 1000 ký tự.");
        return reason;
    }
    private OffsetDateTime now() { return OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS); }
    private static ApiException error(HttpStatus status,String code,String message) { return new ApiException(status,code,message); }
}
