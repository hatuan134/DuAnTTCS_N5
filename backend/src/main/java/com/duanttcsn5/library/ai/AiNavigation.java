package com.duanttcsn5.library.ai;

import org.springframework.stereotype.Component;
import java.util.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import static com.duanttcsn5.library.ai.AiTypes.*;

@Component
public class AiNavigation {
    record Destination(String key,String label,String href,Set<String> roles,String help) {}
    private static Set<String> roles(String... values) { return Set.of(values); }
    private static final Set<String> ALL=roles("PUBLIC","READER","LIBRARIAN","LIBRARY_MANAGER","ADMIN");
    private static final Set<String> STAFF=roles("LIBRARIAN","LIBRARY_MANAGER","ADMIN");
    private static final Set<String> MANAGER=roles("LIBRARY_MANAGER","ADMIN");
    private static final List<Destination> DESTINATIONS=List.of(
        new Destination("catalog","Tra cứu sách","/catalog",ALL,"Nhập tên sách, tác giả hoặc ISBN vào ô tìm kiếm; dùng bộ lọc thể loại và tình trạng sẵn sàng."),
        new Destination("dashboard","Tổng quan thư viện","/dashboard",STAFF,"Theo dõi số liệu và công việc cần xử lý ở trang tổng quan."),
        new Destination("authors","Quản lý tác giả","/authors",STAFF,"Tra cứu tên tác giả, trạng thái hoạt động và số đầu sách của tác giả."),
        new Destination("categories","Quản lý thể loại","/categories",STAFF,"Tra cứu thể loại, phân cấp và danh mục sách."),
        new Destination("libraryCards","Thẻ thư viện","/library-cards",STAFF,"Tìm và kiểm tra thẻ theo nghiệp vụ; không cung cấp dữ liệu cá nhân trái quyền."),
        new Destination("borrowPolicy","Chính sách mượn","/borrow-policy",STAFF,"Xem hạn mức và quy định mượn trong cấu hình hiện hành; cần kiểm tra tại trang chính sách trước khi kết luận."),
        new Destination("readers","Quản lý bạn đọc","/readers",STAFF,"Tra cứu hồ sơ bạn đọc; dữ liệu cá nhân chỉ xem theo quyền nghiệp vụ."),
        new Destination("warehouses","Kho và kệ","/warehouse-shelves",MANAGER,"Tra cứu và quản lý kho, kệ, vị trí sách."),
        new Destination("readyPickup","Sách đang chờ nhận","/reservations/ready-for-pickup",STAFF,"Xem các đơn đặt giữ đã sẵn sàng chờ bạn đọc nhận."),
        new Destination("loanList","Danh sách phiếu mượn","/loans",STAFF,"Xem danh sách phiếu mượn theo quyền được cấp."),
        new Destination("rejections","Nhật ký từ chối mượn","/loans/rejections",STAFF,"Xem nhật ký lý do từ chối ở màn hình nghiệp vụ."),
        new Destination("autoCancellations","Đơn hủy tự động","/manager/auto-cancellations",MANAGER,"Xem các lần tự động hủy đặt giữ và lịch sử liên quan."),
        new Destination("feeReduction","Đề nghị miễn giảm phí","/overdue-fees/reduction-requests/pending",MANAGER,"Xem yêu cầu miễn giảm phí đang chờ duyệt."),
        new Destination("feePayments","Thu phí và biên lai","/fee-payments",roles("LIBRARIAN","ADMIN"),"Tìm khoản nợ, thu phí và xem biên lai; AI không thực hiện giao dịch tiền."),
        new Destination("bookIncidents","Sách mất hoặc hỏng","/book-incidents",STAFF,"Xem tình trạng mất, hỏng, điều chỉnh và xử lý sự cố sách."),
        new Destination("compensation","Hệ số bồi thường","/compensation-settings",MANAGER,"Xem hệ số bồi thường; việc cập nhật phải thực hiện tại giao diện."),
        new Destination("cardLock","Đề xuất khóa thẻ","/card-lock-suggestions",MANAGER,"Xem các đề xuất khóa thẻ cần xử lý."),
        new Destination("cardLockSettings","Ngưỡng nợ phí","/card-lock-suggestion-settings",MANAGER,"Xem các ngưỡng cấu hình đề xuất khóa thẻ."),
        new Destination("reminders","Nhắc nhở đến hạn","/loans/reminders",STAFF,"Xem thông báo và tác vụ nhắc đến hạn trả sách."),
        new Destination("debtReport","Xuất quá hạn và nợ phí","/reports/overdue-debts",MANAGER,"Xem dữ liệu nợ phí và xuất báo cáo khi được phép."),
        new Destination("exportHistory","Lịch sử xuất dữ liệu","/reports/overdue-debts/history",MANAGER,"Xem lịch sử xuất báo cáo quá hạn và nợ phí."),
        new Destination("login","Đăng nhập","/login",roles("PUBLIC"),"Đăng nhập bằng email và mật khẩu đã đăng ký."),
        new Destination("register","Đăng ký bạn đọc","/register",roles("PUBLIC"),"Điền biểu mẫu đăng ký. Mã bạn đọc do hệ thống tự sinh. Sau khi hồ sơ được duyệt, kiểm tra thẻ ở Thẻ thư viện của tôi."),
        new Destination("myLoans","Sách đang mượn và lịch sử","/my-borrowed-books",roles("READER"),"Trang có danh sách sách đang mượn và lịch sử sách đã trả; xem hạn trả trước khi yêu cầu gia hạn."),
        new Destination("myCard","Thẻ thư viện của tôi","/my-library-card",roles("READER"),"Xem trạng thái hồ sơ, thẻ và hạn sử dụng."),
        new Destination("myReservations","Đặt giữ của tôi","/my-reservations",roles("READER"),"Xem trạng thái đơn, thứ tự hàng chờ và hạn đến nhận nếu có."),
        new Destination("password","Đổi mật khẩu","/change-password",roles("READER"),"Nhập mật khẩu hiện tại và mật khẩu mới tại biểu mẫu. Không gửi mật khẩu vào chat."),
        new Destination("loans","Tra cứu phiếu mượn","/loans/search",STAFF,"Lọc theo mã phiếu, mã thẻ hoặc mã bản sao, khoảng ngày và trạng thái."),
        new Destination("overdue","Phiếu quá hạn","/loans/overdue",STAFF,"Dùng Số ngày trễ tối thiểu / tối đa hoặc bộ lọc nhanh. Số ngày trễ dùng ngày mở cửa thư viện."),
        new Destination("cataloging","Biên mục và bản sao","/cataloging",STAFF,"Chọn đầu sách để xem chi tiết và bản sao."),
        new Destination("borrow","Cho mượn tại quầy","/loans/direct",roles("LIBRARIAN","ADMIN"),"Kiểm tra thẻ và bản sao, đối chiếu ngày mượn / hạn trả trước khi xác nhận."),
        new Destination("return","Nhận trả sách","/loans/receive-return",roles("LIBRARIAN","ADMIN"),"Tra cứu mã bản sao, kiểm tra tình trạng rồi xác nhận tại màn hình nghiệp vụ."),
        new Destination("statistics","Báo cáo mượn trả","/reports/loans",MANAGER,"Chọn khoảng ngày và xem số lượt mượn/trả cùng biểu đồ theo ngày."),
        new Destination("users","Quản lý tài khoản","/users",STAFF,"Quyền xem và thao tác do API quản lý tài khoản kiểm tra; AI không tạo, xóa hay đổi quyền."),
        new Destination("audit","Nhật ký hoạt động","/audit-log",STAFF,"Lọc theo ngày, người thực hiện và hành động; xem chi tiết trên màn hình được phân quyền."),
        new Destination("permissions","Rà soát phân quyền","/permission-review",roles("ADMIN"),"Xem công cụ rà soát phân quyền. AI không chạy kiểm thử ghi dữ liệu hay sửa vai trò."),
        new Destination("calendar","Lịch thư viện","/library-calendar",STAFF,"Xem lịch mở cửa và các ngày đóng cửa trong cấu hình thư viện.")
    );
    private final AiAccess access;
    public AiNavigation(AiAccess access) { this.access=access; }
    public List<Destination> allowed() { return DESTINATIONS.stream().filter(d->d.roles().contains(access.role())).toList(); }
    public List<String> keys() { return allowed().stream().map(Destination::key).toList(); }
    public Block open(String key) {
        var d=allowed().stream().filter(x->x.key().equals(key)).findFirst().orElseThrow(()->new org.springframework.security.access.AccessDeniedException("Chức năng không được cấp quyền."));
        return new Block("navigation",d.label(),List.of(Map.of("text",d.help())),Map.of(),List.of(new Action(d.label(),d.href())));
    }
    public Block catalogFiltered(String keyword,boolean availableOnly) {
        return catalogFiltered(keyword,availableOnly,null);
    }
    public Block catalogFiltered(String keyword,boolean availableOnly,Integer publicationYear) {
        if(keyword==null || keyword.length()>100 || keyword.chars().anyMatch(c->Character.isISOControl((char)c)))
            throw new IllegalArgumentException("Từ khóa không hợp lệ.");
        if(publicationYear!=null && (publicationYear<1 || publicationYear>Year.now(ZoneId.of("Asia/Ho_Chi_Minh")).getValue()))
            throw new IllegalArgumentException("Năm xuất bản không hợp lệ.");
        StringBuilder href=new StringBuilder("/catalog");
        if(!keyword.isBlank()) href.append("?keyword=").append(URLEncoder.encode(keyword.trim(),StandardCharsets.UTF_8));
        if(availableOnly) href.append(href.indexOf("?")>=0?"&":"?").append("availableOnly=true");
        if(publicationYear!=null) href.append(href.indexOf("?")>=0?"&":"?").append("publicationYear=").append(publicationYear);
        String title=availableOnly?"Tra cứu sách đang sẵn sàng":"Tra cứu sách";
        return new Block("navigation",title,List.of(Map.of("text","Đã chuẩn bị bộ lọc trên trang tra cứu. Hãy mở kết quả để xem dữ liệu mới nhất.")),
            Map.of(),List.of(new Action("Mở kết quả đã lọc",href.toString())));
    }
    public Block reportFiltered(LocalDate from,LocalDate to) {
        access.require("LIBRARY_MANAGER","ADMIN");
        LocalDate today=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        if(from.isAfter(to) || to.isAfter(today) || ChronoUnit.DAYS.between(from,to)>92)
            throw new IllegalArgumentException("Khoảng ngày báo cáo không hợp lệ.");
        String href="/reports/loans?startDate="+from+"&endDate="+to;
        return new Block("navigation","Báo cáo theo khoảng ngày",
            List.of(Map.of("text","Bộ lọc báo cáo từ "+from+" đến "+to+" đã được chuẩn bị.")),
            Map.of(),List.of(new Action("Mở báo cáo đã lọc",href)));
    }
    public Block overdueFiltered(int days) {
        access.require("LIBRARIAN","LIBRARY_MANAGER","ADMIN");
        if(days<0 || days>365) throw new IllegalArgumentException("Số ngày không hợp lệ.");
        String href="/loans/overdue?minDays="+days+"&exclusive=1";
        return new Block("navigation","Lọc phiếu quá hạn",List.of(Map.of("text","Đã chuẩn bị bộ lọc phiếu quá hạn trên "+days+" ngày mở cửa.")),
            Map.of("minimumDays",days),List.of(new Action("Mở danh sách đã lọc",href)));
    }
    public String pageContext(String path) {
        if(path==null) return "Trang LIBRA; chưa có ngữ cảnh màn hình.";
        String clean=path.split("\\?",2)[0];
        var description=allowed().stream().filter(d->d.href().equals(clean)).map(d->d.label()+": "+d.help()).findFirst();
        if(description.isPresent()) return description.get();
        if(clean.matches("/catalog/books/[1-9][0-9]*")) return "Chi tiết sách công khai: xem thông tin sách, số bản sẵn sàng và hướng dẫn đặt giữ.";
        if(access.principal()!=null && clean.matches("/loans/[1-9][0-9]*") && !access.role().equals("PUBLIC"))
            return "Chi tiết phiếu mượn đang được xem; phải kiểm tra quyền trước khi tra cứu dữ liệu phiếu.";
        return "Trang LIBRA; chưa có hướng dẫn riêng cho màn hình này.";
    }
    // List every authorized screen without sending verbose documentation for all screens
    // to Groq on each turn. Detailed guidance is returned by navigate() on demand.
    public String guide() { return allowed().stream().map(d->d.key()+" = "+d.label())
        .collect(java.util.stream.Collectors.joining("; ","\nCác chức năng được phép: ",".")); }
}
