package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.permission.PermissionReviewDtos.*;
import com.duanttcsn5.library.dto.loan.*;
import com.duanttcsn5.library.service.*;
import com.duanttcsn5.library.repository.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.*;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Explicit valid business fixtures. Never executed against the application's live database. */
class PermissionReviewApiFixtures {
    final WebApplicationContext context;final MockMvc mvc;final JdbcTemplate jdbc;final ObjectMapper mapper;
    final PermissionReviewCatalog catalog;final PermissionReviewInspector inspector;
    final Map<String,Long> users=new LinkedHashMap<>();final Map<String,String> tokens=new HashMap<>(),refresh=new HashMap<>();
    final Map<String,Object> values=new HashMap<>();List<String> tables; String encodedPassword; String fingerprint;
    int dateOffset=100; String password="S4092-Review-Only!",suffix,cardNumber,freeBarcode,borrowedBarcode;long reader,staff,manager;
    PermissionReviewApiFixtures(WebApplicationContext context,MockMvc mvc,JdbcTemplate jdbc,ObjectMapper mapper,PermissionReviewCatalog catalog,PermissionReviewInspector inspector) {
        this.context=context;this.mvc=mvc;this.jdbc=jdbc;this.mapper=mapper;this.catalog=catalog;this.inspector=inspector;
    }
    void users() throws Exception {
        String hash=context.getBean(PasswordEncoder.class).encode(password);encodedPassword=hash;
        for(String role:List.of("ADMIN","LIBRARY_MANAGER","LIBRARIAN","READER","OTHER_READER")) {
            String email="s4092-"+UUID.randomUUID()+"@example.invalid";
            long id=id("INSERT INTO users(role_id,full_name,email,password_hash,status) SELECT id,?,?,?,'ACTIVE' FROM roles WHERE code=? RETURNING id","S4092 "+role,email,hash,role.equals("OTHER_READER")?"READER":role);
            users.put(role,id);
            var login=mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(json(Map.of("email",email,"password",password)))).andReturn();
            assertEquals(200,login.getResponse().getStatus(),login.getResponse().getContentAsString());
            var response=mapper.readTree(login.getResponse().getContentAsString());tokens.put(role,response.get("accessToken").asText());refresh.put(role,response.get("refreshToken").asText());
        }
        tables=jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename NOT IN ('flyway_schema_history','audit_logs','refresh_tokens','password_reset_requests','reader_registration_attempts') ORDER BY tablename",String.class);
    }
    long id(String sql,Object... args){return Objects.requireNonNull(jdbc.queryForObject(sql,Long.class,args));}
    String json(Object value){return mapper.writeValueAsString(value);}
    long value(String key){return ((Number)values.get(key)).longValue();}
    String text(String key){return Objects.toString(values.get(key));}
    void prepare(PolicyEntry entry,String role,String scenario) throws Exception {
        values.clear();suffix=UUID.randomUUID().toString().substring(0,12);
        for(String who:List.of("ADMIN","LIBRARY_MANAGER","LIBRARIAN","READER","OTHER_READER")) {
            String code=who.equals("OTHER_READER")?"READER":who;
            String email="s4092-"+who+suffix+"@example.invalid";
            long uid=id("INSERT INTO users(role_id,full_name,email,password_hash,status) SELECT id,?,?,?,'ACTIVE' FROM roles WHERE code=? RETURNING id","S4092 "+who,email,encodedPassword,code);
            var user=new com.duanttcsn5.library.entity.User();user.setId(uid);user.setEmail(email);user.setFullName("S4092 "+who);user.setStatus("ACTIVE");user.setTokenVersion(0);
            var roleEntity=new com.duanttcsn5.library.entity.Role();roleEntity.setCode(code);user.setRole(roleEntity);
            users.put(who,uid);tokens.put(who,context.getBean(com.duanttcsn5.library.security.JwtService.class).issueAccessToken(user).value());
        }
        reader=users.get("READER");staff=users.get("LIBRARIAN");manager=users.get("LIBRARY_MANAGER");
        values.put("readerId",reader);values.put("readerUserId",reader);
        long type=id("INSERT INTO card_types(name,duration_months,max_books,loan_days,max_renewals,renewal_days) VALUES (?,12,10,14,3,7) RETURNING id","S4092 "+suffix);values.put("cardTypeId",type);
        for(String who:List.of("READER","OTHER_READER")) {
            long user=users.get(who);
            jdbc.update("INSERT INTO reader_profiles(user_id,date_of_birth,member_code,registration_status) VALUES (?,DATE '2006-01-01',?,'APPROVED')",user,"S4092-"+who+suffix);
            long card=id("INSERT INTO library_cards(user_id,card_type_id,card_number,status,issued_at,expires_at,created_by) VALUES (?,?,?,'ACTIVE',CURRENT_DATE,CURRENT_DATE+365,?) RETURNING id",user,type,"S4092-"+who+suffix,staff);
            if(who.equals("READER")){values.put("cardId",card);cardNumber="S4092-"+who+suffix;}
        }
        long warehouse=id("INSERT INTO warehouses(code,name) VALUES (?,?) RETURNING id","S4092"+suffix,"Kho "+suffix);
        long shelf=id("INSERT INTO shelves(warehouse_id,code,name) VALUES (?,?,?) RETURNING id",warehouse,"S4092","Kệ thử");values.put("warehouseId",warehouse);values.put("shelfId",shelf);
        long author=id("INSERT INTO authors(name) VALUES (?) RETURNING id","Tác giả "+suffix);
        long category=id("INSERT INTO categories(name) VALUES (?) RETURNING id","Thể loại "+suffix);values.put("authorId",author);values.put("categoryId",category);
        long book=book(author,category,"Sách tự do "+suffix);values.put("bookId",book);
        freeBarcode="S4092-FREE-"+suffix;long free=copy(book,shelf,freeBarcode);values.put("copyId",free);
        long borrowedBook=book(author,category,"Sách đang mượn "+suffix);values.put("borrowedBookId",borrowedBook);
        borrowedBarcode="S4092-LOAN-"+suffix;long borrowedCopy=copy(borrowedBook,shelf,borrowedBarcode);values.put("borrowedCopyId",borrowedCopy);
        long loan=id("INSERT INTO loans(loan_number,borrower_user_id,created_by) VALUES (?,?,?) RETURNING id","S4092-"+suffix,reader,staff);values.put("loanId",loan);
        long item=id("INSERT INTO loan_items(loan_id,book_copy_id,borrowed_at,due_date) VALUES (?,?,CURRENT_TIMESTAMP-interval '2 days',CURRENT_TIMESTAMP+interval '5 days') RETURNING id",loan,borrowedCopy);values.put("itemId",item);
        long holdBook=book(author,category,"Sách chờ nhận "+suffix);long heldCopy=copy(holdBook,shelf,"S4092-HOLD-"+suffix);
        long reservation=id("INSERT INTO book_reservations(book_id,reader_id,status,book_copy_id,reserved_at,pickup_deadline,hold_started_at) VALUES (?,?,'READY_FOR_PICKUP',?,CURRENT_TIMESTAMP-interval '1 hour',CURRENT_TIMESTAMP+interval '2 days',CURRENT_TIMESTAMP-interval '30 minutes') RETURNING id",holdBook,reader,heldCopy);
        jdbc.update("UPDATE book_copies SET status='HELD' WHERE id=?",heldCopy);values.put("reservationId",reservation);values.put("heldCopyId",heldCopy);
        values.put("id",reader);values.put("requestId",UUID.randomUUID().toString());
        if(entry.controller().equals("AuthorController"))values.put("id",author);
        if(entry.controller().equals("CategoryController"))values.put("id",category);
        if(entry.controller().equals("CardTypeController"))values.put("id",type);
        if(entry.controller().equals("BookCatalogController") || entry.controller().equals("BookCoverController"))values.put("id",book);
        if(entry.controller().equals("BookCopyController"))values.put("id",free);
        if(entry.controller().equals("LibraryConfigurationController"))values.put("id",entry.handler().contains("Warehouse")?warehouse:shelf);
        if(entry.controller().equals("UserManagementController"))values.put("id",id("INSERT INTO users(role_id,full_name,email,status) SELECT id,'Nhân viên thử',?,'ACTIVE' FROM roles WHERE code='LIBRARIAN' RETURNING id",suffix+"@example.invalid"));
        specialized(entry,role,scenario);
    }
    long book(long author,long category,String title){return id("INSERT INTO books(title,author_id,category_id,publisher,publication_year,page_count) VALUES (?,?,?,'NXB Trẻ',2024,100) RETURNING id",title,author,category);}
    long copy(long book,long shelf,String barcode){return id("INSERT INTO book_copies(book_id,barcode,shelf_id,status,received_date,cover_price,physical_condition) VALUES (?,?,?,'AVAILABLE',CURRENT_DATE-10,50000,'GOOD') RETURNING id",book,barcode,shelf);}
    void specialized(PolicyEntry e,String role,String scenario) throws Exception {
        String id=e.id();
        if(id.equals("AuthorController.deleteAuthor") || id.equals("AuthorController.toggleStatus"))values.put("id",id("INSERT INTO authors(name) VALUES (?) RETURNING id","Chưa liên kết "+suffix));
        if(id.equals("CategoryController.deleteCategory") || id.equals("CategoryController.toggleStatus"))values.put("id",id("INSERT INTO categories(name) VALUES (?) RETURNING id","Chưa liên kết "+suffix));
        if(id.equals("CardTypeController.deleteCardType") || id.equals("CardTypeController.toggleStatus"))values.put("id",id("INSERT INTO card_types(name,duration_months,max_books,loan_days,max_renewals,renewal_days) VALUES (?,12,10,14,3,7) RETURNING id","Chưa liên kết "+suffix));
        if(id.equals("LibraryConfigurationController.deleteShelf"))values.put("id",id("INSERT INTO shelves(warehouse_id,code,name) VALUES (?,?,'Kệ chưa dùng') RETURNING id",value("warehouseId"),suffix));
        if(id.contains("ClosedDate")) {
            LocalDate day=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(dateOffset+=2); values.put("closedDate",day.plusDays(1));
            values.put("id",id("INSERT INTO library_closed_dates(closed_date,reason) VALUES (?,'Ngày nghỉ thử') RETURNING id",day));
        }
        if(id.equals("LibraryCardController.approve") || id.equals("LibraryCardController.reject")) {
            long pending=id("INSERT INTO users(role_id,full_name,email,status) SELECT id,'Bạn đọc chờ duyệt',?,'PENDING' FROM roles WHERE code='READER' RETURNING id",suffix+"@example.invalid");
            jdbc.update("INSERT INTO reader_profiles(user_id,date_of_birth,member_code,registration_status) VALUES (?,DATE '2006-01-01',?,'PENDING')",pending,suffix);values.put("readerUserId",pending);
        }
        if(id.equals("BookCopyController.repair"))jdbc.update("UPDATE book_copies SET status='AVAILABLE',physical_condition='LIGHTLY_DAMAGED' WHERE id=?",value("copyId"));
        if(e.controller().equals("BookCoverController"))context.getBean(BookCoverService.class).upload(value("bookId"),image("file"));
        if(id.equals("LoanController.rejectionDetail"))values.put("id",id("INSERT INTO loan_rejections(request_id,source,reader_user_id,reader_name,card_number,actor_user_id,actor_name,borrowed_books,max_books,overdue_loans,unpaid_amount_vnd) VALUES (?,'CARD_CHECK',?,'Bạn đọc thử',?,?,'Thủ thư thử',1,10,0,0) RETURNING id",UUID.randomUUID(),reader,cardNumber,staff));
        if(e.controller().equals("OverdueContactController") || id.equals("LoanController.overdueLoans") || id.toLowerCase(Locale.ROOT).contains("suggestion"))jdbc.update("UPDATE loan_items SET due_date=CURRENT_TIMESTAMP-interval '40 days' WHERE id=?",value("itemId"));
        if(id.toLowerCase(Locale.ROOT).contains("suggestion") || id.equals("LibraryCardController.suggestionDetail")) {
            context.getBean(CardLockSuggestionService.class).refresh();
            values.put("id",jdbc.queryForObject("SELECT id FROM card_lock_suggestions WHERE card_id=? AND status='PENDING'",Long.class,value("cardId")));
        }
        if(id.equals("LibraryCardController.unlockCard")){
            var response=call(post("/api/v1/library-cards/"+value("cardId")+"/lock").contentType("application/json").content(json(Map.of("reason","Khóa thử"))),"LIBRARY_MANAGER");
            values.put("lockStartedAt",jdbc.queryForObject("SELECT manual_lock_started_at FROM library_cards WHERE id=?",OffsetDateTime.class,value("cardId")).toString());
        }
        if(e.controller().equals("FeePaymentController") || e.controller().equals("FeeReductionController"))fees(e);
        if(e.controller().equals("BookIncidentController"))incidents(e);
        if(id.equals("AuthController.refresh")) {
            String who=role.equals("ANONYMOUS")?"READER":role;
            var login=mapper.readTree(call(post("/api/v1/auth/login").contentType("application/json").content(json(Map.of("email",jdbc.queryForObject("SELECT email FROM users WHERE id=?",String.class,users.get(who)),"password",password))),who));
            refresh.put(who,login.get("refreshToken").asText());
        }
        if(e.controller().equals("InitialPasswordController") || e.controller().equals("PasswordResetController"))passwordToken(e);
        if(e.controller().equals("PermissionReviewController"))reviewReport(e);
        if(e.controller().equals("AuditLogController"))values.put("id",id("INSERT INTO audit_logs(actor_user_id,action,entity_type,entity_id) VALUES (?,'S4092_TEST','TEST','1') RETURNING id",staff));
        if(e.controller().equals("LoanReminderController") && (id.endsWith("retrySingle") || id.endsWith("retryPending")))reminder(e);
        if(id.equals("BookReservationController.reserve") || id.equals("BookReservationController.reserveMany")) {
            // Reserve a title for which this reader has no existing reservation.
            values.put("bookId",value("bookId"));
        }
        if(scenario.equals("OVERRIDE"))jdbc.update("INSERT INTO reader_fees(reader_user_id,description,amount_vnd) VALUES (?,'Khoản phí cho luồng ngoại lệ thử',5000)",reader);
        if(id.equals("BookReservationController.getLatestAutoCancelRun"))context.getBean(ReservationAutoCancellationService.class).executeAutoCancellationRun(OffsetDateTime.now(),"S4092 fixture");
        if(id.equals("ReadyPickupNotificationController.runCancellation"))jdbc.update("UPDATE book_reservations SET reserved_at=CURRENT_TIMESTAMP-interval '3 days',hold_started_at=CURRENT_TIMESTAMP-interval '2 days',pickup_deadline=CURRENT_TIMESTAMP-interval '1 hour' WHERE id=?",value("reservationId"));
        if(e.controller().equals("LoanReminderController"))jdbc.update("UPDATE loan_items SET due_date=CURRENT_TIMESTAMP WHERE id=?",value("itemId"));
        if(e.rules().containsValue("OWN")) ownershipFixtures(e,scenario);
    }
    void ownershipFixtures(PolicyEntry entry,String scenario) {
        long other=users.get("OTHER_READER");
        String marker="FOREIGN-"+suffix;values.put("foreignMarker",marker);
        long foreignBook=book(value("authorId"),value("categoryId"),marker);
        long foreignCopy=copy(foreignBook,value("shelfId"),marker);
        long foreignLoan=id("INSERT INTO loans(loan_number,borrower_user_id,created_by) VALUES (?,?,?) RETURNING id",marker,other,staff);
        long foreignItem=id("INSERT INTO loan_items(loan_id,book_copy_id,borrowed_at,due_date) VALUES (?,?,CURRENT_TIMESTAMP-interval '2 days',CURRENT_TIMESTAMP+interval '5 days') RETURNING id",foreignLoan,foreignCopy);
        long foreignReservation=id("INSERT INTO book_reservations(book_id,reader_id,status,reserved_at) VALUES (?,?,'PENDING',CURRENT_TIMESTAMP) RETURNING id",foreignBook,other);
        values.put("foreignPasswordHash",jdbc.queryForObject("SELECT password_hash FROM users WHERE id=?",String.class,other));
        if(entry.id().equals("LoanController.myReturnedBooks")) {
            jdbc.update("UPDATE loan_items SET returned_at=CURRENT_TIMESTAMP,returned_by=?,returned_by_name='Thủ thư thử' WHERE id IN (?,?)",staff,value("itemId"),foreignItem);
        }
        if(scenario.equals("OTHER_OWNER")) {
            if(entry.id().equals("BookReservationController.cancelMine"))values.put("reservationId",foreignReservation);
            else if(entry.id().equals("LoanController.checkMyRenewal"))values.put("itemId",foreignItem);
            else values.put("loanId",foreignLoan);
        }
    }
    void fees(PolicyEntry e) throws Exception {
        long fee;
        if(e.controller().equals("FeeReductionController")){
            jdbc.update("UPDATE loan_items SET returned_at=CURRENT_TIMESTAMP,returned_by=?,returned_by_name='Thủ thư thử' WHERE id=?",staff,value("itemId"));
            var due=OffsetDateTime.now().minusDays(3);var now=OffsetDateTime.now();
            var assessment=new ReturnFeeAssessment("READY","Phí thử","S4-01.1-OPEN-DAYS-HALF-UP-V1",due,now,3,5000L,15000L,15000L,null,List.of(),List.of(),List.of());
            fee=context.getBean(OverdueFeeRepository.class).record(value("itemId"),reader,assessment).feeId();
            if(!e.handler().equals("create")){
                long request=id("INSERT INTO fee_reduction_requests(reader_fee_id,requested_by,requester_name,original_amount_vnd,requested_amount_vnd,reason) VALUES (?,?,'Thủ thư thử',15000,5000,'Miễn giảm thử') RETURNING id",fee,staff);values.put("requestId",request);
            }
        } else fee=id("INSERT INTO reader_fees(reader_user_id,description,amount_vnd) VALUES (?,'Khoản phí thử',10000) RETURNING id",reader);
        values.put("feeId",fee);
        if(e.controller().equals("FeePaymentController")) {
            String detail=call(get("/api/v1/fee-payments/readers/"+reader),"LIBRARIAN");var tree=mapper.readTree(detail);
            values.put("debtToken",tree.get("debtToken").asText());values.put("expectedTotalVnd",tree.get("totalOutstandingVnd").asLong());
            if(e.handler().equals("receipt")) {
                String payment=call(post("/api/v1/fee-payments/readers/"+reader+"/collect-full").contentType("application/json").content(json(payment(false))),"LIBRARIAN");
                values.put("paymentId",mapper.readTree(payment).get("id").asLong());
            }
        }
    }
    Map<String,Object> payment(boolean partial){var map=new LinkedHashMap<String,Object>();map.put("requestKey",UUID.randomUUID());map.put("expectedTotalVnd",values.get("expectedTotalVnd"));map.put("debtToken",values.get("debtToken"));if(partial)map.put("amountVnd",5000);return map;}
    void incidents(PolicyEntry e) throws Exception {
        jdbc.update("UPDATE library_compensation_settings SET lost_factor=1,damaged_factor=0.5 WHERE id=1");
        if(Set.of("search","preview","confirm","factors","update","history").contains(e.handler()))return;
        var preview=mapper.readTree(call(get("/api/v1/book-incidents/preview").param("itemId",text("itemId")).param("type","LOST"),"LIBRARIAN"));
        String body=call(multipart("/api/v1/book-incidents").file(image("files")).param("itemId",text("itemId")).param("type","LOST").param("description","Báo mất sách thử").param("previewToken",preview.get("token").asText()),"LIBRARIAN");
        var incident=mapper.readTree(body);values.put("id",incident.get("id").asLong());values.put("imageId",incident.get("photos").get(0).get("id").asLong());
        if(e.handler().equals("confirmAdjustment")) {
            var adjustment=mapper.readTree(call(post("/api/v1/book-incidents/"+text("id")+"/adjustments").contentType("application/json").content(json(Map.of("reason","Tìm lại sách thử","caseType","FOUND","targetStatus","AVAILABLE"))),"LIBRARIAN"));
            values.put("adjustmentId",adjustment.get("id").asLong());
        }
    }
    void passwordToken(PolicyEntry e) throws Exception {
        String token="s4092-token-"+suffix;String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        long account=id("INSERT INTO users(role_id,full_name,email,password_hash,status) SELECT id,'Tài khoản liên kết thử',?,?,'"+(e.controller().equals("InitialPasswordController")?"PENDING":"ACTIVE")+"' FROM roles WHERE code='LIBRARIAN' RETURNING id",suffix+"@example.invalid",context.getBean(PasswordEncoder.class).encode(password));
        jdbc.update("INSERT INTO password_reset_requests(user_id,requested_email,token_hash,request_type,expires_at) SELECT id,email,?,?,CURRENT_TIMESTAMP+interval '1 hour' FROM users WHERE id=?",hash,e.controller().equals("InitialPasswordController")?"INITIAL_PASSWORD":"RESET_PASSWORD",account);values.put("token",token);
    }
    void reminder(PolicyEntry e) {
        values.put("id",id("INSERT INTO loan_reminder_logs(reminder_date,reader_id,reader_name,recipient_email,reminder_group,loan_item_count,loan_numbers,book_titles,subject,email_content,status,error_message) SELECT CURRENT_DATE,id,full_name,email,'DUE_TODAY',1,'S4092','Sách thử','Nhắc trả sách thử','Nội dung thử','FAILED','Lỗi thử để kiểm tra gửi lại' FROM users WHERE id=? RETURNING id",reader));
    }
    void reviewReport(PolicyEntry entry) throws Exception {
        List<EvidenceCase> evidence=new ArrayList<>();OffsetDateTime at=OffsetDateTime.now().minusSeconds(1);
        for(var e:catalog.policy().entries())for(String method:e.httpMethods()) {
            if(!entry.handler().equals("complete") && !e.id().equals("AuthController.me"))continue;
            for(String role:List.of("ADMIN","LIBRARY_MANAGER","LIBRARIAN","READER","ANONYMOUS")) {
                String scenario="STANDARD";
                int expected=catalog.requiredStatus(e,role,scenario,successStatus(e));
                evidence.add(new EvidenceCase(e.id(),role,scenario,expected,null,expected,true,at,at,"Fixture biên bản đầy đủ để kiểm tra API quản trị; không dùng làm bằng chứng của lần chạy thực tế.",null,method,true));
                if("OVERRIDE".equals(e.rules().get(role))) {
                    evidence.add(new EvidenceCase(e.id(),role,"OVERRIDE",200,null,200,null,at,at,"Fixture điều kiện luồng ngoại lệ.",null,method,true));
                }
                if("OWN".equals(e.rules().get(role))) {
                    String scope=Set.of("LoanController.loanDetail","LoanController.checkMyRenewal","BookReservationController.cancelMine").contains(e.id())?"OTHER_OWNER":"SPOOFED_OWNER";int code=scope.equals("OTHER_OWNER")?404:expected;
                    evidence.add(new EvidenceCase(e.id(),role,scope,code,null,code,true,at,at,"Fixture kiểm tra điều kiện biên bản.",null,method,true));
                }
            }
        }
        var report=new ImportReport(2,UUID.randomUUID(),catalog.policy().version(),fingerprint,"REGRESSION","ISOLATED_POSTGRES","Fixture biên bản kiểm thử điều kiện API quản trị","Người kiểm thử",at.minusSeconds(1),OffsetDateTime.now(),evidence);
        String saved=call(post("/api/v1/permission-review/reports").contentType("application/json").content(json(report)),"ADMIN");
        long id=mapper.readTree(saved).get("summary").get("id").asLong();values.put("id",id);values.put("report",report);values.put("runKey",report.runKey());
        jdbc.update("INSERT INTO permission_review_jobs(run_key,state,message,requested_by_user_id,requested_by_name,fingerprint,finished_at,report_id) VALUES (?,'SUCCEEDED','Fixture hoàn tất',?,'Quản trị thử',?,CURRENT_TIMESTAMP,?)",report.runKey(),users.get("ADMIN"),report.fingerprint(),id);
    }
    MockMultipartFile image(String field) throws Exception {
        var image=new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB);var output=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",output);
        return new MockMultipartFile(field,"s4092.png","image/png",output.toByteArray());
    }
    String call(AbstractMockHttpServletRequestBuilder<?> request,String role) throws Exception {
        var result=mvc.perform(request.header("Authorization","Bearer "+tokens.get(role))).andReturn();
        assertTrue(result.getResponse().getStatus()>=200 && result.getResponse().getStatus()<300,"Fixture API failed "+result.getResponse().getStatus()+": "+result.getResponse().getContentAsString());
        return result.getResponse().getContentAsString();
    }
    AbstractMockHttpServletRequestBuilder<?> request(PolicyEntry e,String method,String role,String scenario) throws Exception {
        String path=e.path();for(var item:values.entrySet())path=path.replace("{"+item.getKey()+"}",Objects.toString(item.getValue()));
        if(path.contains("{"))throw new IllegalArgumentException("Chưa có fixture đường dẫn: "+path);
        AbstractMockHttpServletRequestBuilder<?> request;
        if(e.id().equals("BookCoverController.upload"))request=multipart(path).file(image("file"));
        else if(e.id().equals("BookIncidentController.confirm")) {
            var preview=mapper.readTree(call(get("/api/v1/book-incidents/preview").param("itemId",text("itemId")).param("type","LOST"),"LIBRARIAN"));
            request=multipart(path).file(image("files")).param("itemId",text("itemId")).param("type","LOST").param("description","Báo mất sách thử").param("previewToken",preview.get("token").asText());
        } else request=MockMvcRequestBuilders.request(org.springframework.http.HttpMethod.valueOf(method),path);
        if(!role.equals("ANONYMOUS"))request.header("Authorization","Bearer "+tokens.get(role));
        if(scenario.equals("SPOOFED_OWNER"))request.param("readerId",users.get("OTHER_READER").toString()).param("userId",users.get("OTHER_READER").toString());
        boolean override=scenario.equals("OVERRIDE");
        Object body=null;
        switch(e.id()) {
            case "AuthController.login" -> {String who=role.equals("ANONYMOUS")?"READER":role;body=Map.of("email",jdbc.queryForObject("SELECT email FROM users WHERE id=?",String.class,users.get(who)),"password",password);}
            case "AuthController.refresh" -> body=Map.of("refreshToken",refresh.get(role.equals("ANONYMOUS")?"READER":role));
            case "InitialPasswordController.validateToken", "PasswordResetController.validateResetPasswordToken" -> request.param("token",text("token"));
            case "InitialPasswordController.setInitialPassword" -> body=Map.of("token",text("token"),"password","NewReview123!");
            case "PasswordResetController.resetPassword" -> body=Map.of("token",text("token"),"newPassword","NewReview123!","confirmPassword","NewReview123!");
            case "PasswordResetController.forgotPassword" -> body=Map.of("email",suffix+"@example.invalid");
            case "ReaderController.register" -> body=Map.of("fullName","Bạn đọc đăng ký thử","email",suffix+"@example.invalid","dateOfBirth","2006-01-01","phone","0912345678","address","Địa chỉ thử nghiệm","password","ReviewTest123!");
            case "ReaderController.checkDuplicate" -> request.param("email",suffix+"@example.invalid");
            case "ReaderController.updateMyContact" -> body=Map.of("phone","0912345678","address","Địa chỉ cập nhật thử","email",jdbc.queryForObject("SELECT email FROM users WHERE id=?",String.class,reader),"currentPassword",password);
            case "ReaderController.changeMyPassword" -> body=Map.of("currentPassword",password,"newPassword","NewReview123!","confirmPassword","NewReview123!");
            case "AuthorController.createAuthor","AuthorController.updateAuthor" -> body=Map.of("name","Tác giả cập nhật "+suffix,"note","Ghi chú thử");
            case "CategoryController.createCategory","CategoryController.updateCategory" -> body=Map.of("name","Thể loại cập nhật "+suffix,"description","Mô tả thử");
            case "LibraryPublisherController.create" -> body=Map.of("name","Nhà xuất bản thử "+suffix);
            case "BookCatalogController.catalogBook" -> body=Map.of("title","Sách biên mục mới "+suffix,"authorIds",List.of(value("authorId")),"categoryId",value("categoryId"),"publisher","NXB Trẻ","publicationYear",2024,"pageCount",120,"confirmDuplicateTitle",false);
            case "CardTypeController.createCardType","CardTypeController.updateCardType" -> body=Map.of("name","Loại thẻ cập nhật "+suffix,"duration",12,"maxBooks",10,"loanDays",14,"maxRenewals",3,"renewalDays",7);
            case "UserManagementController.createAccount","UserManagementController.updateAccount" -> body=Map.of("fullName","Nhân viên cập nhật","email",suffix+"new@example.invalid","phone","0912345678","role","LIBRARIAN");
            case "UserManagementController.updateAccountStatus" -> body=Map.of("status","LOCKED");
            case "LibraryCardController.approve" -> body=Map.of("cardTypeId",value("cardTypeId"),"expiresAt",LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusMonths(6));
            case "LibraryCardController.reject" -> body=Map.of("reason","Hồ sơ thử chưa đủ điều kiện");
            case "LibraryCardController.lockCard","LibraryCardController.acceptSuggestion" -> body=Map.of("reason","Khóa thẻ theo hồ sơ thử");
            case "LibraryCardController.ignoreSuggestion" -> body=Map.of("reason","Bỏ qua đề xuất thử");
            case "LibraryCardController.unlockCard" -> body=Map.of("reason","Đã xử lý hồ sơ thử","lockStartedAt",text("lockStartedAt"));
            case "BookCopyController.create" -> body=Map.of("barcodeMode","MANUAL","barcode","S4092-NEW-"+suffix,"warehouseId",value("warehouseId"),"shelfId",value("shelfId"),"receivedDate",LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")),"coverPrice",50000,"physicalCondition","GOOD");
            case "BookCopyController.previewBulk","BookCopyController.createBulk" -> {
                request.param("quantity","2").param("warehouseId",text("warehouseId")).param("shelfId",text("shelfId"));
                var bulk=new LinkedHashMap<String,Object>();bulk.put("quantity",2);bulk.put("warehouseId",value("warehouseId"));bulk.put("shelfId",value("shelfId"));bulk.put("receivedDate",LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")));
                if(e.handler().equals("createBulk")) {
                    var preview=mapper.readTree(call(get("/api/v1/books/"+text("bookId")+"/copies/bulk/preview").param("quantity","2").param("warehouseId",text("warehouseId")).param("shelfId",text("shelfId")),"LIBRARIAN"));
                    bulk.put("confirmed",true);bulk.put("expectedStartNumber",preview.get("startNumber").asLong());bulk.put("expectedSkippedBarcodes",List.of());
                }body=bulk;
            }
            case "BookCopyController.update" -> body=Map.of("warehouseId",value("warehouseId"),"shelfId",value("shelfId"),"physicalCondition","GOOD","notes","Ghi chú đã cập nhật");
            case "BookCopyController.repair" -> body=Map.of("reason","Đã sửa chữa xong");
            case "LoanController.searchLoans" -> request.param("code",cardNumber);
            case "FeePaymentController.search" -> request.param("query",cardNumber);
            case "PickupLastDayReminderController.preview" -> request.param("date",LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).toString());
            case "PickupLastDayReminderController.run" -> body=Map.of("date",LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")));
            case "LoanController.readerEligibility" -> request.param("cardNumber",cardNumber);
            case "LoanController.checkReaderAndLog" -> body=Map.of("cardNumber",cardNumber,"requestId",UUID.randomUUID());
            case "LoanController.previewDirectLoanItem" -> body=Map.of("cardNumber",cardNumber,"barcode",freeBarcode,"selectedBarcodes",List.of(),"overridePreview",override);
            case "LoanController.createDirectLoan" -> body=Map.of("cardNumber",cardNumber,"barcodes",List.of(freeBarcode),"requestId",UUID.randomUUID(),"overrideRequested",override,"overrideReason",override?"Ngoại lệ có lý do theo bài kiểm thử":"");
            case "LoanController.createFromReservation" -> body=reservationConfirmation(override);
            case "LoanController.createFromReservations" -> body=Map.of("reservationIds",List.of(value("reservationId")),"confirmation",reservationConfirmation(override));
            case "LoanController.lookupReturn" -> request.param("barcode",borrowedBarcode);
            case "LoanController.confirmReturn" -> body=Map.of("barcode",borrowedBarcode,"itemId",value("itemId"));
            case "BookReservationController.reserveMany" -> body=Map.of("quantity",1);
            case "BookReservationController.cancelByStaff" -> body=Map.of("reason","Hủy theo dữ liệu thử");
            case "FeePaymentController.collect" -> body=payment(false);
            case "FeePaymentController.collectPartial" -> body=payment(true);
            case "FeeReductionController.create" -> body=Map.of("amountVnd",5000,"reason","Đề nghị miễn giảm thử");
            case "FeeReductionController.reject" -> body=Map.of("reason","Chưa đủ điều kiện miễn giảm");
            case "OverdueContactController.record" -> body=Map.of("note","Đã liên hệ nhắc trả trong ca thử");
            case "LibraryConfigurationController.createWarehouse","LibraryConfigurationController.updateWarehouse" -> body=Map.of("code","UPD"+suffix,"name","Kho cập nhật thử "+suffix,"description","Mô tả thử");
            case "LibraryConfigurationController.createShelf","LibraryConfigurationController.updateShelf" -> body=Map.of("warehouseId",value("warehouseId"),"code","UPD"+suffix,"name","Kệ cập nhật thử","description","Mô tả thử");
            case "LibraryConfigurationController.createClosedDate","LibraryConfigurationController.updateClosedDate" -> body=Map.of("closedDate",values.get("closedDate"),"reason","Ngày nghỉ thử cập nhật");
            case "LibraryConfigurationController.createClosedDatesBulk" -> body=Map.of("dates",List.of(Map.of("closedDate",values.get("closedDate"),"reason","Ngày nghỉ thử")));
            case "LibraryConfigurationController.updateWeeklySchedule" -> body=Map.of("days",java.util.stream.IntStream.rangeClosed(1,7).mapToObj(day->Map.of("dayOfWeek",day,"open",true,"openTime","07:00","closeTime","18:00")).toList());
            case "LibraryConfigurationController.adjustDueDate" -> request.param("date",LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(14).toString());
            case "LibraryCardController.updateSuggestionSettings" -> body=Map.of("debtThresholdVnd",100000);
            case "LibraryConfigurationController.updateOverdueFee" -> body=Map.of("dailyRateVnd",5000);
            case "BookIncidentController.update" -> body=Map.of("lost",1.5,"damaged",0.5);
            case "BookIncidentController.preview" -> request.param("itemId",text("itemId")).param("type","LOST");
            case "BookIncidentController.adjustmentPreview" -> request.param("targetStatus","AVAILABLE");
            case "BookIncidentController.requestAdjustment" -> body=Map.of("reason","Tìm lại bản sách thử","caseType","FOUND","targetStatus","AVAILABLE");
            case "LoanReminderController.triggerReminders" -> body=Map.of("targetDate",LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")),"simulateFailure",false);
            case "LoanReminderController.retryPending","LoanReminderController.retrySingle" -> body=Map.of("simulateFailure",false,"logId",value("id"));
            case "PermissionReviewController.importReport" -> {
                var source=(ImportReport)values.get("report");body=new ImportReport(2,UUID.randomUUID(),source.policyVersion(),source.fingerprint(),source.kind(),source.environment(),source.sourceReference(),source.executedBy(),source.startedAt(),source.completedAt(),source.checks());
            }
            default -> { }
        }
        if(e.id().equals("ReaderController.register"))request.with(value->{value.setRemoteAddr("192.0."+((users.get("READER") / 200)%250)+"."+(users.get("READER")%200+1));return value;});
        if(body!=null)request.contentType("application/json").content(json(body));
        return request;
    }
    Map<String,Object> reservationConfirmation(boolean override) {
        return Map.of("cardNumber",cardNumber,"requestId",UUID.randomUUID(),"overrideRequested",override,"overrideReason",override?"Ngoại lệ theo hồ sơ thử":"");
    }
    int successStatus(PolicyEntry e) {
        if(Set.of("BookReservationController.cancelMine","LibraryCardController.reject","BookCoverController.upload",
                "AuthorController.deleteAuthor","CategoryController.deleteCategory","CardTypeController.deleteCardType","UserManagementController.deleteAccount",
                "LibraryConfigurationController.deleteShelf","LibraryConfigurationController.deleteClosedDate").contains(e.id()))return 204;
        if(e.id().equals("BookReservationController.getLatestAutoCancelRun"))return 200;
        if(e.id().equals("PermissionReviewController.startRun"))return 202;
        if(Set.of("LibraryCardController.approve","LoanController.createFromReservation","LoanController.createFromReservations","LoanController.createDirectLoan",
                "BookReservationController.reserve","BookReservationController.reserveMany","FeeReductionController.create","BookCopyController.create","BookCopyController.createBulk",
                "BookIncidentController.confirm","BookIncidentController.requestAdjustment","OverdueContactController.record","ReaderController.register",
                "AuthorController.createAuthor","CategoryController.createCategory","CardTypeController.createCardType","BookCatalogController.catalogBook","LibraryPublisherController.create",
                "LibraryConfigurationController.createWarehouse","LibraryConfigurationController.createShelf","LibraryConfigurationController.createClosedDate","LibraryConfigurationController.createClosedDatesBulk",
                "UserManagementController.createAccount","PermissionReviewController.importReport").contains(e.id()))return 201;
        return 200;
    }
    void verify(PolicyEntry e,String role,String scenario,MvcResult result) throws Exception {
        String body=result.getResponse().getContentAsString();
        assertFalse(body.contains("passwordHash"),"API đã lộ trường mật khẩu.");
        if(e.id().equals("AuthController.me"))assertEquals(users.get(role).longValue(),mapper.readTree(body).get("id").asLong());
        if(e.rules().containsValue("OWN") && role.equals("READER")) {
            assertFalse(body.contains(text("foreignMarker")),"API cá nhân trả về dữ liệu của bạn đọc khác.");
            assertEquals(text("foreignPasswordHash"),jdbc.queryForObject("SELECT password_hash FROM users WHERE id=?",String.class,users.get("OTHER_READER")),"Yêu cầu cá nhân đã đổi mật khẩu người khác.");
            if(Set.of("LoanController.myBorrowedBooks","LoanController.myReturnedBooks").contains(e.id()))assertTrue(body.contains(suffix),"Danh sách cá nhân không có dữ liệu hợp lệ của chính người gọi.");
        }
        if(scenario.equals("SPOOFED_OWNER")) {
            assertNull(jdbc.queryForObject("SELECT phone FROM users WHERE id=?",String.class,users.get("OTHER_READER")),"Tham số giả mạo đã thay đổi tài khoản khác.");
            if(e.id().equals("ReaderController.updateMyContact"))assertEquals("0912345678",jdbc.queryForObject("SELECT phone FROM users WHERE id=?",String.class,reader));
            if(e.id().equals("ReaderController.getMyProfile"))assertFalse(body.contains("S4092 OTHER_READER"));
            if(e.id().equals("LibraryCardController.getMyCard"))assertTrue(body.contains(cardNumber));
            if(e.id().equals("BookReservationController.getMine"))assertTrue(body.contains(text("reservationId")));
        }
        if(e.id().equals("LoanController.confirmReturn"))assertNotNull(jdbc.queryForObject("SELECT returned_at FROM loan_items WHERE id=?",OffsetDateTime.class,value("itemId")));
        if(e.id().equals("FeePaymentController.collect") || e.id().equals("FeePaymentController.collectPartial"))assertTrue(jdbc.queryForObject("SELECT paid_amount_vnd FROM reader_fees WHERE id=?",Long.class,value("feeId"))>0);
        if(e.id().equals("UserManagementController.deleteAccount"))assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM users WHERE id=?",Integer.class,value("id")));
        if(e.id().equals("PermissionReviewController.complete"))assertNotNull(mapper.readTree(body).get("completion"));
        if(e.id().equals("PermissionReviewController.startRun"))assertEquals("STARTING",mapper.readTree(body).get("state").asText());
        if(e.id().equals("OverdueDebtReportController.export"))assertTrue(result.getResponse().getContentAsByteArray().length>100);
    }
    String snapshot() {
        List<String> rows=new ArrayList<>();
        for(String table:tables) {
            if(!table.matches("[a-z0-9_]+"))throw new IllegalStateException("Tên bảng không hợp lệ.");
            rows.add(table+":"+jdbc.queryForObject("SELECT COALESCE(md5(string_agg(rowdata,E'\\n' ORDER BY rowdata)),'') FROM (SELECT to_jsonb(t)::text rowdata FROM public.\""+table+"\" t) data",String.class));
        }
        return String.join("|",rows);
    }
}
