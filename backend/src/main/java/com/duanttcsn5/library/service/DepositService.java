package com.duanttcsn5.library.service;

import com.duanttcsn5.library.dto.loan.DepositDtos.*;
import com.duanttcsn5.library.dto.loan.FeeCollectionResponse.Fee;
import com.duanttcsn5.library.entity.User;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class DepositService {
    private final DepositRepository deposits;
    private final FeePaymentRepository fees;
    private final UserRepository users;
    private final LoanService loans;
    private final LoanRepository loanRepository;
    private final boolean activationEnabled;
    public DepositService(DepositRepository deposits,FeePaymentRepository fees,UserRepository users,LoanService loans,
            LoanRepository loanRepository,@Value("${app.deposits.activation-enabled:false}") boolean activationEnabled) {
        this.deposits=deposits;this.fees=fees;this.users=users;this.loans=loans;this.loanRepository=loanRepository;this.activationEnabled=activationEnabled;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Policies policies(int page,Long actor) { staff(actor);page(page);var data=deposits.policies(page);return new Policies(data.current(),data.history(),activationEnabled); }
    @Transactional
    public Policies policy(PolicyRequest request,Long actor) {
        var user=staff(actor);
        if(!Set.of("ADMIN","LIBRARY_MANAGER").contains(user.getRole().getCode()))throw forbidden();
        if(!activationEnabled)throw error("DEPOSIT_ACTIVATION_DISABLED","Chưa mở bước kích hoạt cọc. Cần hoàn tất kiểm thử và bật cấu hình triển khai trước.");
        if(request==null || request.percentage()==null || request.percentage().signum()<=0
                || request.percentage().compareTo(java.math.BigDecimal.valueOf(100))>0 || request.percentage().scale()>4
                || (request.effectiveAt()!=null && request.effectiveAt().isBefore(OffsetDateTime.now())))
            throw bad("INVALID_DEPOSIT_POLICY","Tỷ lệ phải lớn hơn 0, tối đa 100%, tối đa 4 số thập phân; không hồi tố hiệu lực.");
        deposits.policy(request,actor);return policies(0,actor);
    }
    @Transactional
    public List<Account> prepare(PrepareRequest request,Long actor) {
        collector(actor);
        if(request==null || request.cardNumber()==null || request.cardNumber().isBlank() || request.cardNumber().length()>100
                || request.barcodes()==null || request.barcodes().isEmpty() || request.barcodes().size()>10)
            throw bad("INVALID_DEPOSIT_REQUEST","Nhập mã thẻ và từ 1 đến 10 mã vạch.");
        var barcodes=request.barcodes().stream().map(s->s==null?"":s.strip()).sorted().toList();
        if(new HashSet<>(barcodes).size()!=barcodes.size() || barcodes.stream().anyMatch(s->s.isBlank() || s.length()>100))throw bad("INVALID_DEPOSIT_COPIES","Mã vạch không hợp lệ hoặc bị trùng.");
        Long reader=loanRepository.findReaderIdByCardNumber(request.cardNumber().strip()).orElseThrow(()->error("READER_NOT_FOUND","Không tìm thấy thẻ bạn đọc."));
        fees.reader(reader,true).orElseThrow(()->error("READER_NOT_FOUND","Không tìm thấy bạn đọc."));
        deposits.lockPolicy();var policy=deposits.current();
        if(policy==null)throw error("DEPOSIT_NOT_ACTIVE","Chưa có chính sách cọc hiệu lực; không tạo nghĩa vụ cọc.");
        var eligibility=loans.readerEligibility(request.cardNumber().strip(),actor);
        if(!eligibility.blockReasons().isEmpty() || eligibility.borrowedBooks()+barcodes.size()>eligibility.maxBooks())
            throw error("DEPOSIT_BORROWING_BLOCKED","Bạn đọc chưa đủ điều kiện mượn. Kiểm tra thẻ, hạn mượn, nợ phí và số sách trước khi thu cọc.");
        var result=new ArrayList<Account>();
        for(String barcode:barcodes) {
            var copy=deposits.copy(barcode).orElseThrow(()->bad("COPY_NOT_FOUND","Không tìm thấy bản sao "+barcode));
            // Preparing/collecting never reserves a copy; handover retains its full availability/hold checks.
            if(!Set.of("AVAILABLE","HELD").contains(copy.status()) || !deposits.holdAllows(reader,copy.id()))throw error("DEPOSIT_COPY_UNAVAILABLE","Bản sao chưa sẵn sàng bàn giao cho bạn đọc này: "+barcode);
            var existing=deposits.reusable(reader,copy.id());
            if(existing.isPresent()) { result.add(deposits.find(existing.get()).orElseThrow());continue; }
            DepositCalculator.Quote quote;
            try {quote=DepositCalculator.quote(List.of(new DepositCalculator.CopyPrice(copy.id(),copy.price())),policy.percentage());}
            catch(IllegalArgumentException ex) {throw error(ex.getMessage(),"Không xác định được cọc: cần giá bìa và tỷ lệ hợp lệ, số tiền tối thiểu 1 VND.");}
            Long id=deposits.account(reader,copy.id(),policy,copy.price(),quote.totalVnd(),actor);
            result.add(deposits.find(id).orElseThrow());
        }
        return List.copyOf(result);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Accounts accounts(Long reader,String query,int page,Long actor) {
        var user=identity(actor);page(page);
        if("READER".equals(user.getRole().getCode())) {if(reader!=null && !reader.equals(actor))throw hidden();reader=actor;}
        else staff(actor);
        query=query==null?"":query.strip();if(query.length()>255)throw bad("INVALID_QUERY","Từ khóa quá dài.");
        return deposits.accounts(reader,query,page);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Transactions history(Long reader,int page,Long actor) {
        var user=identity(actor);page(page);
        if("READER".equals(user.getRole().getCode())) {if(!actor.equals(reader))throw hidden();}else staff(actor);
        return deposits.transactions(reader,page);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED,rollbackFor=Exception.class)
    public Transaction transact(Long reader,String kind,TransactionRequest request,Long actor) {
        var user=collector(actor);
        if(!Set.of("COLLECT","REFUND","DEDUCT").contains(kind))throw bad("INVALID_DEPOSIT_KIND","Loại giao dịch không hợp lệ.");
        if(request==null || request.requestKey()==null || request.accountIds()==null || request.accountIds().isEmpty()
                || request.accountIds().size()>10 || request.expectedAmountVnd()<=0 || !kind.equals(request.confirmation())
                || request.accountIds().stream().anyMatch(id->id==null || id<1)
                || new HashSet<>(request.accountIds()).size()!=request.accountIds().size())
            throw bad("DEPOSIT_CONFIRMATION_REQUIRED","Xác nhận đúng số tiền và thao tác thu/hoàn/khấu trừ trước khi ghi nhận.");
        var ids=request.accountIds().stream().sorted().toList();
        String hash=hash(kind+":"+ids+":"+request.expectedAmountVnd());
        var readerInfo=fees.reader(reader,true).orElseThrow(()->error("READER_NOT_FOUND","Không tìm thấy bạn đọc."));
        var replay=deposits.replay(reader,request.requestKey());
        if(replay.isPresent()) {
            if(!replay.get().actor().equals(actor) || !replay.get().fingerprint().equals(hash))throw error("DEPOSIT_REQUEST_REUSED","Mã giao dịch đã dùng cho nội dung khác.");
            return deposits.transaction(replay.get().id());
        }
        for(Long id:ids)deposits.lockAccount(id);
        // Shared reader -> account -> fee order, with existing fee collection serializing on reader.
        var debt=fees.outstanding(reader,true);
        var accounts=new ArrayList<Account>();var amounts=new LinkedHashMap<Long,Long>();var allocations=new LinkedHashMap<Fee,Long>();
        long total=0;
        for(Long id:ids) {
            var a=deposits.find(id).orElseThrow(()->error("DEPOSIT_NOT_FOUND","Không tìm thấy khoản cọc."));
            if(!a.readerId().equals(reader))throw forbidden();accounts.add(a);
            long amount;
            if("COLLECT".equals(kind)) {
                if(a.collectedVnd()!=0 || a.itemId()!=null)throw error("DEPOSIT_ALREADY_COLLECTED","Khoản cọc đã được thu; không thu lần thứ hai.");
                amount=a.requiredVnd();
            } else {
                if(a.itemId()!=null && a.returnedAt()==null)throw error("DEPOSIT_COPY_NOT_RETURNED","Cọc của sách chưa trả vẫn phải giữ.");
                if("REFUND".equals(kind)) {
                    if(a.outstandingVnd()>0)throw error("DEPOSIT_DEBT_UNSETTLED","Cần đối soát nghĩa vụ đã xác nhận trước khi hoàn cọc.");
                    amount=a.heldVnd();
                } else {
                    if(a.itemId()==null)throw error("DEPOSIT_NO_OBLIGATION","Chưa bàn giao nên không có nghĩa vụ của cuốn để khấu trừ.");
                    var feeIds=deposits.feeIds(a.itemId());long remaining=a.heldVnd();amount=0;
                    for(var fee:debt)if(feeIds.contains(fee.id()) && remaining>0) {
                        long part=Math.min(remaining,fee.outstandingAmountVnd());allocations.put(fee,part);amount+=part;remaining-=part;
                    }
                }
            }
            if(amount<=0)throw error("DEPOSIT_NO_AMOUNT","Không có số tiền hợp lệ cho thao tác này; hãy tải lại đối soát.");
            total=Math.addExact(total,amount);amounts.put(id,amount);
        }
        if(total!=request.expectedAmountVnd())throw error("DEPOSIT_AMOUNT_CHANGED","Số tiền đã thay đổi. Tải lại và xác nhận lại trước khi tiếp tục.");
        Long payment=null;
        if("DEDUCT".equals(kind)) {
            long debtBefore=debt.stream().mapToLong(Fee::outstandingAmountVnd).reduce(0,Math::addExact);
            payment=deposits.offsetReceipt(readerInfo,actor,user.getFullName(),total,debtBefore-total,request.requestKey(),hash);
            for(var allocation:allocations.entrySet()) {
                fees.insertItem(payment,allocation.getKey(),allocation.getValue());
                if(fees.applyPayment(reader,allocation.getKey(),allocation.getValue())!=1)throw error("DEPOSIT_DEBT_CHANGED","Nghĩa vụ đã thay đổi; giao dịch được hủy.");
            }
        }
        Long transaction=deposits.transaction(reader,request.requestKey(),hash,kind,total,actor,user.getFullName(),payment);
        for(var entry:amounts.entrySet())deposits.entry(transaction,entry.getKey(),entry.getValue());
        return deposits.transaction(transaction);
    }
    private User identity(Long id) {
        if(id==null)throw new ApiException(HttpStatus.UNAUTHORIZED,"LOGIN_REQUIRED","Vui lòng đăng nhập.");
        var user=users.findById(id).orElseThrow(()->new ApiException(HttpStatus.UNAUTHORIZED,"LOGIN_REQUIRED","Phiên đăng nhập không hợp lệ."));
        if(!"ACTIVE".equals(user.getStatus()) || user.getRole()==null)throw forbidden();return user;
    }
    private User staff(Long id) {var user=identity(id);if(!Set.of("ADMIN","LIBRARY_MANAGER","LIBRARIAN").contains(user.getRole().getCode()))throw forbidden();return user;}
    private User collector(Long id) {var user=staff(id);if(!Set.of("ADMIN","LIBRARIAN").contains(user.getRole().getCode()))throw forbidden();return user;}
    private static void page(int value) {if(value<0 || value>1000000)throw bad("INVALID_PAGE","Trang không hợp lệ.");}
    private static ApiException forbidden() {return new ApiException(HttpStatus.FORBIDDEN,"DEPOSIT_FORBIDDEN","Không có quyền thực hiện thao tác hoặc xem cọc của bạn đọc khác.");}
    private static ApiException hidden() {return new ApiException(HttpStatus.NOT_FOUND,"DEPOSIT_NOT_FOUND","Không tìm thấy dữ liệu cọc.");}
    private static ApiException error(String code,String message) {return new ApiException(HttpStatus.CONFLICT,code,message);}
    private static ApiException bad(String code,String message) {return new ApiException(HttpStatus.BAD_REQUEST,code,message);}
    private static String hash(String value) {try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
