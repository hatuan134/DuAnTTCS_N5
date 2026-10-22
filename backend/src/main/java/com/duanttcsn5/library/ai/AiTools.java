package com.duanttcsn5.library.ai;

import com.duanttcsn5.library.service.*;
import com.duanttcsn5.library.repository.BookRepository;
import com.duanttcsn5.library.repository.BookCopyRepository;
import com.duanttcsn5.library.dto.book.BookResponse;
import com.duanttcsn5.library.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static com.duanttcsn5.library.ai.AiTypes.*;

@Service
public class AiTools {
    private final AiAccess access;
    private final AiNavigation navigation;
    private final BookCatalogService catalog;
    private final LoanService loans;
    private final LibraryCardService cards;
    private final BookReservationService reservations;
    private final BookCopyService copies;
    private final LoanReportService reports;
    private final AuditLogService audit;
    private final BookRepository bookRepository;
    private final BookCopyRepository copyRepository;
    private final AiReadRepository readRepository;
    // These domain services provide the same read models as the existing controllers.
    // Optional field wiring retains compatibility with legacy unit tests that construct AiTools directly.
    @Autowired(required=false) private DashboardService dashboard;
    @Autowired(required=false) private AuthorService authors;
    @Autowired(required=false) private CategoryService categories;
    @Autowired(required=false) private LibraryConfigurationService libraryConfiguration;
    @Autowired(required=false) private CardLockSuggestionService cardLockSuggestions;
    @Autowired(required=false) private CardTypeService cardTypes;
    @Autowired(required=false) private UserManagementService userManagement;
    @Autowired(required=false) private ReaderRegistrationService readerRegistration;
    @Autowired(required=false) private ReaderSelfService readerSelf;
    @Autowired(required=false) private BookIncidentService bookIncidents;
    @Autowired(required=false) private LoanReminderService loanReminders;
    @Autowired(required=false) private FeeReductionService feeReductions;
    @Autowired(required=false) private OverdueDebtReportService overdueDebts;
    public AiTools(AiAccess access,AiNavigation navigation,BookCatalogService catalog,LoanService loans,
                   LibraryCardService cards,BookReservationService reservations,BookCopyService copies,
                   LoanReportService reports,AuditLogService audit,BookRepository bookRepository,
                   BookCopyRepository copyRepository,AiReadRepository readRepository) {
        this.access=access;this.navigation=navigation;this.catalog=catalog;this.loans=loans;
        this.cards=cards;this.reservations=reservations;this.copies=copies;this.reports=reports;
        this.audit=audit;this.bookRepository=bookRepository;this.copyRepository=copyRepository;this.readRepository=readRepository;
    }
    static Map<String,Object> map(Object... entries) {
        Map<String,Object> m=new LinkedHashMap<>();
        for(int i=0;i<entries.length;i+=2) if(entries[i+1]!=null) m.put((String)entries[i],entries[i+1]);
        return m;
    }
    private Map<String,Object> stringProperty(String description) { return map("type","string","description",description); }
    private Map<String,Object> integerProperty(String description) { return map("type","integer","description",description,"minimum",0,"maximum",10000); }
    private Map<String,Object> schema(String name,String description,Map<String,Object> properties,List<String> required) {
        return map("type","function","function",map("name",name,"description",description,"parameters",map("type","object","properties",properties,"required",required,"additionalProperties",false)));
    }
    public List<Map<String,Object>> definitions() {
        List<Map<String,Object>> result=new ArrayList<>();
        result.add(schema("getAvailableFeatures", "Danh sách các chức năng LIBRA mà vai trò đăng nhập hiện tại được phép truy cập; dùng để hướng dẫn người dùng mà không bịa chức năng.", Map.of(), List.of()));
        result.add(schema("getLibraryInventory","Số lượng đầu sách và bản sao hiện có trong thư viện. Không nhầm với lượt mượn theo tháng.",Map.of(),List.of()));
        result.add(schema("searchBooks","Tìm sách THỰC theo tên, tác giả, chủ đề và NĂM XUẤT BẢN. Khi người dùng hỏi năm 2026, truyền publicationYear=2026, KHÔNG đưa 'sách xuất bản năm 2026' làm từ khóa. Không bịa kết quả.",map("query",stringProperty("Từ khóa tên/tác giả/chủ đề, để trống nếu chỉ lọc theo năm"),"publicationYear",integerProperty("Năm xuất bản được lưu trong database, không phải năm nhập sách"),"page",integerProperty("Trang"),"availableOnly",map("type","boolean")),List.of()));
        result.add(schema("openCatalogFiltered","Mở trang tra cứu sách với từ khóa và bộ lọc còn sách được áp dụng ngay.",map("keyword",stringProperty("Từ khóa tối đa 100 ký tự"),"availableOnly",map("type","boolean"),"publicationYear",integerProperty("Năm xuất bản")),List.of()));
        result.add(schema("getBookAvailability","Thông tin công khai và số bản sẵn sàng của ID sách đã tìm thấy.",map("bookId",map("type","integer","description","ID sách đã tìm thấy","minimum",1)),List.of("bookId")));
        result.add(schema("navigate","Mở chức năng được cấp quyền và hướng dẫn đã xác minh từ giao diện.",map("destination",map("type","string","enum",navigation.keys())),List.of("destination")));
        if(access.role().equals("READER")) {
            result.add(schema("getMyLoans","Sách đang mượn của chính tài khoản hiện tại.",map("page",integerProperty("Trang")),List.of()));
            result.add(schema("getMyOverdueLoans","Sách của tôi đã qua hạn trả; không tính tiền phạt.",map("page",integerProperty("Trang")),List.of()));
            result.add(schema("getMyLoanHistory","Lịch sử sách đã trả của chính tôi.",map("page",integerProperty("Trang")),List.of()));
            result.add(schema("getMyLibraryCard","Trạng thái, ngày cấp, hạn thẻ của chính tôi.",Map.of(),List.of()));
            result.add(schema("getMyReservations","Các đơn đặt giữ của chính tôi.",map("page",integerProperty("Trang")),List.of()));
            result.add(schema("getMyProfileOverview","Trạng thái hồ sơ, mã bạn đọc và thẻ của chính tài khoản đăng nhập. Không nhận readerId từ mô hình.",Map.of(),List.of()));
        }
        if(Set.of("LIBRARIAN","LIBRARY_MANAGER","ADMIN").contains(access.role())) {
            result.add(schema("getLibraryCardStatusSummary",
                "Đếm TỪNG trạng thái thẻ thư viện: ACTIVE, LOCKED, EXPIRED, DISABLED. Dùng khi hỏi bao nhiêu thẻ bị khóa, hết hạn, cần chú ý, 'trong số đó'. Không lấy tên chủ thẻ.",
                Map.of(),List.of()));
            result.add(schema("searchAuthorizedLibraryCards",
                "Tra cứu thẻ thư viện theo trạng thái; dùng khi hỏi 'những thẻ đó thuộc về ai', 'thẻ bị khóa của ai', danh sách chủ thẻ. Chỉ dành cho nhân viên có quyền xem thẻ. Kết quả tên chủ thẻ KHÔNG được gửi đến mô hình AI.",
                map("status",map("type","string","enum",List.of("ACTIVE","LOCKED","EXPIRED","DISABLED"),"description","Trạng thái thẻ từ lượt trước hoặc câu hỏi hiện tại"),
                    "page",integerProperty("Trang, bắt đầu từ 0")),List.of()));
        }
        if(Set.of("LIBRARIAN","LIBRARY_MANAGER","ADMIN").contains(access.role())) {
            result.add(schema("getLibraryCalendar","Đọc lịch mở cửa hằng tuần và ngày đóng cửa thực tế của thư viện theo năm; khác với mở trang lịch.",
                map("year",map("type","integer","minimum",2000,"maximum",2100,"description","Năm cần tra cứu; bỏ trống là năm hiện tại")),List.of()));
        }
        if(Set.of("LIBRARY_MANAGER","ADMIN").contains(access.role())) {
            result.add(schema("getCardLockDebtThreshold","Đọc ngưỡng nợ phí thực tế đang cấu hình để đề xuất khóa thẻ, chỉ đọc không thay đổi.",Map.of(),List.of()));
        }
        if(Set.of("LIBRARIAN","LIBRARY_MANAGER","ADMIN").contains(access.role())) {
            result.add(schema("getStaffDashboard","Số lượng tài khoản, bạn đọc hoạt động, thẻ đã cấp và hồ sơ chờ duyệt; chỉ chỉ số tổng hợp không chứa thông tin cá nhân.",Map.of(),List.of()));
            result.add(schema("getAuthors","Tra cứu tên tác giả và số đầu sách (tối đa 20 kết quả theo từ khóa).",map("query",stringProperty("Tên tác giả cần tìm, tối đa 100 ký tự")),List.of()));
            result.add(schema("getCategories","Tra cứu thể loại và số đầu sách (tối đa 20 kết quả).",map("query",stringProperty("Tên thể loại cần tìm, tối đa 100 ký tự")),List.of()));
            result.add(schema("getReadyForPickup","Danh sách đơn đặt giữ đang chờ nhận tại thư viện, phân trang; không tiết lộ thông tin cá nhân cho AI.",map("page",integerProperty("Trang")),List.of()));
            result.add(schema("openOverdueFiltered","Mở màn hình phiếu quá hạn có sẵn bộ lọc trên N ngày mở cửa (0-365).",map("minimumDays",map("type","integer","description","Chỉ lấy phiếu quá hạn trên số ngày này","minimum",0,"maximum",365)),List.of("minimumDays")));
            result.add(schema("getBookCopies","Tra cứu bản sao của sách đã tìm thấy.",map("bookId",map("type","integer","description","ID sách đã tìm thấy","minimum",1),"page",integerProperty("Trang")),List.of("bookId")));
            result.add(schema("getAuthorizedLoans","Tra cứu phiếu theo mã hoặc danh sách quá hạn được cấp quyền.",map("code",stringProperty("Mã phiếu, mã thẻ hoặc barcode"),"overdueOnly",map("type","boolean"),"page",integerProperty("Trang")),List.of()));
            result.add(schema("getCardTypes","Tra cứu loại thẻ hiện có: hạn mức mượn, thời hạn mượn, số lần và ngày gia hạn theo chính sách thực tế.",Map.of(),List.of()));
            result.add(schema("getWarehouseSummary","Danh sách kho sách và số kệ/bản sao thực tế trong mỗi kho.",Map.of(),List.of()));
            result.add(schema("getShelves","Tra cứu kệ theo mã kho (warehouseId); bỏ trống để xem danh sách kệ.",map("warehouseId",integerProperty("ID kho đã xác minh"),"page",integerProperty("Trang")),List.of()));
            result.add(schema("getOverdueFeePolicy","Đọc mức phí quá hạn mỗi ngày hiện tại (VND), không thay đổi cấu hình.",Map.of(),List.of()));
            result.add(schema("getCompensationFactors","Đọc hệ số bồi thường sách mất và hỏng từ cấu hình thư viện; chỉ đọc.",Map.of(),List.of()));
            result.add(schema("getReminderSummary","Tổng hợp nhật ký nhắc hạn trả sách theo ngày và trạng thái, chỉ cung cấp thông tin nghiệp vụ tổng hợp.",map("date",stringProperty("Ngày YYYY-MM-DD, bỏ trống là ngày hiện tại"),"status",stringProperty("Trạng thái gửi, có thể bỏ trống")),List.of()));
            result.add(schema("searchStaffAccounts","Tra cứu tài khoản được phép xem theo tên và trạng thái, không trả email hoặc số điện thoại cho mô hình AI.",map("query",stringProperty("Tên tài khoản"),"status",stringProperty("ACTIVE, LOCKED, DISABLED..."),"page",integerProperty("Trang")),List.of()));
            result.add(schema("searchAuthorizedReaders","Tra cứu hồ sơ bạn đọc theo tên hoặc mã, chỉ cho nhân viên có quyền; không gửi PII đến AI.",map("query",stringProperty("Tên hoặc mã bạn đọc"),"page",integerProperty("Trang")),List.of()));
            result.add(schema("getAuthorizedAudit","Xem tối đa 10 sự kiện gần đây hôm nay, chỉ thời điểm và tên hành động; không nội dung nhạy cảm.",Map.of(),List.of()));
        }
        if(Set.of("LIBRARY_MANAGER","ADMIN").contains(access.role())) {
            result.add(schema("openReportFiltered","Điều hướng đến báo cáo đã chọn khoảng ngày, áp dụng bộ lọc trước khi mở.",
                map("startDate",stringProperty("YYYY-MM-DD"),"endDate",stringProperty("YYYY-MM-DD")),List.of("startDate","endDate")));
            result.add(schema("getAuthorizedStatistics","Báo cáo mượn trả; bỏ ngày để lấy tháng hiện tại. Khoảng tối đa 93 ngày. Tổng đầu sách/bản sao là hiện tại.",map("startDate",stringProperty("YYYY-MM-DD"),"endDate",stringProperty("YYYY-MM-DD")),List.of()));
            result.add(schema("getOverdueDebtSummary","Số bạn đọc có sách quá hạn/nợ phí và tổng dư nợ theo quyền quản lý, không tiết lộ hồ sơ cá nhân.",Map.of(),List.of()));
        }
        if(Set.of("LIBRARY_MANAGER","ADMIN").contains(access.role())) {
            result.add(schema("getPendingFeeReductions","Thống kê đề nghị miễn giảm phí đang chờ quản lý phê duyệt (chỉ đọc).",map("page",integerProperty("Trang")),List.of()));
        }
        return result;
    }
    /** Keep provider payload small to conserve Groq free-tier tokens and reduce 429s.
     * This is only a tool offer, NOT an authorization decision; execute() still enforces role.
     * navigate remains available for every route in the trusted role-scoped catalogue.
     */
    public List<Map<String,Object>> definitionsFor(String question,String pagePath) {
        String q=(question==null?"":question).toLowerCase(Locale.forLanguageTag("vi-VN"));
        String path=pagePath==null?"":pagePath;
        Set<String> enabled=new HashSet<>(Set.of("navigate","openCatalogFiltered","getAvailableFeatures"));
        if(q.matches("(?s).*(sách|tác giả|thể loại|java|catalog|biên mục).*" ) || path.startsWith("/catalog"))
            enabled.addAll(Set.of("searchBooks","getBookAvailability","getLibraryInventory","getBookCopies","getAuthors","getCategories"));
        if(q.matches("(?s).*(bao nhiêu|số lượng|tổng quan|tài khoản|hồ sơ).*" ) || path.equals("/dashboard"))
            enabled.addAll(Set.of("getLibraryInventory","getStaffDashboard"));
        if(q.matches("(?s).*(mượn|trả|phiếu|quá hạn|gia hạn).*" ) || path.startsWith("/loans"))
            enabled.addAll(Set.of("getMyLoans","getMyOverdueLoans","getMyLoanHistory","getAuthorizedLoans","openOverdueFiltered","getAuthorizedStatistics"));
        if(q.matches("(?s).*(đơn|đặt giữ|giữ sách|chờ nhận|hàng chờ).*" ) || path.contains("reservations"))
            enabled.addAll(Set.of("getMyReservations","getReadyForPickup"));
        if(q.matches("(?s).*(hồ sơ của tôi|thông tin của tôi|tài khoản của tôi).*" ) || path.contains("/me/")) enabled.add("getMyProfileOverview");
        if(q.matches("(?s).*(nhắc hạn|lịch sử gửi (mail|email)|nhắc trả|email nhắc).*" ) || path.contains("reminder")) enabled.add("getReminderSummary");
        if(q.matches("(?s).*(bồi thường|mất sách|hỏng sách|hệ số bồi thường).*" ) || path.contains("incident")) enabled.add("getCompensationFactors");
        if(q.matches("(?s).*(nợ phí|nợ quá hạn|nợ thư viện|báo cáo nợ).*" ) || path.contains("overdue-debts")) enabled.add("getOverdueDebtSummary");
        if(q.matches("(?s).*(miễn giảm|giảm phí|chờ phê duyệt).*" ) || path.contains("reduction")) enabled.add("getPendingFeeReductions");
        if(q.matches("(?s).*(thẻ|hạn thẻ|card|bị khóa|hết hạn|chủ thẻ).*" ) || path.contains("library-card")) {
            enabled.addAll(Set.of("getMyLibraryCard","getLibraryCardStatusSummary","searchAuthorizedLibraryCards","getCardTypes"));
        }
        if(q.matches("(?s).*(báo cáo|thống kê|biểu đồ|số liệu|phân tích).*" ) || path.startsWith("/reports"))
            enabled.addAll(Set.of("getAuthorizedStatistics","openReportFiltered"));
        if(q.matches("(?s).*(lịch (đóng|mở) cửa|ngày đóng cửa|ngày nghỉ|giờ mở cửa|giờ đóng cửa).*" ) || path.equals("/library-calendar"))
            enabled.add("getLibraryCalendar");
        if(q.matches("(?s).*(ngưỡng nợ|ngưỡng.*khóa thẻ|mức nợ.*khóa thẻ).*" ) || path.equals("/card-lock-suggestion-settings"))
            enabled.add("getCardLockDebtThreshold");
        if(q.matches("(?s).*(loại thẻ|hạn mức mượn|số sách tối đa|chính sách mượn).*" ) || path.contains("card-types") || path.contains("borrow-policy")) enabled.add("getCardTypes");
        if(q.matches("(?s).*(kho|kệ|vị trí sách).*" ) || path.contains("warehouse")) enabled.addAll(Set.of("getWarehouseSummary","getShelves"));
        if(q.matches("(?s).*(phí quá hạn|phí trễ hạn|mức phí).*" ) || path.contains("fee")) enabled.add("getOverdueFeePolicy");
        if(q.matches("(?s).*(tài khoản|nhân viên|trạng thái tài khoản).*" ) || path.equals("/users")) enabled.add("searchStaffAccounts");
        if(q.matches("(?s).*(hồ sơ bạn đọc|danh sách bạn đọc|tìm bạn đọc).*" ) || path.equals("/readers")) enabled.add("searchAuthorizedReaders");
        if(q.matches("(?s).*(nhật ký|hoạt động hôm nay|audit).*" ) || path.equals("/audit-log"))
            enabled.add("getAuthorizedAudit");
        // Restrict card-related tool offers so short follow-ups don't accidentally
        // invoke getStaffDashboard and repeat the generic issued-card total.
        if(enabled.contains("getLibraryCardStatusSummary")
            && q.matches("(?s).*(thẻ|card).*")) {
            if(q.matches("(?s).*(thuộc về|chủ thẻ|là ai|danh sách (thẻ|bạn đọc)|liệt kê (thẻ|bạn đọc)).*"))
                enabled.retainAll(Set.of("searchAuthorizedLibraryCards","navigate"));
            else if(q.matches("(?s).*(bao nhiêu|bị khóa|hết hạn|trạng thái|cần chú ý|đang hoạt động).*"))
                enabled.retainAll(Set.of("getLibraryCardStatusSummary","navigate"));
        }
        return definitions().stream().filter(d->enabled.contains(((Map<?,?>)d.get("function")).get("name"))).toList();
    }

    /** Every branch enforces roles again before invoking domain services. */
    @Transactional(readOnly=true, timeout=8)
    public ToolResult execute(String name,JsonNode args) {
        var definition=definitions().stream().filter(d->((Map<?,?>)d.get("function")).get("name").equals(name)).findFirst()
            .orElseThrow(()->new org.springframework.security.access.AccessDeniedException("Công cụ không được cấp quyền."));
        var function=(Map<?,?>)definition.get("function");
        var params=(Map<?,?>)function.get("parameters");
        var allowed=((Map<?,?>)params.get("properties")).keySet();
        if(!args.isObject()) throw invalid();
        for(String key:args.propertyNames()) if(!allowed.contains(key)) throw invalid();
        int page=integer(args,"page",0);
        return switch(name) {
            case "getAvailableFeatures" -> {
                var entries=navigation.allowed().stream().map(d->map("title",d.label(),"status",d.help(),"href",d.href())).toList();
                yield result(new Block("records","Chức năng được cấp quyền",entries,map("total",entries.size()),List.of()),false);
            }
            case "getLibraryInventory" -> result(new Block("overview","Quy mô kho sách",List.of(
                map("title","Đầu sách hiện có","value",bookRepository.count()),
                map("title","Bản sao hiện có","value",copyRepository.count())),Map.of(),List.of(new Action("Xem danh mục sách","/catalog"))),false);
            case "getLibraryCardStatusSummary" -> {
                access.staffId();
                var all=cards.getIssuedCards();
                var counts=new LinkedHashMap<String,Long>();
                for(String status:List.of("ACTIVE","LOCKED","EXPIRED","DISABLED")) counts.put(status,0L);
                for(var c:all) {
                    String status=c.status()==null?"UNKNOWN":c.status().toUpperCase(Locale.ROOT);
                    counts.merge(status,1L,Long::sum);
                }
                var rows=counts.entrySet().stream().map(e->map("title",e.getKey(),"status",e.getKey(),"value",e.getValue())).toList();
                yield result(new Block("cardStatus","Trạng thái thẻ thư viện",rows,
                    map("total",all.size()),List.of(new Action("Mở quản lý thẻ thư viện","/library-cards"))),true);
            }
            case "searchAuthorizedLibraryCards" -> {
                // Same staff roles as GET /api/v1/library-cards. Never honor a model-provided actor/user ID.
                access.staffId();
                String status=text(args,"status",16).toUpperCase(Locale.ROOT);
                if(!status.isBlank() && !Set.of("ACTIVE","LOCKED","EXPIRED","DISABLED").contains(status)) throw invalid();
                var matching=cards.getIssuedCards().stream()
                    .filter(c->status.isBlank() || status.equalsIgnoreCase(c.status()))
                    .toList();
                var rows=matching.stream().skip(page*10L).limit(10)
                    .map(c->map("id",c.id(),"title",c.readerName(),"cardNumber",c.cardNumber(),
                        "memberCode",c.memberCode(),"status",c.status(),"href","/library-cards"))
                    .toList();
                yield result(new Block("cardRecords",status.isBlank()?"Danh sách thẻ thư viện":"Thẻ thư viện "+status,
                    rows,map("total",matching.size(),"page",page,"size",10,"status",status),
                    List.of(new Action("Mở quản lý thẻ thư viện","/library-cards"))),true);
            }
            case "getStaffDashboard" -> {
                access.staffId();
                if(dashboard==null) throw invalid();
                var d=dashboard.getStats();
                yield result(new Block("overview","Tổng quan thư viện",List.of(
                    map("title","Tài khoản","value",d.totalAccounts()),
                    map("title","Bạn đọc hoạt động","value",d.activeReaders()),
                    map("title","Thẻ thư viện đã cấp","value",d.issuedLibraryCards()),
                    map("title","Hồ sơ chờ duyệt","value",d.pendingReaderRequests())),Map.of(),List.of(new Action("Mở tổng quan","/dashboard"))),true);
            }
            case "getAuthors" -> {
                access.staffId();
                if(authors==null) throw invalid();
                String query=text(args,"query",100).toLowerCase(Locale.ROOT);
                var found=authors.getAllAuthors().stream().filter(x->query.isBlank() || x.name().toLowerCase(Locale.ROOT).contains(query)).toList();
                var rows=found.stream().limit(20).map(x->map("title",x.name(),"value",x.bookCount(),"status",x.active()?"Hoạt động":"Ngừng dùng")).toList();
                yield result(new Block("records","Tác giả",rows,map("total",found.size()),List.of(new Action("Quản lý tác giả","/authors"))),false);
            }
            case "getCategories" -> {
                access.staffId();
                if(categories==null) throw invalid();
                String query=text(args,"query",100).toLowerCase(Locale.ROOT);
                var found=categories.getAllCategories().stream().filter(x->query.isBlank() || x.name().toLowerCase(Locale.ROOT).contains(query)).toList();
                var rows=found.stream().limit(20).map(x->map("title",x.name(),"value",x.bookCount(),"status",x.active()?"Hoạt động":"Ngừng dùng")).toList();
                yield result(new Block("records","Thể loại",rows,map("total",found.size()),List.of(new Action("Quản lý thể loại","/categories"))),false);
            }
            case "getLibraryCalendar" -> {
                access.staffId();
                if(libraryConfiguration==null) throw invalid();
                int year=args.path("year").isIntegralNumber()?args.path("year").asInt():LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).getYear();
                if(year<2000 || year>2100) throw invalid();
                var weekly=libraryConfiguration.getWeeklySchedule();
                var closed=libraryConfiguration.getClosedDates(year);
                var rows=new ArrayList<Map<String,Object>>();
                for(var w:weekly) rows.add(map("title",w.dayLabel(),"kind","weekly","open",w.open(),
                    "openTime",w.openTime(),"closeTime",w.closeTime(),"text",w.dayLabel()+": "+
                    (w.open()?"Mở "+w.openTime()+"–"+w.closeTime():"Đóng cửa")));
                LocalDate now=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
                closed.stream().filter(d->year<now.getYear() || !d.closedDate().isBefore(now))
                    .sorted(Comparator.comparing(d->d.closedDate()))
                    .limit(12).forEach(d->rows.add(map("title",d.closedDate().toString(),"kind","special",
                        "closedDate",d.closedDate(),"reason",d.reason(),"text",d.closedDate()+
                            (d.reason()==null || d.reason().isBlank()?": đóng cửa":": "+d.reason()))));
                yield result(new Block("libraryCalendar","Lịch thư viện năm "+year,rows,
                    map("year",year,"totalClosedDates",closed.size(),"upcomingClosedDates",
                        closed.stream().filter(d->!d.closedDate().isBefore(now)).count(),"weeklyCount",weekly.size()),
                    List.of(new Action("Xem lịch thư viện","/library-calendar"))),true);
            }
            case "getCardLockDebtThreshold" -> {
                access.require("LIBRARY_MANAGER","ADMIN");
                if(cardLockSuggestions==null) throw invalid();
                var cfg=cardLockSuggestions.settings();
                yield result(new Block("settings","Ngưỡng nợ phí đề xuất khóa thẻ",
                    List.of(map("title","Ngưỡng nợ phí","value",cfg.debtThresholdVnd(),
                        "text","Ngưỡng nợ phí được cấu hình: "+cfg.debtThresholdVnd()+" VND")),
                    map("thresholdVnd",cfg.debtThresholdVnd(),"updatedAt",cfg.updatedAt()),
                    List.of(new Action("Mở cấu hình ngưỡng nợ phí","/card-lock-suggestion-settings"))),true);
            }
            case "getReadyForPickup" -> {
                access.staffId();
                var found=reservations.getReadyForPickup();
                var rows=found.stream().skip(page*10L).limit(10).map(x->map(
                    "id",x.id(),"title",x.bookTitle(),"status",x.status(),"barcode",x.barcode(),
                    "pickupDeadline",x.pickupDeadline(),"href","/reservations/ready-for-pickup/"+x.id())).toList();
                yield result(new Block("reservations","Sách đang chờ nhận",rows,map("total",found.size(),"page",page,"size",10),
                    List.of(new Action("Mở danh sách chờ nhận","/reservations/ready-for-pickup"))),true);
            }
            case "searchBooks" -> {
                String query=text(args,"query",100);
                Integer year=publicationYear(args,query);
                query=withoutPublicationYear(query,year);
                boolean available=bool(args,"availableOnly");
                String sort=year!=null ? "publicationYear" : query.isBlank()?"createdAt":"relevance";
                var data=catalog.searchPublicBooks(query,null,year,available,page,6,sort);
                var nav=navigation.catalogFiltered(query,available,year);
                var block=new Block("books",year==null?"Sách trong thư viện":"Sách xuất bản năm "+year,
                    data.content().stream().map(this::book).toList(),map("total",data.totalElements(),"page",data.page(),"size",data.size(),"publicationYear",year==null?"":year),nav.actions());
                yield result(block,false);
            }
            case "getBookAvailability" -> result(new Block("books","Tình trạng sách",List.of(book(catalog.getPublicBookById(id(args)))),Map.of(),List.of()),false);
            case "navigate" -> result(navigation.open(text(args,"destination",40)),false);
            case "openCatalogFiltered" -> {
                String keyword=text(args,"keyword",100);
                Integer year=publicationYear(args,keyword);
                yield result(navigation.catalogFiltered(withoutPublicationYear(keyword,year),bool(args,"availableOnly"),year),false);
            }
            case "openReportFiltered" -> {
                if(args.path("startDate").isMissingNode() || args.path("endDate").isMissingNode()) throw invalid();
                yield result(navigation.reportFiltered(date(args,"startDate",LocalDate.now()),date(args,"endDate",LocalDate.now())),false);
            }
            case "openOverdueFiltered" -> {
                var days=args.path("minimumDays");
                if(!days.isIntegralNumber() || days.asInt()<0 || days.asInt()>365) throw invalid();
                yield result(navigation.overdueFiltered(days.asInt()),false);
            }
            case "getMyLoans", "getMyOverdueLoans" -> {
                Long user=access.readerId();
                var all=loans.myBorrowedBooks(user).stream().filter(x->!name.equals("getMyOverdueLoans") || x.remainingDays()!=null && x.remainingDays()<0).toList();
                var slice=all.stream().skip(page*10L).limit(10).toList();
                var ids=readRepository.ownedLoanIds(user,slice.stream().map(x->x.id()).toList());
                var rows=slice.stream().map(x->map("id",x.id(),"title",x.bookTitle(),"borrowedAt",x.borrowedAt(),"dueAt",x.dueAt(),"overdue",x.remainingDays()!=null && x.remainingDays()<0,"status",x.remainingDays()!=null && x.remainingDays()<0?"Quá hạn":"Đang mượn","href",ids.containsKey(x.id())?"/loans/"+ids.get(x.id()):"/my-borrowed-books")).toList();
                yield result(new Block("loans",name.equals("getMyOverdueLoans")?"Sách của bạn đã quá hạn":"Sách bạn đang mượn",rows,map("total",all.size(),"page",page,"size",10),List.of(new Action("Sách đang mượn","/my-borrowed-books"))),true);
            }
            case "getMyLoanHistory" -> {
                Long user=access.readerId();
                var data=loans.myReturnedBooks(user,page);
                var ids=readRepository.ownedLoanIds(user,data.items().stream().map(x->x.id()).toList());
                var rows=data.items().stream().map(x->map("id",x.id(),"title",x.bookTitle(),"loanNumber",x.loanNumber(),"borrowedAt",x.borrowedAt(),"returnedAt",x.returnedAt(),"status","Đã trả","href",ids.containsKey(x.id())?"/loans/"+ids.get(x.id()):"/my-borrowed-books")).toList();
                yield result(new Block("loans","Lịch sử sách đã trả",rows,map("total",data.total(),"page",page,"size",data.size()),List.of(new Action("Mở lịch sử mượn trả","/my-borrowed-books"))),true);
            }
            case "getMyLibraryCard" -> {
                var card=cards.getMyCard(access.readerId());
                var row=map("title","Thẻ thư viện của bạn","status",card.cardStatus(),"registrationStatus",card.registrationStatus(),"cardNumber",card.cardNumber(),"cardTypeName",card.cardTypeName(),"issuedAt",card.issuedAt(),"expiresAt",card.expiresAt(),"href","/my-library-card");
                yield result(new Block("libraryCard","Thẻ thư viện",List.of(row),Map.of(),List.of()),true);
            }
            case "getMyProfileOverview" -> {
                Long ownerId=access.readerId();
                if(readerSelf==null) throw invalid();
                var profile=readerSelf.getProfile(ownerId);
                var rows=List.of(map("title","Hồ sơ của tôi","status",profile.registrationStatus(),
                    "memberCode",profile.memberCode(),"cardTypeName",profile.cardTypeName(),
                    "cardStatus",profile.cardStatus(),"expiresAt",profile.expiresAt(),"href","/my-library-card"));
                yield result(new Block("records","Hồ sơ cá nhân",rows,map("total",1),
                    List.of(new Action("Xem thẻ và hồ sơ của tôi","/my-library-card"))),true);
            }
            case "getMyReservations" -> {
                var all=reservations.getMyReservations(access.readerId());
                var rows=all.stream().skip(page*10L).limit(10).map(x->map("id",x.id(),"title",x.bookTitle(),"status",x.status(),"reservedAt",x.reservedAt(),"pickupDeadline",x.pickupDeadline(),"queuePosition",x.queuePosition(),"href","/my-reservations")).toList();
                yield result(new Block("reservations","Đơn đặt giữ của bạn",rows,map("total",all.size(),"page",page,"size",10),List.of(new Action("Mở đơn đặt giữ","/my-reservations"))),true);
            }
            case "getBookCopies" -> {
                access.staffId(); var all=copies.getByBookId(id(args));
                var rows=all.stream().skip(page*10L).limit(10).map(x->map("id",x.id(),"title",x.bookTitle(),"barcode",x.barcode(),"status",x.statusLabel(),"location",x.warehouseName()+" / "+x.shelfName(),"href","/book-copies/"+x.id())).toList();
                yield result(new Block("copies","Bản sao sách",rows,map("total",all.size(),"page",page,"size",10),List.of()),true);
            }
            case "getAuthorizedLoans" -> {
                Long actor=access.staffId();
                if(bool(args,"overdueOnly")) {
                    var all=loans.overdueLoans(actor);
                    var rows=all.stream().skip(page*10L).limit(10).map(x->map("id",x.itemId(),"title",x.bookTitle(),"loanNumber",x.loanNumber(),"dueAt",x.dueAt(),"overdueDays",x.overdueDays(),"overdue",true,"status","Quá hạn","href","/loans/"+x.loanId())).toList();
                    yield result(new Block("loans","Sách quá hạn — ngày mở cửa",rows,map("total",all.size(),"page",page,"size",10),List.of(new Action("Xem danh sách quá hạn","/loans/overdue"))),true);
                }
                var data=loans.searchLoans(text(args,"code",100),page,actor);
                var rows=data.items().stream().map(x->map("id",x.id(),"title",x.loanNumber(),"borrowedAt",x.borrowedAt(),"status",x.status(),"books",x.items().stream().map(i->map("title",i.bookTitle(),"dueAt",i.dueAt(),"status",i.status())).toList(),"href","/loans/"+x.id())).toList();
                yield result(new Block("loans","Phiếu mượn được cấp quyền",rows,map("total",data.total(),"page",page,"size",data.size()),List.of(new Action("Tra cứu phiếu mượn","/loans/search"))),true);
            }
            case "getAuthorizedStatistics" -> {
                access.require("LIBRARY_MANAGER","ADMIN");
                LocalDate today=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
                LocalDate start=date(args,"startDate",today.withDayOfMonth(1)),end=date(args,"endDate",today);
                if(start.isAfter(end) || ChronoUnit.DAYS.between(start,end)>92 || end.isAfter(today)) throw invalid();
                var d=reports.report(start,end);
                var metrics=List.of(map("label","Đầu sách hiện tại","value",bookRepository.count()),map("label","Bản sao hiện tại","value",copyRepository.count()),map("label","Lượt mượn bản sao","value",d.totalBorrowed()),map("label","Lượt trả bản sao","value",d.totalReturned()),map("label","Phiếu còn mở tại cuối kỳ","value",d.openLoans()),map("label","Phiếu quá hạn tại cuối kỳ","value",d.overdueLoans()));
                var daily=d.dailyLoans().stream().map(x->map("date",x.date(),"count",x.borrowCount())).toList();
                yield result(new Block("statistics","Tình hình mượn trả",metrics,map("startDate",start,"endDate",end,"overdueRate",d.overdueRate(),"daily",daily),List.of(new Action("Mở báo cáo chi tiết","/reports/loans"))),true);
            }
            case "getCardTypes" -> {
                access.staffId(); if(cardTypes==null) throw invalid();
                var all=cardTypes.getAllCardTypes();
                var rows=all.stream().limit(20).map(x->map("title",x.name(),"status",(x.active()?"Đang áp dụng":"Ngừng áp dụng")
                    +"; tối đa "+x.maxBooks()+" sách; mượn "+x.loanDays()+" ngày; gia hạn "+x.maxRenewals()+" lần, mỗi lần "+x.renewalDays()+" ngày; đang sử dụng: "+x.usageCount()+" thẻ")).toList();
                yield result(new Block("records","Loại thẻ và chính sách mượn",rows,map("total",all.size()),List.of(new Action("Mở chính sách mượn","/borrow-policy"))),false);
            }
            case "getWarehouseSummary" -> {
                access.staffId(); if(libraryConfiguration==null) throw invalid();
                var all=libraryConfiguration.getWarehouses();
                var rows=all.stream().limit(20).map(x->map("title",x.name(),"status",x.code()+" — "+x.shelfCount()+" kệ, "+x.copyCount()+" bản sao",
                    "id",x.id())).toList();
                yield result(new Block("records","Kho và bản sao",rows,map("total",all.size()),List.of(new Action("Mở kho và kệ","/warehouse-shelves"))),false);
            }
            case "getShelves" -> {
                access.staffId(); if(libraryConfiguration==null) throw invalid();
                var raw=args.path("warehouseId");
                if(!raw.isMissingNode() && (!raw.isIntegralNumber() || raw.asLong()<1)) throw invalid();
                var all=libraryConfiguration.getShelves(raw.isMissingNode()?null:raw.asLong());
                var rows=all.stream().skip(page*10L).limit(10).map(x->map("title",x.name(),"status",x.warehouseName()+" / "+x.code()+" — "+x.copyCount()+" bản sao")).toList();
                yield result(new Block("records","Kệ sách",rows,map("total",all.size(),"page",page),List.of(new Action("Mở kho và kệ","/warehouse-shelves"))),false);
            }
            case "getOverdueFeePolicy" -> {
                access.staffId(); if(libraryConfiguration==null) throw invalid();
                var cfg=libraryConfiguration.getOverdueFeeSetting();
                var rows=new java.util.ArrayList<java.util.Map<String,Object>>();
                rows.add(map("title","Chính sách cũ cho sách chưa có snapshot %",
                    "status",cfg.dailyRateVnd()==null?"Chưa cấu hình":cfg.dailyRateVnd()+" đồng/ngày"));
                for(var policy:libraryConfiguration.percentagePolicies(0).history()) {
                    rows.add(map("title","Phiên bản chính sách % #"+policy.id(),
                        "status",policy.dailyPercent()+"% giá bìa/ngày; "
                            +(policy.capPercent()==null?"không giới hạn": "trần "+policy.capPercent()+"% giá bìa")
                            +"; hiệu lực "+policy.effectiveAt()+". Chỉ áp dụng cho bàn giao mới; từng cuốn giữ snapshot lúc bàn giao."));
                }
                yield result(new Block("records","Phí quá hạn — các phiên bản gần nhất",rows,map("total",rows.size()),List.of()),false);
            }
            case "getCompensationFactors" -> {
                access.staffId(); if(bookIncidents==null) throw invalid();
                var settings=bookIncidents.factors();
                // Staff can view factors, but only management can open the edit-settings screen.
                var actions=Set.of("LIBRARY_MANAGER","ADMIN").contains(access.role())
                    ? List.of(new Action("Mở cấu hình bồi thường","/compensation-settings"))
                    : List.<Action>of();
                yield result(new Block("records","Hệ số bồi thường sách",
                    List.of(map("title","Sách mất","value",settings.lost()),
                            map("title","Sách hỏng","value",settings.damaged())),
                    map("total",2,"updatedAt",settings.updatedAt()),actions),false);
            }
            case "getReminderSummary" -> {
                access.staffId(); if(loanReminders==null) throw invalid();
                LocalDate requested=date(args,"date",LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")));
                if(requested.isAfter(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")))
                    || requested.isBefore(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusDays(365))) throw invalid();
                String status=text(args,"status",32);
                long total=loanReminders.countLogs(requested,null,status.isBlank()?null:status,null,null);
                // Avoid reading email content, recipient address or message bodies into the tool context.
                yield result(new Block("overview","Nhật ký nhắc hạn ngày "+requested,
                    List.of(map("title",status.isBlank()?"Lượt ghi nhận": "Lượt trạng thái "+status,"value",total)),
                    map("date",requested,"total",total),
                    List.of(new Action("Mở nhật ký nhắc hạn","/loans/reminders"))),true);
            }
            case "getOverdueDebtSummary" -> {
                access.require("LIBRARY_MANAGER","ADMIN"); if(overdueDebts==null) throw invalid();
                var snapshot=overdueDebts.preview(access.staffId());
                java.math.BigDecimal debt=snapshot.readers().stream()
                    .map(x->x.outstandingVnd()==null?java.math.BigDecimal.ZERO:x.outstandingVnd())
                    .reduce(java.math.BigDecimal.ZERO,java.math.BigDecimal::add);
                yield result(new Block("overview","Tổng hợp quá hạn và nợ phí",
                    List.of(map("title","Bạn đọc thuộc diện báo cáo","value",snapshot.totalReaders()),
                            map("title","Dư nợ được ghi nhận (VND)","value",debt)),
                    map("snapshotAt",snapshot.snapshotAt()),
                    List.of(new Action("Mở báo cáo quá hạn và nợ phí","/reports/overdue-debts"))),true);
            }
            case "getPendingFeeReductions" -> {
                access.require("LIBRARY_MANAGER","ADMIN"); if(feeReductions==null) throw invalid();
                var data=feeReductions.pending(page,access.staffId());
                var rows=data.items().stream().map(x->map("id",x.id(),"title",x.bookTitle(),
                    "status",x.status(),"requestedAmountVnd",x.requestedAmountVnd(),
                    "submittedAt",x.submittedAt())).toList();
                yield result(new Block("records","Đề nghị miễn giảm phí chờ duyệt",
                    rows,map("total",data.totalItems(),"page",data.page(),"size",data.size()),
                    List.of(new Action("Xem đề nghị miễn giảm phí","/overdue-fees/reduction-requests/pending"))),true);
            }
            case "searchStaffAccounts" -> {
                access.staffId(); if(userManagement==null) throw invalid();
                String query=text(args,"query",100), status=text(args,"status",24);
                var all=userManagement.getAccounts(query.isBlank()?null:query,null,status.isBlank()?null:status);
                var rows=all.stream().skip(page*10L).limit(10).map(x->map("title",x.fullName(),"status",x.role()+" — "+x.status(),"href","/users")).toList();
                yield result(new Block("records","Tài khoản được cấp quyền",rows,map("total",all.size(),"page",page),List.of(new Action("Quản lý tài khoản","/users"))),true);
            }
            case "searchAuthorizedReaders" -> {
                access.staffId(); if(readerRegistration==null) throw invalid();
                String query=text(args,"query",100).toLowerCase(Locale.forLanguageTag("vi-VN"));
                var all=readerRegistration.getAllReaders().stream().filter(x->query.isBlank()
                    || x.fullName().toLowerCase(Locale.forLanguageTag("vi-VN")).contains(query)
                    || (x.memberCode()!=null && x.memberCode().toLowerCase(Locale.ROOT).contains(query))).toList();
                var rows=all.stream().skip(page*10L).limit(10).map(x->map("title",x.fullName(),"status",x.registrationStatus(),"memberCode",x.memberCode(),"href","/readers")).toList();
                yield result(new Block("records","Bạn đọc được cấp quyền",rows,map("total",all.size(),"page",page),List.of(new Action("Quản lý bạn đọc","/readers"))),true);
            }
            case "getAuthorizedAudit" -> {
                access.staffId(); LocalDate today=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
                var all=audit.search(today,today,null,null,null);
                var rows=all.stream().sorted(Comparator.comparing(com.duanttcsn5.library.dto.audit.AuditLogResponse::timestamp).reversed()).limit(10).map(x->map("id",x.id(),"title",x.actionLabel(),"timestamp",x.timestamp(),"href","/audit-log")).toList();
                yield result(new Block("audit","Hoạt động hôm nay",rows,map("total",all.size()),List.of(new Action("Mở nhật ký hoạt động","/audit-log"))),true);
            }
            default -> throw invalid();
        };
    }
    /** Server-side year filter: never convert a year into a title keyword. */
    static Integer publicationYear(JsonNode args,String query) {
        var raw=args.path("publicationYear");
        if(!raw.isMissingNode() && !raw.isNull()) {
            if(!raw.isIntegralNumber()) throw invalid();
            int year=raw.asInt();
            if(year<1 || year>Year.now(ZoneId.of("Asia/Ho_Chi_Minh")).getValue()) throw invalid();
            return year;
        }
        var matcher=java.util.regex.Pattern.compile("(?iu)(?:xuất\\s*bản\\s*(?:năm\\s*)?|năm\\s*)(\\d{4})").matcher(query);
        if(matcher.find()) {
            int year=Integer.parseInt(matcher.group(1));
            if(year<1 || year>Year.now(ZoneId.of("Asia/Ho_Chi_Minh")).getValue()) throw invalid();
            return year;
        }
        return null;
    }
    static String withoutPublicationYear(String query,Integer year) {
        if(year==null) return query;
        String remaining=query.replaceAll("(?iu)(?:xuất\\s*bản\\s*(?:năm\\s*)?|năm\\s*)"+year+"\\b", " ")
            .replaceAll("(?iu)^\\s*(?:tìm|kiếm|cho tôi|giúp tôi|các|những|sách|đầu sách|cuốn sách|quyển sách|về)\\s+", "")
            .trim();
        if(remaining.toLowerCase(Locale.forLanguageTag("vi-VN")).matches(
            "(?iu)(?:tìm\\s*|kiếm\\s*|tìm\\s*kiếm\\s*)?(?:các\\s*|những\\s*)?(?:cuốn\\s*|quyển\\s*|đầu\\s*)?sách")) return "";
        return remaining;
    }
    private Map<String,Object> book(BookResponse b) {
        String description=b.description()==null?"":b.description().trim();
        if(description.length()>500)description=description.substring(0,500)+"…";
        return map("id",b.id(),"title",b.title(),"authorName",b.authorName(),"categoryName",b.categoryName(),
            "publisher",b.publisher(),"publicationYear",b.publicationYear(),"summary",description,
            "availableCount",b.availableCount(),"copyCount",b.copyCount(),"coverImageUrl",b.coverImageUrl(),
            "href","/catalog/books/"+b.id(),"canOpenReservation",access.role().equals("READER") && b.hasCopies());
    }
    private ToolResult result(Block block,boolean sensitive) {
        // Actual PII (name, email, DOB, phone, staff notes) is never part of these projections.
        // Never send a metrics count ('displayed': 6) that the model can misinterpret
        // as six loan transactions. The backend produces the numeric summary itself.
        Object summary=block.type().equals("statistics")
            ? map("type","statistics","notice","Đã hiển thị bảng thống kê trên giao diện. Không nhận được giá trị số; KHÔNG viết con số trong câu trả lời.")
            : map("title",block.title(),"displayed",block.items().size(),"total",block.meta().get("total"),"notice","Dữ liệu đã xác minh được hiển thị trên thẻ. Không suy đoán các trường không được cung cấp.");
        return new ToolResult(block,sensitive?summary:block,sensitive);
    }
    private static ApiException invalid() { return new ApiException(HttpStatus.BAD_REQUEST,"AI_TOOL_ARGUMENTS","Tham số công cụ không hợp lệ. Hãy diễn đạt lại yêu cầu."); }
    private String text(JsonNode a,String key,int max) { var v=a.path(key); if(v.isMissingNode())return ""; if(!v.isTextual() || v.asText().length()>max)throw invalid(); return v.asText().trim(); }
    private int integer(JsonNode a,String key,int fallback) { var v=a.path(key); if(v.isMissingNode())return fallback; if(!v.isIntegralNumber() || !v.canConvertToInt() || v.asInt()<0 || v.asInt()>10000)throw invalid();return v.asInt(); }
    private long id(JsonNode a) { var v=a.path("bookId"); if(!v.isIntegralNumber() || !v.canConvertToLong() || v.asLong()<1)throw invalid(); return v.asLong(); }
    private boolean bool(JsonNode a,String key) { var v=a.path(key); if(v.isMissingNode())return false;if(!v.isBoolean())throw invalid();return v.asBoolean(); }
    private LocalDate date(JsonNode a,String key,LocalDate fallback) { String v=text(a,key,10);if(v.isBlank())return fallback;try{return LocalDate.parse(v);}catch(java.time.DateTimeException e){throw invalid();} }
}
